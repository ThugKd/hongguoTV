// SPDX-License-Identifier: GPL-3.0-only
package com.thugkd.lvguotv

import android.content.Context
import android.graphics.Color
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.graphics.drawable.ScaleDrawable
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView

/** Shared poster presentation; callers retain artwork loading, actions and focus restoration. */
class SeriesCard(context: Context): LinearLayout(context) {
    private fun dp(value: Int)=(value*resources.displayMetrics.density).toInt()
    private fun label(size: Float,color: Int)=TextView(context).apply {
        textSize=size; setTextColor(color); includeFontPadding=false
        maxLines=1; ellipsize=TextUtils.TruncateAt.END
    }
    val image=ImageView(context).apply {
        scaleType=ImageView.ScaleType.CENTER_CROP
        importantForAccessibility=IMPORTANT_FOR_ACCESSIBILITY_NO
    }
    private val artwork=object: FrameLayout(context) {
        override fun onMeasure(widthSpec: Int,heightSpec: Int) {
            val height=(MeasureSpec.getSize(widthSpec)/.76f).toInt()
            super.onMeasure(widthSpec,MeasureSpec.makeMeasureSpec(height,MeasureSpec.EXACTLY))
        }
    }.apply { background=TvStyle.shape(context,TvStyle.surface,radius=8); clipToOutline=true }
    val badge=label(11f,Color.WHITE).apply {
        gravity=Gravity.END; setPadding(dp(6),dp(4),dp(6),dp(4))
        fontFeatureSettings="tnum"
        importantForAccessibility=IMPORTANT_FOR_ACCESSIBILITY_NO
    }
    val rank=label(13f,TvStyle.onAccent).apply {
        typeface=Typeface.create("sans-serif-medium",Typeface.NORMAL)
        gravity=Gravity.CENTER; minWidth=dp(30); setPadding(dp(6),0,dp(6),0)
        visibility=GONE; importantForAccessibility=IMPORTANT_FOR_ACCESSIBILITY_NO
    }
    val title=label(13f,TvStyle.text).apply { maxLines=2; minLines=2; setPadding(dp(5),dp(6),dp(5),dp(3)) }
    private val heat=label(13f,Color.rgb(231,196,133)).apply {
        typeface=Typeface.create("sans-serif-medium",Typeface.NORMAL); visibility=GONE
        setPadding(dp(5),dp(2),dp(5),dp(5)); setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_heat,0,0,0)
        compoundDrawablePadding=dp(3)
    }
    private val progress=ProgressBar(context,null,android.R.attr.progressBarStyleHorizontal).apply {
        max=100; visibility=GONE
        setPadding(0,0,0,0)
        fun rail(color: Int)=GradientDrawable().apply { setColor(color); cornerRadius=dp(2).toFloat() }
        progressDrawable=LayerDrawable(arrayOf(
            rail(Color.argb(55,255,255,255)),
            ScaleDrawable(rail(TvStyle.accent),Gravity.START,1f,-1f)
        )).apply {
            setId(0,android.R.id.background)
            setId(1,android.R.id.progress)
        }
        importantForAccessibility=IMPORTANT_FOR_ACCESSIBILITY_NO
    }
    private val footer=LinearLayout(context).apply {
        orientation=VERTICAL
        background=GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,intArrayOf(Color.TRANSPARENT,Color.argb(215,0,0,0)))
        addView(badge,LayoutParams(-1,-2))
        addView(progress,LayoutParams(-1,dp(2)).apply {
            leftMargin=dp(8); rightMargin=dp(8); bottomMargin=dp(9)
        })
    }
    init {
        orientation=VERTICAL; id=View.generateViewId()
        isFocusable=true; isFocusableInTouchMode=true; setPadding(0,0,0,dp(3))
        TvStyle.stylePosterCard(this)
        artwork.addView(image,FrameLayout.LayoutParams(-1,-1))
        artwork.addView(footer,FrameLayout.LayoutParams(-1,-2,Gravity.BOTTOM))
        artwork.addView(rank,FrameLayout.LayoutParams(-2,dp(24),Gravity.TOP or Gravity.START))
        val titleHeight=title.lineHeight*2+title.compoundPaddingTop+title.compoundPaddingBottom
        addView(artwork,LayoutParams(-1,-2)); addView(title,LayoutParams(-1,titleHeight)); addView(heat,LayoutParams(-1,-2))
    }
    fun bindText(name: String,info: String) {
        title.text=name; badge.text=info; badge.visibility=if(info.isBlank()) GONE else VISIBLE
    }
    fun showProgress(percent: Int?) {
        progress.visibility=if(percent==null) GONE else VISIBLE
        progress.progress=percent?.coerceIn(0,100) ?: 0
        badge.gravity=if(percent==null) Gravity.END else Gravity.START
        if(percent==null) badge.setPadding(dp(6),dp(4),dp(6),dp(4))
        else badge.setPadding(dp(8),dp(14),dp(8),dp(7))
    }
    fun showHeat(value: String?) {
        heat.text=value.orEmpty(); heat.visibility=if(value.isNullOrBlank()) GONE else VISIBLE
    }
    fun showRank(position: Int?) {
        val leading=position!=null && position in 1..3
        rank.text=position?.toString()?.padStart(2,'0') ?: "—"
        rank.setTextColor(if(leading) TvStyle.onAccent else Color.rgb(30,48,39))
        rank.background=GradientDrawable().apply {
            setColor(if(leading) TvStyle.accent else Color.rgb(183,199,189))
            val corner=dp(8).toFloat()
            val bottom=dp(6).toFloat()
            cornerRadii=floatArrayOf(corner,corner,0f,0f,bottom,bottom,0f,0f)
        }
        rank.visibility=VISIBLE
    }
    override fun onFocusChanged(gainFocus: Boolean,direction: Int,previouslyFocusedRect: Rect?) {
        super.onFocusChanged(gainFocus,direction,previouslyFocusedRect)
        title.setTextColor(if(gainFocus) TvStyle.accent else TvStyle.text)
        elevation=if(gainFocus) dp(6).toFloat() else 0f
    }
}
