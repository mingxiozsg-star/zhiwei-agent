package com.zhiwei.agent.agent

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/**
 * 设备自动化无障碍服务。
 *
 * 这是「让 AI 控制整台设备自动操作」的真正落点：开启后本应用可以模拟点击、滑动、
 * 输入文字、按返回/主页，以及对屏幕上的节点做查找点击。无障碍服务不需要 root，
 * 但必须由用户到系统「无障碍」设置页手动开启一次，之后一直生效（系统可能回收）。
 */
class AccessibilityAgentService : AccessibilityService() {

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onUnbind(intent: Intent?): Boolean {
        instance = null
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        instance = null
        super.onDestroy()
    }

    /** 模拟点击屏幕坐标 */
    fun tap(x: Int, y: Int): Boolean = dispatchGesture(
        android.accessibilityservice.GestureDescription.Builder()
            .addStroke(android.accessibilityservice.GestureDescription.StrokeDescription(
                android.graphics.Path().apply { moveTo(x.toFloat(), y.toFloat()) },
                0,
                80
            ))
            .build(),
        null,
        null
    )

    /** 模拟滑动，from/to 为屏幕坐标 */
    fun swipe(x1: Int, y1: Int, x2: Int, y2: Int, durationMs: Long = 400): Boolean {
        val path = android.graphics.Path().apply {
            moveTo(x1.toFloat(), y1.toFloat())
            lineTo(x2.toFloat(), y2.toFloat())
        }
        return dispatchGesture(
            android.accessibilityservice.GestureDescription.Builder()
                .addStroke(android.accessibilityservice.GestureDescription.StrokeDescription(path, 0, durationMs))
                .build(),
            null,
            null
        )
    }

    /** 在当前焦点输入框输入文字 */
    fun type(text: String): Boolean {
        val root = rootInActiveWindow ?: return false
        val node = findFocus(root) ?: return false
        val args = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        }
        return node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
    }

    /** 按返回键 */
    fun back(): Boolean = performGlobalAction(GLOBAL_ACTION_BACK)

    /** 回主页 */
    fun home(): Boolean = performGlobalAction(GLOBAL_ACTION_HOME)

    /** 打开最近任务 */
    fun recents(): Boolean = performGlobalAction(GLOBAL_ACTION_RECENTS)

    /** 按文本查找可点击节点并点击，返回是否找到 */
    fun clickByText(text: String): Boolean {
        val root = rootInActiveWindow ?: return false
        val node = findByText(root, text) ?: return false
        return node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
    }

    /** 按描述查找可点击节点并点击 */
    fun clickByDesc(desc: String): Boolean {
        val root = rootInActiveWindow ?: return false
        val node = findByDesc(root, desc) ?: return false
        return node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
    }

    /**
     * 把当前屏幕上能点的控件摘出来：文字 + 可点击区域的中心坐标。
     *
     * 模型看不见屏幕，只靠猜坐标基本点不中；先让它读这一份清单，
     * 再照着 (x, y) 调点击，成功率差一个数量级。
     */
    fun dumpScreen(limit: Int = 60): String {
        val root = rootInActiveWindow ?: return "当前没有可读的窗口（可能锁屏了，或者前台应用不允许读取）"
        val lines = mutableListOf<String>()
        collectClickable(root, lines, limit)
        if (lines.isEmpty()) return "当前窗口没有找到可点击的控件"
        return buildString {
            append("当前屏幕可点击控件（限 ").append(limit).append(" 条）：\n")
            lines.forEach { append(it).append('\n') }
            append("用 ui_tap 按上面的坐标点击，或用 ui_click_text 直接按文字点。")
        }
    }

    private fun collectClickable(node: AccessibilityNodeInfo, out: MutableList<String>, limit: Int) {
        if (out.size >= limit) return
        val label = (node.text?.toString()?.trim().orEmpty().ifEmpty {
            node.contentDescription?.toString()?.trim().orEmpty()
        })
        if (node.isClickable && label.isNotEmpty()) {
            val rect = android.graphics.Rect()
            node.getBoundsInScreen(rect)
            val cx = (rect.left + rect.right) / 2
            val cy = (rect.top + rect.bottom) / 2
            out.add("· 「$label」 @ ($cx, $cy)")
        }
        for (i in 0 until node.childCount) {
            if (out.size >= limit) return
            val c = node.getChild(i) ?: continue
            collectClickable(c, out, limit)
        }
    }

    private fun findFocus(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (node.isFocused && node.isEditable) return node
        for (i in 0 until node.childCount) {
            val c = node.getChild(i) ?: continue
            val hit = findFocus(c)
            if (hit != null) return hit
        }
        return null
    }

    private fun findByText(node: AccessibilityNodeInfo, text: String): AccessibilityNodeInfo? {
        if (node.text?.toString()?.contains(text, ignoreCase = true) == true && node.isClickable) return node
        for (i in 0 until node.childCount) {
            val c = node.getChild(i) ?: continue
            val hit = findByText(c, text)
            if (hit != null) return hit
        }
        return null
    }

    private fun findByDesc(node: AccessibilityNodeInfo, desc: String): AccessibilityNodeInfo? {
        if (node.contentDescription?.toString()?.contains(desc, ignoreCase = true) == true && node.isClickable) return node
        for (i in 0 until node.childCount) {
            val c = node.getChild(i) ?: continue
            val hit = findByDesc(c, desc)
            if (hit != null) return hit
        }
        return null
    }

    companion object {
        @Volatile
        private var instance: AccessibilityAgentService? = null

        /**
         * 是否已开启。
         *
         * 先看服务实例（同一进程内，开启后立刻就有），拿不到再查系统的
         * ENABLED_ACCESSIBILITY_SERVICES 列表 —— 刚在系统设置里点开、服务还没连上时也不会误报「未开启」。
         */
        fun isEnabled(context: Context? = null): Boolean {
            if (instance != null) return true
            val ctx = context ?: return false
            val component = android.content.ComponentName(ctx, AccessibilityAgentService::class.java)
            // 系统里存的是 package/class，可能用全名也可能用短名，两种都比一遍
            val names = listOf(component.flattenToString(), component.flattenToShortString())
            val enabled = Settings.Secure.getString(
                ctx.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: return false
            return enabled.split(':').any { item ->
                names.any { it.equals(item.trim(), ignoreCase = true) }
            }
        }

        fun get(): AccessibilityAgentService? = instance

        /** 跳到系统无障碍设置页，引导用户开启本服务 */
        fun openSettings(context: Context) {
            runCatching {
                val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
            }
        }
    }
}
