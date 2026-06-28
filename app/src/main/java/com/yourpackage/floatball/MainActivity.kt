package com.yourpackage.floatball

import android.app.Activity
import android.content.Intent
import android.os.Bundle

/**
 * 全透明、瞬间结束的Activity，唯一作用是给个图标，
 * 第一次装完App后点一下就能把悬浮球拉起来，不用等设备重启。
 * 之后开机会走 BootReceiver 自动启动。
 */
class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        startService(Intent(this, FloatBallService::class.java))
        finish()
    }
}
