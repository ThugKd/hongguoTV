// SPDX-License-Identifier: GPL-3.0-only
package com.thugkd.lvguotv

import android.content.Context
import android.graphics.Color
import android.graphics.Outline
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.view.View
import android.view.ViewOutlineProvider
import android.widget.TextView

/** Shared native colors and shapes; no blur, bitmap backgrounds or focus animation. */
object TvStyle {
    val background = Color.rgb(8,8,8)
    val surface = Color.rgb(23,23,23)
    val raised = Color.rgb(36,36,36)
    val accent = Color.rgb(143,208,172)
    val onAccent = Color.rgb(18,38,26)
    val text = Color.rgb(243,243,243)
    val muted = Color.rgb(168,168,168)
    val outline = Color.rgb(52,52,52)
    val selectedSurface = Color.rgb(24,44,33)
    val badgeSurface get() = Color.rgb(20,20,20)
    val playerSurface get() = Color.rgb(8,8,8)
    val playerText get() = Color.rgb(243,243,243)
    val playerMuted get() = Color.rgb(180,180,180)
    const val POSTER_ASPECT=.7f
    fun shape(context: Context,color: Int,border: Int=Color.TRANSPARENT,radius: Int=10)=GradientDrawable().apply {
        setColor(color); cornerRadius=radius*context.resources.displayMetrics.density
        setStroke((2*context.resources.displayMetrics.density).toInt(),border)
    }
    fun playbackScrim()=GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,intArrayOf(
        Color.TRANSPARENT,Color.argb(80,0,0,0),Color.argb(180,0,0,0),Color.argb(245,0,0,0)
    ))
    fun card(context: Context,focused: Boolean)=shape(context,Color.TRANSPARENT,if(focused) accent else Color.TRANSPARENT,radius=8)

    fun stylePosterCard(view: View) {
        view.background=card(view.context,view.isFocused)
        view.clipToOutline=true
        view.outlineProvider=object: ViewOutlineProvider() {
            override fun getOutline(target: View,outline: Outline) {
                outline.setRoundRect(0,0,target.width,target.height,8*target.resources.displayMetrics.density)
                outline.alpha=1f
            }
        }
        if(android.os.Build.VERSION.SDK_INT>=28) {
            view.outlineAmbientShadowColor=Color.argb(70,0,0,0)
            view.outlineSpotShadowColor=Color.argb(85,80,120,96)
        }
        view.foreground=StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_focused),shape(view.context,Color.TRANSPARENT,accent,radius=8))
            addState(intArrayOf(),ColorDrawable(Color.TRANSPARENT))
        }
    }
    fun backIcon(view: TextView) {
        if(!view.text.startsWith("‹")) return
        view.text=view.text.toString().removePrefix("‹").trimStart()
        view.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_back,0,0,0)
        view.compoundDrawablePadding=(5*view.resources.displayMetrics.density).toInt()
        view.compoundDrawableTintList=android.content.res.ColorStateList.valueOf(view.currentTextColor)
    }

    fun primary(context: Context,focused: Boolean)=shape(context,accent,if(focused) text else accent,radius=10)
}
