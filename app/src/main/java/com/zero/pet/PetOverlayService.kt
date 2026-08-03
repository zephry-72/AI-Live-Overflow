package com.zero.pet

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.JavascriptInterface

class PetOverlayService : Service() {

    companion object {
        private const val TAG = "PetOverlay"
        private const val CHANNEL_ID = "pet_overlay_channel"
        private const val NOTIFICATION_ID = 1001

        // 手势判定参数
        private const val CLICK_MAX_MS = 600L
        private const val DOUBLE_CLICK_GAP_MS = 300L
        private const val LONG_PRESS_MS = 600L
        private const val MOVE_SLOP = 10
    }

    private lateinit var windowManager: WindowManager
    private var overlayView: View? = null
    private var webView: WebView? = null

    // 手势状态
    private var downX = 0f
    private var downY = 0f
    private var downTime = 0L
    private var isDragging = false
    private var isLongPressTriggered = false
    private var lastClickTime = 0L

    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    private var longPressRunnable: Runnable = Runnable {
        if (!isDragging) {
            isLongPressTriggered = true
            webView?.evaluateJavascript("window.pet && window.pet.onLongPress && window.pet.onLongPress();", null)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (overlayView == null) {
            showOverlay()
        }
        return START_STICKY
    }

    private fun showOverlay() {
        if (!Settings.canDrawOverlays(this)) return

        val inflater = LayoutInflater.from(this)
        overlayView = inflater.inflate(R.layout.overlay_pet, null).apply {
            webView = findViewById(R.id.pet_webview)
            setupWebView(webView!!)
            setupTouchListener(this)
        }

        val params = WindowManager.LayoutParams(
            220,
            280,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 300
            y = 300
        }

        windowManager.addView(overlayView, params)
        Log.d(TAG, "overlay added")
    }

    private fun setupWebView(wv: WebView) {
        wv.setBackgroundColor(0x00000000)
        wv.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = true
            cacheMode = WebSettings.LOAD_NO_CACHE
            mediaPlaybackRequiresUserGesture = false
        }
        wv.addJavascriptInterface(PetBridge(), "android")
        wv.loadUrl("file:///android_asset/pet.html")
    }

    private fun setupTouchListener(view: View) {
        view.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.rawX
                    downY = event.rawY
                    downTime = System.currentTimeMillis()
                    isDragging = false
                    isLongPressTriggered = false
                    handler.postDelayed(longPressRunnable, LONG_PRESS_MS)
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - downX
                    val dy = event.rawY - downY
                    if (!isDragging && (Math.abs(dx) > MOVE_SLOP || Math.abs(dy) > MOVE_SLOP)) {
                        isDragging = true
                        handler.removeCallbacks(longPressRunnable)
                    }
                    if (isDragging) {
                        val params = overlayView?.layoutParams as? WindowManager.LayoutParams ?: return@setOnTouchListener true
                        params.x = (params.x + dx).toInt()
                        params.y = (params.y + dy).toInt()
                        windowManager.updateViewLayout(overlayView, params)
                        downX = event.rawX
                        downY = event.rawY
                    }
                    true
                }

                MotionEvent.ACTION_UP -> {
                    handler.removeCallbacks(longPressRunnable)
                    val elapsed = System.currentTimeMillis() - downTime
                    if (!isDragging && !isLongPressTriggered) {
                        if (elapsed < CLICK_MAX_MS) {
                            val now = System.currentTimeMillis()
                            if (now - lastClickTime < DOUBLE_CLICK_GAP_MS) {
                                webView?.evaluateJavascript("window.pet && window.pet.onDoubleClick && window.pet.onDoubleClick();", null)
                                lastClickTime = 0
                            } else {
                                lastClickTime = now
                                webView?.evaluateJavascript("window.pet && window.pet.onSingleClick && window.pet.onSingleClick();", null)
                            }
                        }
                    }
                    true
                }
            }
        }
    }

    inner class PetBridge {
        @JavascriptInterface
        fun log(message: String) {
            Log.d(TAG, "[pet.js] $message")
        }
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "零点桌宠",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "零点正陪着你呢"
            setShowBadge(false)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_IMMUTABLE
        )
        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("零点在呢")
            .setContentText("戳我或者长按都有反应哦")
            .setSmallIcon(android.R.drawable.ic_menu_myplaces)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        overlayView?.let { windowManager.removeView(it) }
        overlayView = null
        super.onDestroy()
    }
}
