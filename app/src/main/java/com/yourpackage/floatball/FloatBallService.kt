package com.yourpackage.floatball

import android.animation.ValueAnimator
import android.app.ActivityManager
import android.app.Service
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.IBinder
import android.view.GestureDetector
import android.view.Gravity
import android.view.MotionEvent
import android.view.WindowManager
import android.widget.TextView

/**
 * 悬浮球本体：Service + WindowManager叠加层，不是Activity，
 * 不会被任何ROM的横竖屏锁影响。
 *
 * 新方案：不再依赖 AccessibilityService / performGlobalAction，
 * 因为航盛这台ROM会在几秒内自动重置 enabled_accessibility_services，
 * 而且 system 级的 systemsetting provider 是签名权限保护的，三方App写不进去，
 * 这条路在这台机器上走不通。
 *
 * 改用任务栈管理（REORDER_TASKS/GET_TASKS是普通权限，不需要ROM特殊授权）：
 *   - "返回桌面" 用 ACTION_MAIN + CATEGORY_HOME 的 Intent 跳转
 *   - "退出当前App" 优先尝试 am force-stop（需要shell权限，部分机型可用），
 *     不行就退化为跳转桌面
 *
 * 装好后唯一需要的授权：
 *   adb shell appops set com.yourpackage.floatball SYSTEM_ALERT_WINDOW allow
 */
class FloatBallService : Service() {

    companion object {
        // 双击想打开的App，改成你想要的包名，比如导航/音乐App
        private const val DOUBLE_TAP_TARGET_PACKAGE = "com.autonavi.amapauto"
        private const val BALL_SIZE_DP = 48
    }

    private lateinit var windowManager: WindowManager
    private var floatView: TextView? = null
    private lateinit var params: WindowManager.LayoutParams
    private lateinit var gestureDetector: GestureDetector

    private var isDragging = false
    private var startX = 0
    private var startY = 0
    private var touchStartRawX = 0f
    private var touchStartRawY = 0f

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        setupGestureDetector()
        addFloatBall()
    }

    /** 单击：退出当前App（优先force-stop，失败则退化为回桌面） */
    private fun exitCurrentApp() {
        val am = getSystemService(ACTIVITY_SERVICE) as ActivityManager
        val topPackage = getTopPackageName(am)

        if (topPackage != null && topPackage != packageName) {
            val killed = tryForceStop(topPackage)
            if (killed) return
        }
        // force-stop不可用（没有shell权限）时，退化为跳桌面
        goHome()
    }

    /** 长按：直接回桌面，等价于过去的 GLOBAL_ACTION_HOME */
    private fun goHome() {
        val intent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        startActivity(intent)
    }

    /**
     * 需要 GET_TASKS 权限。在 Android 5.0+ getRunningTasks 已被限制只能看到自己的任务，
     * 但这台车机是 Android 4.4.3（API 19），限制还没加上，getRunningTasks 仍然有效。
     */
    @Suppress("DEPRECATION")
    private fun getTopPackageName(am: ActivityManager): String? {
        return try {
            val tasks = am.getRunningTasks(1)
            tasks.firstOrNull()?.topActivity?.packageName
        } catch (e: SecurityException) {
            null
        }
    }

    /**
     * 通过shell调用 am force-stop。普通三方App签名在大多数车机上没有shell权限会直接抛异常，
     * 如果你的APK是system签名或者设备本身允许，这里能直接生效。
     */
    private fun tryForceStop(targetPackage: String): Boolean {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("am", "force-stop", targetPackage))
            process.waitFor()
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    private fun setupGestureDetector() {
        gestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                exitCurrentApp()
                return true
            }

            override fun onDoubleTap(e: MotionEvent): Boolean {
                launchTargetApp()
                return true
            }

            override fun onLongPress(e: MotionEvent) {
                goHome()
            }
        })
    }

    private fun launchTargetApp() {
        val launchIntent = packageManager.getLaunchIntentForPackage(DOUBLE_TAP_TARGET_PACKAGE)
        if (launchIntent != null) {
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(launchIntent)
        }
    }

    private fun addFloatBall() {
        val ball = TextView(this).apply {
            text = "●"
            setTextColor(Color.WHITE)
            textSize = 16f
            gravity = Gravity.CENTER
            background = ballBackground()
        }

        params = WindowManager.LayoutParams(
            dp(BALL_SIZE_DP),
            dp(BALL_SIZE_DP),
            WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = 300
        }

        ball.setOnTouchListener { _, event ->
            gestureDetector.onTouchEvent(event)
            handleDrag(event)
            true
        }

        floatView = ball
        windowManager.addView(ball, params)
    }

    private fun handleDrag(event: MotionEvent) {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                startX = params.x
                startY = params.y
                touchStartRawX = event.rawX
                touchStartRawY = event.rawY
                isDragging = false
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = (event.rawX - touchStartRawX).toInt()
                val dy = (event.rawY - touchStartRawY).toInt()
                if (isDragging || Math.abs(dx) > dp(8) || Math.abs(dy) > dp(8)) {
                    isDragging = true
                    params.x = startX + dx
                    params.y = startY + dy
                    windowManager.updateViewLayout(floatView, params)
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (isDragging) {
                    snapToEdge()
                }
                isDragging = false
            }
        }
    }

    /** 松手后自动吸到屏幕最近的左边或右边 */
    private fun snapToEdge() {
        val screenWidth = resources.displayMetrics.widthPixels
        val ballWidth = dp(BALL_SIZE_DP)
        val targetX = if (params.x + ballWidth / 2 < screenWidth / 2) {
            0
        } else {
            screenWidth - ballWidth
        }

        val animator = ValueAnimator.ofInt(params.x, targetX)
        animator.duration = 200
        animator.addUpdateListener { anim ->
            params.x = anim.animatedValue as Int
            floatView?.let { windowManager.updateViewLayout(it, params) }
        }
        animator.start()
    }

    private fun ballBackground(): GradientDrawable = GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(Color.parseColor("#CC1A1A1A"))
    }

    override fun onDestroy() {
        super.onDestroy()
        floatView?.let { windowManager.removeView(it) }
    }
}
