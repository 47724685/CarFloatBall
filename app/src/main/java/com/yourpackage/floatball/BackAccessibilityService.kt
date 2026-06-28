package com.yourpackage.floatball

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent

/**
 * 只用来执行全局手势，不监听页面内容，onAccessibilityEvent留空省资源。
 *
 * 需要通过ADB开启（车机的无障碍设置页一般被锁/藏起来）：
 *   adb shell settings put secure enabled_accessibility_services \
 *       com.yourpackage.floatball/com.yourpackage.floatball.BackAccessibilityService
 *   adb shell settings put secure accessibility_enabled 1
 */
class BackAccessibilityService : AccessibilityService() {

    companion object {
        @Volatile
        var instance: BackAccessibilityService? = null
            private set
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // 不需要处理
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        super.onDestroy()
        instance = null
    }

    fun doBack(): Boolean = performGlobalAction(GLOBAL_ACTION_BACK)

    fun doHome(): Boolean = performGlobalAction(GLOBAL_ACTION_HOME)
}
