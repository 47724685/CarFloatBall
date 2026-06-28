package com.yourpackage.floatball

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * 开机自启。如果车机ROM对开机广播有自定义限制导致这个不触发，
 * 退路是直接在你自己的Launcher的onCreate里加一句
 * startService(Intent(this, FloatBallService::class.java))
 * ——反正你的Launcher本来就是车机的Home，开机必跑。
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action == Intent.ACTION_BOOT_COMPLETED) {
            context.startService(Intent(context, FloatBallService::class.java))
        }
    }
}
