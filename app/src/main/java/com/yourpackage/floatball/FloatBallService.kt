package com.yourpackage.floatball

import android.animation.ValueAnimator
import android.app.Service
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.IBinder
import android.view.GestureDetector
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.TextView

/**
 * 悬浮球本体：Service + WindowManager叠加层，不是Activity，
 * 不会被任何ROM的横竖屏锁影响。
 *
 * 装好后需要的两条授权（跟简悬浮一样的流程）：
 *   adb shell appops set com.yourpackage.floatball SYSTEM_ALERT_WINDOW allow
 *   adb shell settings put secure enabled_accessibility_services \
 *       com.yourpackage.floatball/com.yourpackage.floatball.BackAccessibilityService
 *   adb shell settings put secure accessibility_enabled 1
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

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    private fun setupGestureDetector() {
        gestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                BackAccessibilityService.instance?.doBack()
                return true
            }

            override fun onDoubleTap(e: MotionEvent): Boolean {
                launchTargetApp()
                return true
            }

            override fun onLongPress(e: MotionEvent) {
                BackAccessibilityService.instance?.doHome()
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
