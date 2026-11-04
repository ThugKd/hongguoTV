// SPDX-License-Identifier: GPL-3.0-only
package com.thugkd.lvguotv.core

import okhttp3.Call
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.Closeable
import java.io.IOException

/** One playback stream. No listening socket or local HTTP service.
 *  Reads are served from an in-memory chunk cache keyed by absolute offset. A daemon
 *  read-ahead thread keeps [aheadBytes] of upcoming chunks downloading in the background,
 *  so playback almost never waits on a network round trip. The reader path and the
 *  read-ahead thread may fetch different chunks in parallel; the same chunk is deduplicated. */
class RemoteVideo(
    private val http: OkHttpClient,
    val info: StreamInfo,
    private val chunkBytes: Int = 2*1024*1024,
    private val aheadBytes: Long = 8*1024*1024L,
): Closeable {
    @Volatile private var closed=false
    private val calls=mutableSetOf<Call>()
    data class Range(val bytes: ByteArray,val total: Long)
    @Volatile var total: Long=0; private set
    private val urls=(listOf(info.url)+info.backupUrls).distinct()
    private val routeLock=Any()
    private var routeIndex=0
    private class CancelledRead(cause: IOException? = null): IOException("播放请求已取消",cause)
    private lateinit var prepared: MediaCrypto.Prepared
    private val lock=Object()
    private val cache=HashMap<Long,ByteArray>()
    private var fetchingRead=-1L
    private var fetchingAhead=-1L
    private var lastRead=0L
    private var aheadThread: Thread?=null
    private var hitChunks=0
    private var missChunks=0
    private var readerChunks=0
    private var aheadChunks=0
    private var fetchedBytes=0L
    /** Playback-side counters. Pure JVM module: the app logs this, core never touches android.util.Log. */
    data class Stats(val readerChunks:Int,val aheadChunks:Int,val cacheHits:Int,val cacheMisses:Int,val fetchedBytes:Long) {
        val aheadShare: Int get() { val all=readerChunks+aheadChunks; return if(all==0) 0 else aheadChunks*100/all }
        override fun toString()="$readerChunks reader / $aheadChunks ahead chunks, hit=$cacheHits miss=$cacheMisses, aheadShare=$aheadShare%, fetched=${fetchedBytes/1048576}MB"
    }
    fun stats(): Stats = synchronized(lock) { Stats(readerChunks,aheadChunks,hitChunks,missChunks,fetchedBytes) }

    private fun range(start: Long,end: Long): Range {
        if(closed || Thread.currentThread().isInterrupted) throw CancelledRead()
        require(start>=0 && end>=start && end-start<8*1024*1024)
        val initialRoute=synchronized(routeLock) { routeIndex }
        val firstFailure=try {
            return requestRange(urls[initialRoute],start,end)
        } catch(cancelled: CancelledRead) {
            throw cancelled
        } catch(problem: IOException) {
            problem
        }
        // Normal reader/read-ahead requests remain concurrent. Only route changes are serialized.
        return synchronized(routeLock) {
            var failure=firstFailure
            if(routeIndex!=initialRoute) {
                try {
                    return@synchronized requestRange(urls[routeIndex],start,end)
                } catch(cancelled: CancelledRead) {
                    throw cancelled
                } catch(problem: IOException) {
                    failure=problem
                }
            }
            for(nextRoute in routeIndex+1 until urls.size) {
                try {
                    val firstChunk=synchronized(lock) { cache[0L] }
                    // A backup must contain the same bytes before sharing decrypted MP4 offsets/cache.
                    // Before the first successful read there is no accepted resource to compare against.
                    if(firstChunk!=null) {
                        val identity=requestRange(urls[nextRoute],0L,firstChunk.lastIndex.toLong())
                        if(!identity.bytes.contentEquals(firstChunk)) {
                            throw IOException("视频备用线路内容不一致")
                        }
                    }
                    val result=requestRange(urls[nextRoute],start,end)
                    routeIndex=nextRoute
                    return@synchronized result
                } catch(cancelled: CancelledRead) {
                    throw cancelled
                } catch(problem: IOException) {
                    failure=problem
                }
            }
            throw failure
        }
    }

    private fun requestRange(url: String,start: Long,end: Long): Range {
        if(closed || Thread.currentThread().isInterrupted) throw CancelledRead()
        val address=url.toHttpUrlOrNull() ?: throw IOException("视频地址无效")
        val request=Request.Builder().url(address).header("User-Agent",VendorConstants.VIDEO_UA)
            .header("Referer","https://novel.snssdk.com/").header("Range","bytes=$start-$end")
            .header("Accept-Encoding","identity").build()
        val call=http.newCall(request)
        synchronized(calls) {
            if(closed) throw CancelledRead()
            calls+=call
        }
        try {
            return RequestScope.execute(call) { response ->
                if(response.code!=206) throw IOException("视频服务器不支持分段读取（HTTP ${response.code}）")
                val match=Regex("bytes (\\d+)-(\\d+)/(\\d+)").matchEntire(response.header("Content-Range").orEmpty())
                    ?: throw IOException("视频分段响应异常")
                val (begin,finish,size)=match.destructured
                val full=size.toLongOrNull() ?: throw IOException("视频分段响应异常")
                val last=minOf(end,full-1)
                if(begin.toLongOrNull()!=start || finish.toLongOrNull()!=last || full<=start) {
                    throw IOException("视频分段范围不匹配")
                }
                if(total!=0L && full!=total) throw IOException("视频资源已变化，请重试")
                val length=last-start+1
                val body=response.body ?: throw IOException("视频分段为空")
                if(body.contentLength()>=0 && body.contentLength()!=length) {
                    throw IOException("视频分段长度不匹配")
                }
                val bytes=body.source().readByteArray(length)
                if(closed || call.isCanceled() || Thread.currentThread().isInterrupted) throw CancelledRead()
                Range(bytes,full)
            }
        } catch(problem: IOException) {
            if(closed || call.isCanceled() || Thread.currentThread().isInterrupted) throw CancelledRead(problem)
            throw problem
        } finally {
            synchronized(calls) { calls-=call }
        }
    }
    private fun fetchChunk(start: Long): Range {
        val end=if(total>0) minOf(total,start+chunkBytes)-1 else start+chunkBytes-1L
        return range(start,end)
    }
    private fun inFlightLocked(start: Long)=fetchingRead==start || fetchingAhead==start
    /** Fetch one chunk into the cache from the reader path; waits out an in-flight duplicate. */
    private fun readChunk(start: Long) {
        synchronized(lock) {
            if(closed) throw IOException("播放请求已取消")
            try { while(!closed && inFlightLocked(start) && !cache.containsKey(start)) lock.wait(500) }
            catch(e: InterruptedException) { throw IOException("播放请求已取消") }
            if(closed) throw IOException("播放请求已取消")
            if(cache.containsKey(start)) return
            fetchingRead=start
        }
        try {
            val chunk=fetchChunk(start)
            synchronized(lock) { if(total==0L) total=chunk.total; if(!closed) cache[start]=chunk.bytes; fetchingRead=-1L; readerChunks++; fetchedBytes+=chunk.bytes.size; lock.notifyAll() }
        } catch(e: Throwable) {
            synchronized(lock) { fetchingRead=-1L; lock.notifyAll() }
            throw e
        }
    }
    private fun cached(k: Long)=synchronized(lock) { cache.containsKey(k) }
    /** Chunk bytes from cache. A concurrent eviction (backward seek while read-ahead advances) may drop a
     *  chunk between planning and assembly, so re-fetch instead of trusting the map. */
    private fun chunkOrFetch(k: Long): ByteArray {
        synchronized(lock) { cache[k] }?.let { return it }
        readChunk(k)
        return synchronized(lock) { cache[k] } ?: throw IOException("视频缓存块丢失")
    }
    @Synchronized fun prepare(): RemoteVideo {
        if(::prepared.isInitialized) return this
        readChunk(0L)
        val first=chunkOrFetch(0L)
        val end=MediaCrypto.headerEnd(first,total)
        val header=if(end>first.size) first+range(first.size.toLong(),end-1L).bytes else first.copyOf(end)
        prepared=MediaCrypto.prepare(header,info.key)
        if(prepared.samples.any { it.end>total }) throw IOException("视频样本超出资源大小")
        return this
    }
    /** Bounded warm-up: parses the header, keeps the first chunk resident, starts read-ahead. */
    @Synchronized fun warm(): RemoteVideo {
        prepare()
        synchronized(lock) { lastRead=0; ensureAheadLocked(); lock.notifyAll() }
        return this
    }
    private fun ensureAheadLocked() {
        if(aheadBytes<=0 || aheadThread!=null || closed || total<=chunkBytes) return
        val thread=Thread({ aheadLoop() },"RemoteVideo-ahead")
        thread.isDaemon=true; aheadThread=thread; thread.start()
    }
    private fun nextMissingLocked(): Long {
        if(total<=0) return -1
        val target=minOf(total,lastRead+aheadBytes)
        var at=lastRead-lastRead%chunkBytes
        while(at<target) { if(!cache.containsKey(at) && !inFlightLocked(at)) return at; at+=chunkBytes }
        return -1
    }
    private fun evictLocked() {
        val floor=lastRead-lastRead%chunkBytes
        cache.keys.filter { it!=0L && it<floor }.forEach { cache.remove(it) }
    }
    private fun aheadLoop() {
        while(true) {
            val next: Long
            try {
                next=synchronized(lock) {
                    var candidate=-1L
                    while(!closed && candidate<0L) {
                        candidate=nextMissingLocked()
                        if(candidate<0L) lock.wait()
                    }
                    if(closed) -1L else { fetchingAhead=candidate; candidate }
                }
            } catch(e: InterruptedException) { return }
            if(next<0) return
            try {
                val chunk=fetchChunk(next)
                synchronized(lock) { if(total==0L) total=chunk.total; if(!closed) { cache[next]=chunk.bytes; evictLocked() }; fetchingAhead=-1L; aheadChunks++; fetchedBytes+=chunk.bytes.size; lock.notifyAll() }
            } catch(e: Throwable) {
                synchronized(lock) { fetchingAhead=-1L; lock.notifyAll() }
                if(closed) return
                // 预读失败不影响播放：读取方会自行同步补拉，这里退避后继续
                try { Thread.sleep(1500) } catch(interrupted: InterruptedException) { return }
            }
        }
    }
    fun read(start: Long,length: Int): ByteArray {
        require(length in 1..chunkBytes && start>=0)
        if(closed) throw IOException("播放请求已取消")
        if(start>=total) return byteArrayOf()
        val end=minOf(total,start+length)
        synchronized(lock) {
            if(closed) throw IOException("播放请求已取消")
            lastRead=start; ensureAheadLocked(); lock.notifyAll()
        }
        var at=start
        while(at<end) {
            val k=at-at%chunkBytes
            if(cached(k)) synchronized(lock) { hitChunks++ } else { synchronized(lock) { missChunks++ }; readChunk(k) }
            at=k+chunkBytes
        }
        val raw=ByteArray((end-start).toInt())
        var pos=start; var written=0
        while(pos<end) {
            val k=pos-pos%chunkBytes
            val chunk=chunkOrFetch(k)
            val from=(pos-k).toInt()
            val count=minOf(chunk.size-from,(end-pos).toInt())
            if(count<=0) throw IOException("视频分段数据不完整")
            System.arraycopy(chunk,from,raw,written,count); written+=count; pos+=count
        }
        return MediaCrypto.decrypt(raw,start,info.key,prepared)
    }
    override fun close() {
        synchronized(lock) { closed=true; lock.notifyAll() }
        aheadThread?.interrupt()
        synchronized(calls) { closed=true; calls.toList().forEach { it.cancel() }; calls.clear() }
    }
}
