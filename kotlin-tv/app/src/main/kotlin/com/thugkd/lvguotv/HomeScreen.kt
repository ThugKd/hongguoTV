// SPDX-License-Identifier: GPL-3.0-only
package com.thugkd.lvguotv

import android.content.Context
import android.graphics.Color
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.*
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.thugkd.lvguotv.core.Series
import com.thugkd.lvguotv.core.FocusGrid

class SeriesPreview(context: Context): LinearLayout(context) {
    private val title=TextView(context).apply { textSize=20f; setTextColor(TvStyle.text); maxLines=1; ellipsize=TextUtils.TruncateAt.END; includeFontPadding=false; typeface=Typeface.create("sans-serif-medium",Typeface.NORMAL) }
    private val meta=TextView(context).apply { textSize=12f; setTextColor(TvStyle.muted); maxLines=1; ellipsize=TextUtils.TruncateAt.END; includeFontPadding=false }
    init {
        orientation=VERTICAL
        val dp=resources.displayMetrics.density
        setPadding(0,(4*dp).toInt(),0,(10*dp).toInt()); addView(title)
        addView(meta,LayoutParams(-1,-2).apply { topMargin=(5*dp).toInt() }); show(null,"")
    }
    fun show(series: Series?,status: String) {
        title.text=series?.title ?: "选择一部剧"
        meta.text=if(series==null) "浏览片库，选择你想看的剧" else listOf(status,series.badge,series.tags).flatMap { it.split('·') }.map(String::trim).filter { it.isNotBlank() && !it.all(Char::isDigit) }.distinct().joinToString("  /  ")
    }
}

