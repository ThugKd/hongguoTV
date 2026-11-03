// SPDX-License-Identifier: GPL-3.0-only
package com.thugkd.lvguotv

import android.graphics.Rect
import android.view.View
import android.view.ViewGroup

/** Pick the control in the same horizontal lane before considering other lanes. */
object TvFocus {
    private fun bounds(view: View): Rect {
        val location=IntArray(2)
        view.getLocationOnScreen(location)
        return Rect(location[0],location[1],location[0]+view.width,location[1]+view.height)
    }

    fun nearestHorizontal(origin: View,targets: List<View>): View? {
        val source=bounds(origin)
        return targets.filter { it.isShown && it.isFocusable && it.isEnabled }.minByOrNull { target ->
            val rect=bounds(target)
            val gap=maxOf(0,source.left-rect.right,rect.left-source.right)
            gap.toLong()*100_000L+kotlin.math.abs(source.centerX()-rect.centerX())
        }
    }

    fun move(origin: View,targets: List<View>): Boolean=nearestHorizontal(origin,targets)?.requestFocus()==true

    fun firstVisibleRow(container: ViewGroup,accept: (View)->Boolean): List<View> {
        val viewport=Rect()
        container.getGlobalVisibleRect(viewport)
        val visible=(0 until container.childCount).map { container.getChildAt(it) }.filter { view ->
            val rect=Rect()
            accept(view) && view.isShown && view.getGlobalVisibleRect(rect) && Rect.intersects(viewport,rect)
        }
        val complete=visible.filter { bounds(it).let { rect -> rect.top>=viewport.top && rect.bottom<=viewport.bottom } }
        val candidates=complete.ifEmpty { visible }
        val firstTop=candidates.minOfOrNull { it.top }
        return candidates.filter { it.top==firstTop }
    }
}
