package com.pixelcat.overlay

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.graphics.Point
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.WindowManager
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.core.app.NotificationCompat

class PetOverlayService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var webView: WebView
    private lateinit var layoutParams: WindowManager.LayoutParams

    private val handler = Handler(Looper.getMainLooper())
    private val supabase = SupabaseClient.getInstance()

    private var touchStartTime = 0L
    private var touchStartX = 0f
    private var touchStartY = 0f
    private var isDragging = false
    private var tapPending = false
    private var lastTapTime = 0L

    companion object {
        const val TAG = "PixelCat"
        const val CHANNEL_ID = "pet_overlay_channel"
        const val NOTIFICATION_ID = 1001
        const val DOUBLE_TAP_THRESHOLD = 300L
        const val LONG_PRESS_THRESHOLD = 600L
        const val DRAG_THRESHOLD = 10f
    }

    inner class PetHost {
        @JavascriptInterface
        fun onEvent(action: String, emotion: String) {
            Log.d(TAG, "PetEvent: action=$action emotion=$emotion")
            supabase.logEvent(
                action = action,
                emotion = emotion,
                onSuccess = { Log.d(TAG, "事件已同步到云端") },
                onError = { Log.e(TAG, "同步失败: $it") }
            )
        }
    }

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        createNotificationChannel()
        setupWebView()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, buildNotification())
        showOverlay()
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        hideOverlay()
        webView.destroy()
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "Pixel Cat", NotificationManager.IMPORTANCE_LOW
            ).apply { description = "像素猫咪正在陪伴你" }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun buildNotification() =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Pixel Cat")
            .setContentText("猫咪正在屏幕上～")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(createLaunchIntent())
            .setOngoing(true)
            .build()

    private fun createLaunchIntent(): PendingIntent {
        val intent = Intent(this, MainActivity::class.java)
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
                    PendingIntent.FLAG_IMMUTABLE else 0
        return PendingIntent.getActivity(this, 0, intent, flags)
    }

    private fun setupWebView() {
        webView = WebView(this).apply {
            setBackgroundColor(0x00000000)
            settings.apply {
                javaScriptEnabled = true
                allowFileAccess = true
                domStorageEnabled = true
            }
            addJavascriptInterface(PetHost(), "PetHost")
            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    injectPetHostBridge()
                }
            }
            loadUrl("file:///android_asset/pixel-cat/pet.html")
            setOnTouchListener { _, event -> handleTouch(event) }
        }
    }

    private fun injectPetHostBridge() {
        val js = """
            (function() {
                if (window.petEngine) {
                    window.petEngine._notifyHost = function(action, emotion) {
                        try { PetHost.onEvent(action, emotion); } catch(e) {}
                    };
                }
            })();
        """.trimIndent()
        webView.evaluateJavascript(js, null)
    }

    private fun handleTouch(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                touchStartTime = System.currentTimeMillis()
                touchStartX = event.rawX
                touchStartY = event.rawY
                isDragging = false
                tapPending = true
                handler.postDelayed({
                    if (tapPending && !isDragging) {
                        tapPending = false
                        onLongPress()
                    }
                }, LONG_PRESS_THRESHOLD)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = event.rawX - touchStartX
                val dy = event.rawY - touchStartY
                if (Math.sqrt((dx * dx + dy * dy).toDouble()) > DRAG_THRESHOLD) {
                    isDragging = true
                    tapPending = false
                    layoutParams.x = event.rawX.toInt() - webView.width / 2
                    layoutParams.y = event.rawY.toInt() - webView.height / 2
                    windowManager.updateViewLayout(webView, layoutParams)
                }
                return true
            }
            MotionEvent.ACTION_UP -> {
                if (isDragging || !tapPending) return true
                tapPending = false
                val timeSinceLastTap = System.currentTimeMillis() - lastTapTime
                lastTapTime = System.currentTimeMillis()
                if (timeSinceLastTap < DOUBLE_TAP_THRESHOLD && timeSinceLastTap > 0) {
                    onDoubleTap()
                } else {
                    onTap()
                }
                return true
            }
        }
        return false
    }

    private fun onTap() {
        webView.evaluateJavascript("window.petEngine && window.petEngine.onTap()", null)
    }

    private fun onDoubleTap() {
        webView.evaluateJavascript("window.petEngine && window.petEngine.onDoubleTap()", null)
    }

    private fun onLongPress() {
        webView.evaluateJavascript("window.petEngine && window.petEngine.onLongPress()", null)
    }

    private fun showOverlay() {
        val displaySize = Point()
        windowManager.defaultDisplay.getSize(displaySize)
        val w = dpToPx(180)
        val h = dpToPx(240)
        layoutParams = WindowManager.LayoutParams(
            w, h,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = (displaySize.x - w) / 2
            y = displaySize.y / 4
        }
        windowManager.addView(webView, layoutParams)
    }

    private fun hideOverlay() {
        try { windowManager.removeView(webView) } catch (_: Exception) {}
    }

    private fun dpToPx(dp: Int): Int = (dp * resources.displayMetrics.density).toInt()
}