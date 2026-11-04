// SPDX-License-Identifier: GPL-3.0-only
package com.thugkd.lvguotv

import android.content.Context
import android.opengl.GLES20
import android.util.Log
import android.widget.Toast
import androidx.media3.common.PlaybackException
import androidx.media3.common.VideoFrameProcessingException
import androidx.media3.common.util.GlProgram
import androidx.media3.common.util.GlUtil
import androidx.media3.common.util.Size
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.BaseGlShaderProgram
import androidx.media3.effect.GlEffect
import androidx.media3.effect.GlShaderProgram
import androidx.media3.exoplayer.ExoPlayer
import java.io.IOException

/** All players share the preference and bypass GPU effects after an incompatibility in this process. */
@androidx.annotation.OptIn(UnstableApi::class)
class VideoEnhancement(context: Context) {
    private val app=context.applicationContext
    private val preferences=PlaybackExtras(app)
    private var applied=false

    var enabled: Boolean
        get() = preferences.pictureEnhancement
        set(value) { preferences.pictureEnhancement=value; unavailable=false }

    fun applyTo(player: ExoPlayer) {
        applied=false
        if(!enabled || unavailable) return
        try {
            player.setVideoEffects(listOf(MildSharpenEffect()))
            applied=true
        } catch(problem: RuntimeException) {
            disableForCompatibility(problem)
        }
    }

    /** Only effect failures trigger this fallback; network/codec errors keep their existing recovery. */
    fun fallbackAfter(error: PlaybackException): Boolean {
        if(!applied) return false
        val effectFailure=error.errorCode == PlaybackException.ERROR_CODE_VIDEO_FRAME_PROCESSOR_INIT_FAILED ||
            error.errorCode == PlaybackException.ERROR_CODE_VIDEO_FRAME_PROCESSING_FAILED ||
            generateSequence(error.cause) { it.cause }.any { it is VideoFrameProcessingException }
        if(!effectFailure) return false
        disableForCompatibility(error)
        return true
    }

    private fun disableForCompatibility(problem: Throwable) {
        applied=false; unavailable=true
        Log.w("LvguoTV","Picture enhancement unavailable; using original video",problem)
        Toast.makeText(app,"画面增强暂时不可用，已切回原画",Toast.LENGTH_LONG).show()
    }

    private companion object {
        // All calls run on the player application thread (the main thread).
        var unavailable=false
    }
}

/** Applies contrast-adaptive luminance sharpening while limiting noise and halos. */
@androidx.annotation.OptIn(UnstableApi::class)
private class MildSharpenEffect: GlEffect {
    override fun toGlShaderProgram(context: Context,useHdr: Boolean): GlShaderProgram =
        MildSharpenShaderProgram(context,useHdr)
}

@androidx.annotation.OptIn(UnstableApi::class)
private class MildSharpenShaderProgram(context: Context,useHdr: Boolean): BaseGlShaderProgram(useHdr,1) {
    private val program: GlProgram

    init {
        try {
            program=GlProgram(context,"shaders/lvguo_sharpen_vertex.glsl","shaders/lvguo_sharpen_fragment.glsl")
            program.setBufferAttribute("aFramePosition",GlUtil.getNormalizedCoordinateBounds(),GlUtil.HOMOGENEOUS_COORDINATE_VECTOR_SIZE)
            // Media3 1.8 uses SDR BT.709 as its default working space. HDR is passed through unchanged.
            program.setFloatUniform("uAmount",if(useHdr) 0f else 0.55f)
        } catch(problem: IOException) {
            throw VideoFrameProcessingException(problem)
        } catch(problem: GlUtil.GlException) {
            throw VideoFrameProcessingException(problem)
        }
    }

    override fun configure(inputWidth: Int,inputHeight: Int): Size {
        program.setFloatsUniform("uTexelSize",floatArrayOf(1f/inputWidth,1f/inputHeight))
        return Size(inputWidth,inputHeight)
    }

    override fun drawFrame(inputTexId: Int,presentationTimeUs: Long) {
        try {
            program.use()
            program.setSamplerTexIdUniform("uTexSampler",inputTexId,0)
            program.bindAttributesAndUniforms()
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP,0,4)
            GlUtil.checkGlError()
        } catch(problem: GlUtil.GlException) {
            throw VideoFrameProcessingException(problem,presentationTimeUs)
        }
    }

    override fun release() {
        try {
            super.release()
        } finally {
            try { program.delete() } catch(problem: GlUtil.GlException) { throw VideoFrameProcessingException(problem) }
        }
    }
}