/** One vertical TV feed: feature, real recommendations, local collections and paging share focus/scroll state. */
class HomeScreen(
    context: Context,
    private val top: View,
    private val loadCover: (ImageView,String)->Unit,
    private val selected: (Series,String)->Unit,
    private val open: (Series,Boolean)->Unit,
    private val actions: (Series,View)->Unit,
    private val remember: (String)->Unit,
    private val favorite: (Series)->Boolean,
    private val toggleFavorite: (Series)->Boolean,
    private val filterTarget: View,
    private val toolbarTargets: List<View>
): LinearLayout(context) {
    data class Entry(val series: Series,val label: String,val resume: Boolean=false,val progress: Int?=null)
    data class Shelf(val key: String,val title: String,val entries: List<Entry>,val more: (() -> Unit)?=null)
    data class Position(val focus: String,val shelf: String?,val column: Int,val row: Int,val vertical: Int,val horizontal: Map<String,Int>,val anchor: String?=null)
    private data class Feature(val entry: Entry,val rail: List<Entry>,val history: Boolean)
    private data class Block(val kind: Int,val key: String,val entry: Entry?=null,val shelf: Shelf?=null,val feature: Feature?=null,val count: Int=0)
    private companion object { const val FEATURE=0; const val HEADING=1; const val POSTER=2; const val FOOTER=3; const val EMPTY=4; const val COLUMNS=6 }
    private fun dp(value: Int)=(value*resources.displayMetrics.density).toInt()
    private fun label(value: String,size: Float,color: Int=TvStyle.text)=TextView(context).apply { text=value; textSize=size; setTextColor(color); includeFontPadding=false }
    private fun line()=LinearLayout(context).apply { orientation=HORIZONTAL; gravity=Gravity.CENTER_VERTICAL }
    private fun column()=LinearLayout(context).apply { orientation=VERTICAL }
    private val ids=mutableMapOf<String,Int>()
    private fun id(key: String)=ids.getOrPut(key) { View.generateViewId() }
    private var shelves=emptyList<Shelf>()
    private var blocks=emptyList<Block>()
    private var footerMore: (() -> Unit)?=null
    private var paging=false
    private var pagingFailed=false
    private var pagingPaused=false
    private var pagingLimit=false
    private var lastFocus=""
    private var restoring=false
    private var revision=0
    private var pendingFocus: ViewTreeObserver.OnPreDrawListener?=null
    private var pendingFocusKey: String?=null
    private var posterRows=emptyList<List<Int>>()
    private var posterNavigation=FocusGrid(emptyList())
    private val grid=GridLayoutManager(context,COLUMNS)
    private val adapter=FeedAdapter()
    private val recycler=RecyclerView(context).apply {
        id=View.generateViewId(); layoutManager=grid; adapter=this@HomeScreen.adapter
        itemAnimator=null; isVerticalScrollBarEnabled=false; clipToPadding=false
        setPadding(dp(2),dp(2),dp(2),dp(8)); descendantFocusability=FOCUS_AFTER_DESCENDANTS
    }
    val refresh=action("刷新推荐","home:refresh") {}
    private val more=action("继续浏览  ↓","home:more") { if(!paging) footerMore?.invoke() }
    init {
        orientation=VERTICAL
        grid.spanSizeLookup=object: GridLayoutManager.SpanSizeLookup() { override fun getSpanSize(position: Int)=if(blocks.getOrNull(position)?.kind==POSTER) 1 else COLUMNS }
        addView(recycler,LayoutParams(-1,0,1f))
        recycler.addOnScrollListener(object: RecyclerView.OnScrollListener() {
            override fun onScrolled(view: RecyclerView,dx: Int,dy: Int) {
                if(dy>0 && !restoring && !paging && !pagingFailed && !pagingPaused && grid.findLastVisibleItemPosition()>=blocks.size-8) footerMore?.invoke()
            }
        })
    }
    private fun action(value: String,key: String,primary: Boolean=false,onClick: ()->Unit): TextView = label(value,13f).apply {
        id=id(key); tag=key; isFocusable=true; isFocusableInTouchMode=true; gravity=Gravity.CENTER
        minHeight=dp(36); setPadding(dp(13),dp(7),dp(13),dp(7))
        fun paint(focused: Boolean) {
            background=if(primary) TvStyle.primary(context,focused) else TvStyle.shape(context,if(focused) TvStyle.raised else Color.TRANSPARENT,if(focused) TvStyle.accent else Color.TRANSPARENT,radius=8)
            setTextColor(if(primary) TvStyle.onAccent else if(focused) TvStyle.accent else TvStyle.muted)
        }
        paint(false); setOnFocusChangeListener { _,focused -> paint(focused); if(focused) { lastFocus=key; remember(key) } }
        setOnClickListener { lastFocus=key; remember(key); onClick() }
    }
    private fun models(next: List<Shelf>): List<Block> = buildList {
        val hot=next.firstOrNull { it.key=="hot" }
        val recent=next.firstOrNull { it.key=="resume" }?.entries.orEmpty().take(2)
        hot?.entries?.firstOrNull()?.let { featured ->
            add(Block(FEATURE,"feature",feature=Feature(featured,if(recent.isNotEmpty()) recent else hot.entries.drop(1).take(2),recent.isNotEmpty())))
        }
        if(hot?.entries.isNullOrEmpty()) add(Block(EMPTY,"empty"))
        hot?.entries?.forEach { add(Block(POSTER,"hot:${it.series.id}",entry=it,shelf=hot)) }
        next.filter { it.key!="hot" && it.key!="resume" }.forEach { shelf ->
            if(shelf.entries.isNotEmpty()) {
                add(Block(HEADING,"heading:${shelf.key}",shelf=shelf))
                shelf.entries.forEach { add(Block(POSTER,"${shelf.key}:${it.series.id}",entry=it,shelf=shelf)) }
            }
        }
        add(Block(FOOTER,"footer",count=hot?.entries?.size ?: 0))
    }
    fun render(shelves: List<Shelf>,focus: String,requestFocus: Boolean,more: (() -> Unit)?,position: Position?=null) {
        val previous=position ?: if(blocks.isNotEmpty()) capturePosition() else null
        this.shelves=shelves; footerMore=more; paging=false; pagingFailed=false; revision++
        val next=models(shelves); val old=blocks
        val diff=DiffUtil.calculateDiff(object: DiffUtil.Callback() {
            override fun getOldListSize()=old.size
            override fun getNewListSize()=next.size
            override fun areItemsTheSame(a: Int,b: Int)=old[a].key==next[b].key
            override fun areContentsTheSame(a: Int,b: Int): Boolean {
                val before=old[a]; val after=next[b]
                return if(before.kind==POSTER) before.entry==after.entry && before.shelf?.key==after.shelf?.key else before==after
            }
        })
        cancelPendingFocus()
        blocks=next
        val rows=mutableListOf<List<Int>>()
        var row=mutableListOf<Int>()
        next.forEachIndexed { index,block ->
            if(block.kind==POSTER) {
                row+=index
                if(row.size==COLUMNS) { rows+=row; row=mutableListOf() }
            } else if(row.isNotEmpty()) { rows+=row; row=mutableListOf() }
        }
        if(row.isNotEmpty()) rows+=row
        posterRows=rows; posterNavigation=FocusGrid(rows.map { it.size })
        diff.dispatchUpdatesTo(adapter)
        val remembered=focus.ifBlank { previous?.focus.orEmpty() }.ifBlank { defaultKey() }
        val oldHot=old.filter { it.kind==POSTER && it.shelf?.key=="hot" }.mapTo(HashSet()) { it.key }
        val firstAdded=next.firstOrNull { it.kind==POSTER && it.shelf?.key=="hot" && it.key !in oldHot }
        val key=if(remembered=="home:more" && oldHot.isNotEmpty() && firstAdded!=null) firstAdded.key else remembered
        lastFocus=key
        val ticket=revision; restoring=true
        if(previous!=null) restoreScroll(previous)
        post {
            if(ticket==revision && isAttachedToWindow) {
                if(requestFocus) focusKey(key.ifBlank { defaultKey() })
                restoring=false
            }
        }
    }
    fun updateShelves(next: List<Shelf>,requestFocus: Boolean=hasFocus(),more: (() -> Unit)?=footerMore) {
        val position=capturePosition(); render(next,position.focus,requestFocus,more,position)
    }
    fun capturePosition(): Position {
        val focus=pendingFocusKey ?: findFocus()?.tag?.toString() ?: lastFocus
        val first=grid.findFirstVisibleItemPosition().coerceAtLeast(0)
        val index=blocks.indexOfFirst { it.key==focus }
        return Position(focus,blocks.getOrNull(index)?.shelf?.key,0,index.coerceAtLeast(0),first,mapOf("offset" to ((grid.findViewByPosition(first)?.top ?: recycler.paddingTop)-recycler.paddingTop)),blocks.getOrNull(first)?.key)
    }
    private fun restoreScroll(position: Position) {
        val anchor=position.anchor?.let { key -> blocks.indexOfFirst { it.key==key } }?.takeIf { it>=0 }
        grid.scrollToPositionWithOffset(anchor ?: position.vertical.coerceIn(0,(blocks.size-1).coerceAtLeast(0)),position.horizontal["offset"] ?: 0)
    }
    fun restorePosition(position: Position,requestFocus: Boolean=true) {
        restoreScroll(position)
        if(requestFocus) post { focusKey(position.focus) }
    }
    private fun defaultKey()=blocks.firstOrNull { it.kind==FEATURE }?.feature?.entry?.series?.id?.let { "home:featured:$it" }
        ?: blocks.firstOrNull { it.kind==POSTER }?.key ?: "home:refresh"
    private fun cancelPendingFocus() {
        pendingFocus?.let { if(recycler.viewTreeObserver.isAlive) recycler.viewTreeObserver.removeOnPreDrawListener(it) }
        pendingFocus=null; pendingFocusKey=null
    }
    override fun onDetachedFromWindow() {
        cancelPendingFocus()
        super.onDetachedFromWindow()
    }
    private fun focusKey(key: String) {
        cancelPendingFocus()
        recycler.findViewWithTag<View>(key)?.takeIf { it.isShown && it.isEnabled }?.let { it.requestFocus(); return }
        val index=when {
            key.startsWith("home:featured:") || key.startsWith("home:favorite:") || key.startsWith("home:rail:") -> blocks.indexOfFirst { it.kind==FEATURE }
            key.startsWith("more:") -> blocks.indexOfFirst { it.kind==HEADING && "more:${it.shelf?.key}"==key }
            key=="home:more" || key=="home:refresh" -> blocks.lastIndex
            else -> blocks.indexOfFirst { it.key==key }
        }
        if(index<0) {
            if(key!=defaultKey()) focusKey(defaultKey()) else filterTarget.requestFocus()
            return
        }
        grid.scrollToPositionWithOffset(index,0)
        pendingFocusKey=key
        var attempts=0
        val ready=object: ViewTreeObserver.OnPreDrawListener {
            override fun onPreDraw(): Boolean {
                val target=recycler.findViewWithTag<View>(key)
                if(target!=null && target.isShown && target.isEnabled) {
                    cancelPendingFocus(); target.requestFocus()
                } else if(++attempts>=4) {
                    cancelPendingFocus()
                    val fallback=recycler.findViewWithTag<View>(defaultKey())
                    (fallback ?: filterTarget).requestFocus()
                } else recycler.postInvalidateOnAnimation()
                return true
            }
        }
        pendingFocus=ready
        recycler.viewTreeObserver.addOnPreDrawListener(ready)
        recycler.requestLayout()
    }
    private fun featureControls(): List<View> {
        val host=recycler.findViewWithTag<ViewGroup>("home:feature") ?: return emptyList()
        val controls=ArrayList<View>()
        host.addFocusables(controls,View.FOCUS_DOWN,View.FOCUSABLES_ALL)
        return controls.filter { it.tag?.toString()?.startsWith("home:")==true && it.isShown && it.isEnabled }
    }
    fun focusBelowToolbar(origin: View) {
        val viewport=Rect()
        recycler.getGlobalVisibleRect(viewport)
        fun visible(view: View): Boolean {
            val bounds=Rect()
            return view.getGlobalVisibleRect(bounds) && Rect.intersects(viewport,bounds)
        }
        val feature=featureControls().filter(::visible)
        if(feature.isNotEmpty()) { TvFocus.move(origin,feature); return }
        val firstRow=TvFocus.firstVisibleRow(recycler) { it is SeriesCard }
        if(!TvFocus.move(origin,firstRow)) focusRememberedCard()
    }
    private fun focusToolbar(origin: View) { if(!TvFocus.move(origin,toolbarTargets)) filterTarget.requestFocus() }
    private fun focusFirstPoster(column: Int) {
        val target=posterRows.firstOrNull()?.let { it[column.coerceAtMost(it.lastIndex)] }
        if(target==null) filterTarget.requestFocus() else focusKey(blocks[target].key)
    }
    private fun focusFeatureAbove(column: Int) {
        val feature=blocks.firstOrNull { it.kind==FEATURE }?.feature
        if(feature==null) { filterTarget.requestFocus(); return }
        val key=when {
            column>=4 && feature.rail.isNotEmpty() -> "home:rail:${feature.rail.last().series.id}"
            column==0 -> "home:featured:${feature.entry.series.id}"
            else -> "home:favorite:${feature.entry.series.id}"
        }
        focusKey(key)
    }
    private fun movePoster(key: String,code: Int): Boolean {
        val index=blocks.indexOfFirst { it.key==key }
        val row=posterRows.indexOfFirst { index in it }
        if(row<0) return false
        val col=posterRows[row].indexOf(index)
        val current=recycler.findViewWithTag<View>(key)
        val target=when(code) {
            KeyEvent.KEYCODE_DPAD_LEFT -> posterNavigation.left(row,col)
            KeyEvent.KEYCODE_DPAD_RIGHT -> posterNavigation.right(row,col)
            KeyEvent.KEYCODE_DPAD_UP -> {
                val heading=blocks.getOrNull(posterRows[row].first()-1)
                if(col==COLUMNS-1 && heading?.kind==HEADING && heading.shelf?.more!=null) {
                    focusKey("more:${heading.shelf.key}"); return true
                }
                if(row==0) { focusFeatureAbove(col); return true }
                posterNavigation.up(row,col)
            }
            KeyEvent.KEYCODE_DPAD_DOWN -> {
                val next=posterRows.getOrNull(row+1)
                val heading=next?.let { blocks.getOrNull(it.first()-1) }
                if(col==COLUMNS-1 && heading?.kind==HEADING && heading.shelf?.more!=null) {
                    focusKey("more:${heading.shelf.key}"); return true
                }
                if(next==null) {
                    if(current!=null) TvFocus.move(current,listOf(refresh,more))
                    return true
                }
                posterNavigation.down(row,col)
            }
            else -> return false
        }
        target?.let { focusKey(blocks[posterRows[it.row][it.col]].key) }
        return true
    }
    private fun reveal(view: View) {
        recycler.post {
            if(!view.hasFocus() || view.parent==null) return@post
            val bounds=Rect(0,0,view.width,view.height)
            recycler.offsetDescendantRectToMyCoords(view,bounds)
            val top=recycler.paddingTop; val bottom=recycler.height-recycler.paddingBottom
            val distance=when {
                bounds.bottom>bottom -> bounds.bottom-bottom
                bounds.top<top -> bounds.top-top
                else -> 0
            }
            if(distance!=0) recycler.smoothScrollBy(0,distance)
        }
    }
    fun setPaging(loading: Boolean,failed: Boolean=false) {
        paging=loading; pagingFailed=failed
        paintPaging()
    }
    fun setPagingBoundary(paused: Boolean,limit: Boolean=false) {
        pagingPaused=paused; pagingLimit=limit; paintPaging()
    }
    private fun paintPaging() {
        more.text=when { paging -> "正在加载…"; pagingFailed -> "重试加载"; pagingLimit -> "达到本次浏览上限"; pagingPaused && footerMore!=null -> "继续下一段  ↓"; footerMore!=null -> "继续浏览  ↓"; else -> "已展示全部推荐" }
        // Keep the focused footer attached and focusable while the next page loads.
        more.isEnabled=true
        val focusable=footerMore!=null && !pagingLimit || more.hasFocus()
        more.isFocusable=focusable; more.isFocusableInTouchMode=focusable
        more.alpha=if(paging) .6f else 1f
    }
    fun refreshSelection() { blocks.firstOrNull { it.key==lastFocus }?.entry?.let { selected(it.series,it.label) } }
    fun reloadArtwork() { adapter.notifyItemRangeChanged(0,blocks.size,"artwork") }
    fun firstId()=id(defaultKey())
    fun firstPosterId()=blocks.firstOrNull { it.kind==POSTER }?.let { id(it.key) } ?: filterTarget.id
    fun focusContent()=focusKey(defaultKey())
    fun focusRememberedCard()=focusKey(lastFocus.ifBlank { defaultKey() })

    private inner class FeedAdapter: RecyclerView.Adapter<RecyclerView.ViewHolder>() {
        init { setHasStableIds(true) }
        override fun getItemCount()=blocks.size
        override fun getItemId(position: Int)=id(blocks[position].key).toLong()
        override fun getItemViewType(position: Int)=blocks[position].kind
        override fun onCreateViewHolder(parent: ViewGroup,kind: Int): RecyclerView.ViewHolder = when(kind) {
            FEATURE -> object: RecyclerView.ViewHolder(FrameLayout(context).apply { layoutParams=RecyclerView.LayoutParams(-1,-2).apply { bottomMargin=dp(12) } }) {}
            HEADING -> object: RecyclerView.ViewHolder(line().apply { setPadding(0,dp(4),0,dp(8)); layoutParams=RecyclerView.LayoutParams(-1,-2) }) {}
            POSTER -> PosterHolder()
            FOOTER -> FooterHolder()
            else -> object: RecyclerView.ViewHolder(label("推荐内容加载后会显示在这里",14f,TvStyle.muted).apply { gravity=Gravity.CENTER; setPadding(0,dp(35),0,dp(35)); layoutParams=RecyclerView.LayoutParams(-1,-2) }) {}
        }
        override fun onBindViewHolder(holder: RecyclerView.ViewHolder,position: Int) {
            val block=blocks[position]
            when(block.kind) {
                FEATURE -> bindFeature(holder.itemView as FrameLayout,requireNotNull(block.feature))
                HEADING -> bindHeading(holder.itemView as LinearLayout,requireNotNull(block.shelf))
                POSTER -> (holder as PosterHolder).bind(block,position)
                FOOTER -> (holder as FooterHolder).bind(block.count)
            }
        }
        override fun onBindViewHolder(holder: RecyclerView.ViewHolder,position: Int,payloads: MutableList<Any>) {
            if(payloads.isNotEmpty() && payloads.all { it=="artwork" } && holder is PosterHolder) {
                blocks[position].entry?.let { loadCover(holder.image,it.series.cover) }
            } else super.onBindViewHolder(holder,position,payloads)
        }
    }
    private fun bindHeading(host: LinearLayout,shelf: Shelf) {
        host.removeAllViews()
        host.addView(label(if(shelf.key=="hot") "为你推荐" else shelf.title,17f).apply { typeface=Typeface.create("sans-serif-medium",Typeface.NORMAL) },LayoutParams(0,-2,1f))
        shelf.more?.let { callback ->
            val button=action("全部  ›","more:${shelf.key}") { callback() }
            button.nextFocusLeftId=button.id; button.nextFocusRightId=button.id
            button.setOnKeyListener { _,code,event ->
                if(event.action!=KeyEvent.ACTION_DOWN) false
                else {
                    val heading=blocks.indexOfFirst { it.kind==HEADING && it.shelf?.key==shelf.key }
                    val row=when(code) {
                        KeyEvent.KEYCODE_DPAD_UP -> posterRows.lastOrNull { it.last()<heading }
                        KeyEvent.KEYCODE_DPAD_DOWN -> posterRows.firstOrNull { it.first()>heading }
                        else -> null
                    }
                    if(row!=null) { focusKey(blocks[row.last()].key); true }
                    else if(code==KeyEvent.KEYCODE_DPAD_UP) { focusToolbar(button); true } else false
                }
            }
            host.addView(button,LayoutParams(-2,-2))
        }

    }
    private inner class PosterHolder: RecyclerView.ViewHolder(SeriesCard(context)) {
        private val card=itemView as SeriesCard
        val image=card.image
        init {
            card.layoutParams=RecyclerView.LayoutParams(-1,-2).apply { rightMargin=dp(12); bottomMargin=dp(14) }
        }
        fun bind(block: Block,position: Int) {
            val entry=requireNotNull(block.entry); val key=block.key
            card.id=id(key); card.tag=key; card.bindText(entry.series.title,entry.label)
            card.showProgress(entry.progress)
            card.contentDescription="${entry.series.title}，${entry.label}"
            card.setOnFocusChangeListener { _,focused ->
                if(focused) { lastFocus=key; remember(key); selected(entry.series,entry.label); loadCover(image,entry.series.cover); reveal(card) }
            }
            card.setOnClickListener { remember(key); open(entry.series,entry.resume) }
            card.setOnLongClickListener { remember(key); actions(entry.series,card); true }
            card.setOnKeyListener { _,code,event ->
                if(code==KeyEvent.KEYCODE_MENU) { if(event.action==KeyEvent.ACTION_UP) actions(entry.series,card); true }
                else if(event.action==KeyEvent.ACTION_DOWN) movePoster(key,code) else false
            }
            loadCover(image,entry.series.cover)
        }
    }
    private inner class FooterHolder: RecyclerView.ViewHolder(line()) {
        private val count=label("",12f,TvStyle.muted)
        init {
            val host=itemView as LinearLayout; host.setPadding(0,dp(8),dp(12),dp(12)); host.layoutParams=RecyclerView.LayoutParams(-1,-2)
            (refresh.parent as? ViewGroup)?.removeView(refresh); (more.parent as? ViewGroup)?.removeView(more)
            host.addView(count,LayoutParams(0,-2,1f)); host.addView(refresh,LayoutParams(dp(205),dp(38))); host.addView(more,LayoutParams(-2,dp(38)))
        }
        fun bind(total: Int) {
            count.text="已展示 $total 部推荐"; paintPaging()
            val last=blocks.lastOrNull { it.kind==POSTER }?.let { id(it.key) } ?: filterTarget.id
            refresh.nextFocusUpId=last; more.nextFocusUpId=last
            refresh.nextFocusLeftId=refresh.id; refresh.nextFocusRightId=more.id
            more.nextFocusLeftId=refresh.id; more.nextFocusRightId=more.id
            listOf(refresh,more).forEach { button ->
                button.nextFocusDownId=button.id
                button.setOnKeyListener { _,code,event ->
                    if(event.action==KeyEvent.ACTION_DOWN && code==KeyEvent.KEYCODE_DPAD_UP) {
                        val row=posterRows.lastOrNull().orEmpty().mapNotNull { recycler.findViewWithTag<View>(blocks[it].key) }
                        if(!TvFocus.move(button,row)) blocks.lastOrNull { it.kind==POSTER }?.let { focusKey(it.key) }
                        true
                    } else false
                }
            }
        }
    }
    private fun bindFeature(host: FrameLayout,feature: Feature) {
        host.removeAllViews(); host.tag="home:feature"
        val heightDp=resources.displayMetrics.heightPixels/resources.displayMetrics.density
        val compact=heightDp<450
        val height=dp(if(compact) 176 else 168)
        val width=(resources.displayMetrics.widthPixels*.92f).toInt()
        val root=line().apply { gravity=Gravity.TOP }
        val hero=FrameLayout(context).apply { background=TvStyle.shape(context,TvStyle.surface,radius=12); clipToOutline=true; elevation=dp(3).toFloat() }
        val cover=ImageView(context).apply { scaleType=ImageView.ScaleType.CENTER_CROP; importantForAccessibility=IMPORTANT_FOR_ACCESSIBILITY_NO }
        hero.addView(cover,FrameLayout.LayoutParams((width*.46f).toInt(),-1,Gravity.END))
        val shade=TvStyle.surface
        hero.addView(View(context).apply { background=GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,intArrayOf(shade,Color.argb(235,Color.red(shade),Color.green(shade),Color.blue(shade)),Color.TRANSPARENT)) },FrameLayout.LayoutParams(-1,-1))
        val copy=column().apply { gravity=Gravity.CENTER_VERTICAL; setPadding(dp(18),dp(12),dp(12),dp(12)) }
        copy.addView(label(feature.entry.series.title,if(compact) 22f else 24f).apply { typeface=Typeface.create("sans-serif-medium",Typeface.NORMAL); maxLines=2; ellipsize=TextUtils.TruncateAt.END })
        copy.addView(label(listOf(feature.entry.series.tags,feature.entry.series.badge).filter { it.isNotBlank() }.joinToString(" · "),12f,TvStyle.muted).apply { maxLines=1; ellipsize=TextUtils.TruncateAt.END },LayoutParams(-1,-2).apply { topMargin=dp(7); bottomMargin=dp(12) })
        val controls=line()
        val play=action("▶  立即观看","home:featured:${feature.entry.series.id}",true) { open(feature.entry.series,true) }.apply { contentDescription="立即观看，${feature.entry.series.title}"; nextFocusUpId=filterTarget.id; nextFocusDownId=firstPosterId() }
        lateinit var save: TextView
        save=action(if(favorite(feature.entry.series)) "✓ 已收藏" else "＋ 收藏","home:favorite:${feature.entry.series.id}") { save.text=if(toggleFavorite(feature.entry.series)) "✓ 已收藏" else "＋ 收藏" }.apply { nextFocusUpId=filterTarget.id; nextFocusDownId=firstPosterId() }
        controls.addView(play,LayoutParams(-2,-2).apply { rightMargin=dp(10) }); controls.addView(save,LayoutParams(-2,-2)); copy.addView(controls)
        hero.addView(copy,FrameLayout.LayoutParams((width*.44f).toInt(),-1))
        root.addView(hero,LayoutParams(0,height,2.3f).apply { rightMargin=dp(14) })
        val rail=column().apply { setPadding(dp(10),dp(10),dp(10),dp(10)); background=TvStyle.shape(context,TvStyle.surface,radius=12); elevation=dp(3).toFloat() }
        rail.addView(label(if(feature.history) "继续观看" else "更多推荐",15f).apply { typeface=Typeface.create("sans-serif-medium",Typeface.NORMAL) })
        val railViews=mutableListOf<View>()
        feature.rail.forEach { entry ->
            val key="home:rail:${entry.series.id}"
            val row=line().apply { id=id(key); tag=key; isFocusable=true; isFocusableInTouchMode=true; setPadding(dp(2),dp(2),dp(2),dp(2)); contentDescription="${entry.series.title}，${entry.label}" }
            val image=ImageView(context).apply { scaleType=ImageView.ScaleType.CENTER_CROP; background=TvStyle.shape(context,TvStyle.raised,radius=6); clipToOutline=true }
            row.addView(image,LayoutParams(dp(72),-1).apply { rightMargin=dp(8) })
            val text=column().apply { gravity=Gravity.CENTER_VERTICAL }
            text.addView(label(entry.series.title,12f).apply { maxLines=2; ellipsize=TextUtils.TruncateAt.END })
            text.addView(label(entry.label,11f,TvStyle.muted).apply { maxLines=1; ellipsize=TextUtils.TruncateAt.END },LayoutParams(-1,-2).apply { topMargin=dp(3) })
            if(entry.progress!=null) text.addView(ProgressBar(context,null,android.R.attr.progressBarStyleHorizontal).apply { max=100; progress=entry.progress; progressTintList=android.content.res.ColorStateList.valueOf(TvStyle.accent); progressBackgroundTintList=android.content.res.ColorStateList.valueOf(TvStyle.outline) },LayoutParams(-1,dp(2)).apply { topMargin=dp(4) })
            row.addView(text,LayoutParams(0,-1,1f))
            row.setOnClickListener { remember(key); open(entry.series,entry.resume) }; row.setOnLongClickListener { actions(entry.series,row); true }
            row.setOnFocusChangeListener { _,focused -> row.background=TvStyle.shape(context,if(focused) TvStyle.raised else Color.TRANSPARENT,if(focused) TvStyle.accent else Color.TRANSPARENT,radius=8); if(focused) { lastFocus=key; remember(key) } }
            rail.addView(row,LayoutParams(-1,0,1f).apply { topMargin=dp(6) }); railViews+=row; loadCover(image,entry.series.cover)
        }
        root.addView(rail,LayoutParams(0,height,1f)); host.addView(root,FrameLayout.LayoutParams(-1,-2))
        play.nextFocusLeftId=play.id; play.nextFocusRightId=save.id; save.nextFocusLeftId=play.id; save.nextFocusRightId=railViews.firstOrNull()?.id ?: save.id
        listOf(play,save).forEachIndexed { column,view ->
            view.setOnKeyListener { _,code,event ->
                if(event.action!=KeyEvent.ACTION_DOWN) false
                else when(code) {
                    KeyEvent.KEYCODE_DPAD_UP -> { focusToolbar(view); true }
                    KeyEvent.KEYCODE_DPAD_DOWN -> { focusFirstPoster(column); true }
                    else -> false
                }
            }
        }
        railViews.forEachIndexed { index,view ->
            view.nextFocusLeftId=save.id; view.nextFocusRightId=view.id
            view.setOnKeyListener { _,code,event ->
                if(event.action!=KeyEvent.ACTION_DOWN) false
                else when(code) {
                    KeyEvent.KEYCODE_DPAD_UP -> {
                        if(index>0) railViews[index-1].requestFocus() else focusToolbar(view)
                        true
                    }
                    KeyEvent.KEYCODE_DPAD_DOWN -> {
                        if(index<railViews.lastIndex) railViews[index+1].requestFocus() else focusFirstPoster(COLUMNS-1)
                        true
                    }
                    else -> false
                }
            }
        }
        loadCover(cover,feature.entry.series.cover)
    }
}
