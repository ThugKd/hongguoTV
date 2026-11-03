// SPDX-License-Identifier: GPL-3.0-only
package com.thugkd.lvguotv

import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.view.ViewTreeObserver
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.media3.ui.PlayerView
import kotlin.math.roundToInt

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
object PlaybackWatermark {
    private const val SIZE_SCALE=1.3f
    private class State(val host: FrameLayout, val mark: View) {
        var pending: ViewTreeObserver.OnPreDrawListener? = null
        fun hide() {
            pending?.let { if(host.viewTreeObserver.isAlive) host.viewTreeObserver.removeOnPreDrawListener(it) }
            pending=null
            mark.visibility=View.INVISIBLE
        }
    }

    fun attach(player: PlayerView) {
        if(state(player)!=null) return
        val host=player.findViewById<FrameLayout>(androidx.media3.ui.R.id.exo_content_frame) ?: player.overlayFrameLayout ?: return
        val context=player.context
        fun dp(value: Int)=(value*context.resources.displayMetrics.density).toInt()
        fun markDp(value: Int)=(value*SIZE_SCALE*context.resources.displayMetrics.density).roundToInt()
        val mark=LinearLayout(context).apply {
            id=R.id.playback_watermark
            visibility=View.INVISIBLE
            orientation=LinearLayout.HORIZONTAL; gravity=Gravity.CENTER_VERTICAL; alpha=.48f
            isFocusable=false; isClickable=false; importantForAccessibility=View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        }
        mark.addView(ImageView(context).apply { setImageResource(R.drawable.ic_watermark); isFocusable=false },LinearLayout.LayoutParams(markDp(14),markDp(14)))
        mark.addView(TextView(context).apply {
            text="绿果TV"; textSize=11f*SIZE_SCALE; setTextColor(Color.WHITE); includeFontPadding=false
            setShadowLayer(dp(2).toFloat(),0f,1f,Color.BLACK); isFocusable=false
        },LinearLayout.LayoutParams(-2,-2).apply { leftMargin=markDp(4) })
        val state=State(host,mark)
        mark.tag=state
        mark.addOnAttachStateChangeListener(object: View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(view: View) {}
            override fun onViewDetachedFromWindow(view: View) { state.hide() }
        })
        host.addView(mark,FrameLayout.LayoutParams(-2,-2,Gravity.TOP or Gravity.END).apply { topMargin=dp(12); rightMargin=dp(12) })
    }

    private fun state(player: PlayerView?)=player?.findViewById<View>(R.id.playback_watermark)?.tag as? State
    fun hide(player: PlayerView?) { state(player)?.hide() }

    fun onFirstFrame(player: PlayerView?) {
        val state=state(player) ?: return
        state.hide()
        // Wait for Media3's aspect-ratio layout before revealing the picture's corner.
        val ready=object: ViewTreeObserver.OnPreDrawListener {
            override fun onPreDraw(): Boolean {
                if(state.host.isLayoutRequested || state.host.width==0 || state.host.height==0) return true
                state.host.viewTreeObserver.removeOnPreDrawListener(this)
                state.pending=null
                state.mark.visibility=View.VISIBLE
                return true
            }
        }
        state.pending=ready
        state.host.viewTreeObserver.addOnPreDrawListener(ready)
        state.host.invalidate()
    }
}
