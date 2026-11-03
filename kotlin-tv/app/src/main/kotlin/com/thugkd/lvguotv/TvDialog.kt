// SPDX-License-Identifier: GPL-3.0-only
package com.thugkd.lvguotv

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

/**
 * 统一弹窗视觉层：实色墨绿面板、圆角、细描边、翡翠绿焦点。
 * 所有原生 AlertDialog 统一走这里，保证全应用弹窗观感一致。
 * 注意：构建方法只创建不显示，由调用方决定何时 show() 与焦点恢复。
 */
object TvDialog {

    private fun dp(activity: Activity, value: Int) = (value * activity.resources.displayMetrics.density).toInt()

    /** 弹窗内容卡片：圆角 + 细描边 + 主题 surface 底色。 */
    fun card(activity: Activity, content: View): LinearLayout {
        val card = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                setColor(TvStyle.surface)
                cornerRadius = dp(activity, 16).toFloat()
                setStroke(dp(activity, 1), TvStyle.outline)
            }
            elevation=dp(activity,12).toFloat()
            setPadding(dp(activity, 24), dp(activity, 22), dp(activity, 24), dp(activity, 20))
        }
        card.addView(content)
        return card
    }

    /** 弹窗标题：20sp 加粗，底部留间距。 */
    fun title(activity: Activity, text: String): TextView = TextView(activity).apply {
        this.text = text
        textSize = 20f
        setTypeface(null, android.graphics.Typeface.BOLD)
        setTextColor(TvStyle.text)
        setPadding(0, 0, 0, dp(activity, 14))
    }

    /** 弹窗正文/说明：15sp，muted 色，行高舒适。 */
    fun body(activity: Activity, text: String): TextView = TextView(activity).apply {
        this.text = text
        textSize = 15f
        setTextColor(TvStyle.muted)
        setLineSpacing(0f, 1.25f)
        setPadding(0, 0, 0, dp(activity, 12))
    }

    /** 胶囊按钮：默认 surface 底，焦点 accent 填充 + 深色文字。 */
    fun capsuleButton(activity: Activity, text: String, onClick: () -> Unit): Button {
        val dp = { v: Int -> dp(activity, v) }
        return Button(activity).apply {
            id=View.generateViewId()
            isFocusableInTouchMode=true
            this.text = text
            textSize = 15f
            setTextColor(TvStyle.text)
            TvStyle.backIcon(this)
            minHeight = dp(44)
            setPadding(dp(20), 0, dp(20), 0)
            background = GradientDrawable().apply {
                setColor(TvStyle.surface)
                cornerRadius = dp(10).toFloat()
                setStroke(dp(1), TvStyle.outline)
            }
            setOnFocusChangeListener { _, focused ->
                background = GradientDrawable().apply {
                    setColor(if(focused) TvStyle.accent else TvStyle.surface)
                    cornerRadius = dp(10).toFloat()
                    setStroke(dp(1), if(focused) TvStyle.accent else TvStyle.outline)
                }
                setTextColor(if(focused) TvStyle.onAccent else TvStyle.text)
                compoundDrawableTintList=android.content.res.ColorStateList.valueOf(currentTextColor)
                setTypeface(null, if(focused) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL)
            }
            setOnClickListener { onClick() }
        }
    }

    /** 普通列表适配器：文字颜色跟随主题，深浅卡片底上均可读（系统布局在暖玉白主题下不可读）。 */
    fun listAdapter(activity: Activity, labels: List<String>): android.widget.ArrayAdapter<String> {
        return object : android.widget.ArrayAdapter<String>(activity, 0, labels) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val view = (convertView as? TextView) ?: TextView(activity).apply {
                    textSize = 16f
                    setPadding(dp(activity, 16), dp(activity, 12), dp(activity, 16), dp(activity, 12))
                }
                view.text = getItem(position)
                view.setTextColor(TvStyle.text)
                return view
            }
        }
    }

    /** 勾选列表适配器：单选/多选弹窗通用，文字与勾选标记颜色跟随主题。 */
    fun choiceAdapter(activity: Activity, labels: List<String>, multiple: Boolean = false): android.widget.ArrayAdapter<String> {
        val attr = if(multiple) android.R.attr.listChoiceIndicatorMultiple else android.R.attr.listChoiceIndicatorSingle
        val styled = activity.obtainStyledAttributes(intArrayOf(attr))
        val checkMark = styled.getDrawable(0)
        styled.recycle()
        return object : android.widget.ArrayAdapter<String>(activity, 0, labels) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val view = (convertView as? android.widget.CheckedTextView) ?: android.widget.CheckedTextView(activity).apply {
                    textSize = 16f
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(dp(activity, 16), dp(activity, 12), dp(activity, 16), dp(activity, 12))
                    checkMarkDrawable = checkMark
                    checkMarkTintList = android.content.res.ColorStateList.valueOf(TvStyle.accent)
                }
                view.text = getItem(position)
                view.setTextColor(TvStyle.text)
                // 视图复用时必须显式从 ListView 同步勾选状态，否则焦点移动触发重新绑定后勾选显示会丢失
                (parent as? android.widget.AbsListView)?.let { listView ->
                    if (listView.choiceMode != android.widget.AbsListView.CHOICE_MODE_NONE) {
                        view.isChecked = listView.isItemChecked(position)
                    }
                }
                return view
            }
        }
    }

    /** 单选项适配器：勾选状态由适配器自己维护，不依赖 ListView 的 CHOICE_MODE，
     *  避免视图复用时多行同时显示为选中。 */
    class RadioAdapter(activity: Activity, labels: List<String>) : android.widget.ArrayAdapter<String>(activity, 0, labels) {
        private val host = activity
        var selected: Int = -1
            set(value) { field = value; notifyDataSetChanged() }

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val view = (convertView as? android.widget.CheckedTextView) ?: android.widget.CheckedTextView(host).apply {
                textSize = 16f
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(host, 16), dp(host, 12), dp(host, 16), dp(host, 12))
                val styled = host.obtainStyledAttributes(intArrayOf(android.R.attr.listChoiceIndicatorSingle))
                checkMarkDrawable = styled.getDrawable(0)
                styled.recycle()
                checkMarkTintList = android.content.res.ColorStateList.valueOf(TvStyle.accent)
            }
            view.text = getItem(position)
            view.setTextColor(TvStyle.text)
            view.isChecked = position == selected
            return view
        }
    }

    /** 列表选择弹窗：统一卡片 + 列表 + 底部按钮。 */
    fun choose(
        activity: Activity,
        title: String,
        labels: List<String>,
        onChoice: (Int) -> Unit,
        extra: String? = null,
        onExtra: () -> Unit = {},
        selectedIndex: Int = -1
    ): AlertDialog {
        val content = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        content.addView(title(activity, title))
        val list = android.widget.ListView(activity).apply {
            id=View.generateViewId()
            adapter=if(selectedIndex>=0) RadioAdapter(activity,labels).apply { selected=selectedIndex } else listAdapter(activity,labels)
            selector=TvStyle.shape(activity,TvStyle.selectedSurface,TvStyle.accent,radius=6)
            divider=null
            isFocusableInTouchMode=true
        }
        val listHeight=(labels.size*dp(activity,48)).coerceAtMost((activity.resources.displayMetrics.heightPixels*.55f).toInt())
        content.addView(list, LinearLayout.LayoutParams(-1, listHeight))
        val buttons = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
            setPadding(0, dp(activity, 14), 0, 0)
        }
        if(extra != null) buttons.addView(capsuleButton(activity, extra) { onExtra() }, LinearLayout.LayoutParams(-2, -2).apply { rightMargin = dp(activity, 12) })
        val cancel = capsuleButton(activity, "取消") {}
        buttons.addView(cancel, LinearLayout.LayoutParams(-2, -2))
        content.addView(buttons)
        val dialog = AlertDialog.Builder(activity).setView(card(activity, content)).create()
        var lastSelection=selectedIndex.coerceIn(0,(labels.size-1).coerceAtLeast(0))
        val actions=(0 until buttons.childCount).map { buttons.getChildAt(it) }
        list.nextFocusUpId=list.id
        list.nextFocusDownId=actions.first().id
        list.nextFocusRightId=actions.first().id
        list.nextFocusLeftId=list.id
        list.setOnItemSelectedListener(object: android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) { lastSelection=position }
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
        })
        list.setOnKeyListener { _,key,event ->
            if(event.action!=android.view.KeyEvent.ACTION_DOWN) false
            else when {
                key==android.view.KeyEvent.KEYCODE_DPAD_UP && list.selectedItemPosition<=0 -> true
                key==android.view.KeyEvent.KEYCODE_DPAD_DOWN && list.selectedItemPosition==labels.lastIndex -> { actions.first().requestFocus(); true }
                key==android.view.KeyEvent.KEYCODE_DPAD_RIGHT -> { actions.first().requestFocus(); true }
                else -> false
            }
        }
        actions.forEachIndexed { index,view ->
            view.nextFocusUpId=list.id; view.nextFocusDownId=view.id
            view.nextFocusLeftId=actions[(index-1).coerceAtLeast(0)].id
            view.nextFocusRightId=actions[(index+1).coerceAtMost(actions.lastIndex)].id
            view.setOnKeyListener { _,key,event ->
                if(event.action==android.view.KeyEvent.ACTION_DOWN && key==android.view.KeyEvent.KEYCODE_DPAD_UP) {
                    list.requestFocus(); list.setSelection(lastSelection); true
                } else false
            }
        }
        styleWindow(activity, dialog) {
            list.requestFocus()
            list.setSelectionFromTop(lastSelection,(listHeight-dp(activity,48))/2)
        }
        cancel.setOnClickListener { dialog.dismiss() }
        list.setOnItemClickListener { _, _, index, _ -> dialog.dismiss(); onChoice(index) }
        return dialog
    }

    /** 信息弹窗：标题 + 正文 + 单按钮。 */
    fun info(activity: Activity, title: String, message: String): AlertDialog {
        val content = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        content.addView(title(activity, title))
        content.addView(body(activity, message))
        val buttons = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
            setPadding(0, dp(activity, 14), 0, 0)
        }
        val ok = capsuleButton(activity, "知道了") {}
        buttons.addView(ok, LinearLayout.LayoutParams(-2, -2))
        content.addView(buttons)
        val dialog = AlertDialog.Builder(activity).setView(card(activity, content)).create()
        styleWindow(activity, dialog) { ok.requestFocus() }
        ok.setOnClickListener { dialog.dismiss() }
        return dialog
    }

    /** 确认弹窗：标题 + 正文 + 取消/确认双按钮。 */
    fun confirm(activity: Activity, title: String, message: String, action: () -> Unit): AlertDialog {
        val content = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        content.addView(title(activity, title))
        content.addView(body(activity, message))
        val buttons = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
            setPadding(0, dp(activity, 14), 0, 0)
        }
        val cancel = capsuleButton(activity, "取消") {}
        val ok = capsuleButton(activity, "确认") { action() }
        buttons.addView(cancel, LinearLayout.LayoutParams(-2, -2).apply { rightMargin = dp(activity, 12) })
        buttons.addView(ok, LinearLayout.LayoutParams(-2, -2))
        content.addView(buttons)
        val dialog = AlertDialog.Builder(activity).setView(card(activity, content)).create()
        styleWindow(activity, dialog) { cancel.requestFocus() }
        cancel.setOnClickListener { dialog.dismiss() }
        ok.setOnClickListener { dialog.dismiss(); action() }
        return dialog
    }

    /** 限制滚动容器最大高度，避免长列表把弹窗撑出屏幕（post 到 attach 后执行，未 show 前调用也安全）。 */
    fun capHeight(activity: Activity, view: View, fraction: Float = 0.55f) {
        view.post {
            val cap = (activity.resources.displayMetrics.heightPixels * fraction).toInt()
            if(view.height > cap) { val p = view.layoutParams; p.height = cap; view.layoutParams = p }
        }
    }

    /** 通用弹窗窗口样式：透明背景、居中、限制最大宽度；onShown 合并进同一个 OnShowListener（AlertDialog 仅支持一个）。 */
    fun styleWindow(activity: Activity, dialog: AlertDialog, widthFraction: Float = 0.55f, maxWidthDp: Int = 620, onShown: (() -> Unit)? = null) {
        dialog.setOnShowListener {
            dialog.window?.apply {
                setBackgroundDrawableResource(android.R.color.transparent)
                val width = (activity.resources.displayMetrics.widthPixels * widthFraction).toInt().coerceAtMost(dp(activity, maxWidthDp))
                setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT)
                setGravity(Gravity.CENTER)
            }
            onShown?.invoke()
        }
    }
}
