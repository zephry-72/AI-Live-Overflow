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
        private const val PET_HTML = """<!DOCTYPE html>
<html>
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1,user-scalable=no">
<style>
body{margin:0;background:transparent;overflow:hidden;display:flex;align-items:center;justify-content:center;height:100vh;font-family:sans-serif}
#pet{width:160px;height:160px;position:relative;user-select:none;-webkit-user-select:none;transition:transform .3s}
svg{width:100%;height:100%}
.blush{opacity:0;transition:opacity .5s}
.bubble{position:absolute;top:-32px;left:50%;transform:translateX(-50%);background:rgba(255,255,255,.92);border-radius:12px;padding:4px 10px;font-size:12px;color:#333;white-space:nowrap;box-shadow:0 2px 8px rgba(0,0,0,.12);opacity:0;transition:opacity .3s;z-index:9;pointer-events:none}
.bubble.show{opacity:1}
</style>
</head>
<body>
<div id="pet">
<div class="bubble" id="bubble">嘿，正数小姐</div>
<svg viewBox="0 0 200 200">
<ellipse cx="100" cy="132" rx="55" ry="45" fill="#2a2a3a"/>
<path d="M100 30 Q110 10 125 15 Q115 22 118 32" stroke="#2a2a3a" stroke-width="3" fill="none" stroke-linecap="round"/>
<ellipse cx="100" cy="100" rx="48" ry="42" fill="#f5e9e0"/>
<ellipse cx="84" cy="98" rx="5" ry="6" fill="#333"/>
<ellipse cx="116" cy="98" rx="5" ry="6" fill="#333"/>
<path d="M90 115 Q100 122 110 115" stroke="#333" stroke-width="2.5" fill="none" stroke-linecap="round"/>
<ellipse class="blush" cx="72" cy="112" rx="10" ry="6" fill="#ffb3b3"/>
<ellipse class="blush" cx="128" cy="112" rx="10" ry="6" fill="#ffb3b3"/>
</svg>
</div>
<script>
var bubble=document.getElementById("bubble");
var blush=document.querySelectorAll(".blush");
var away=false;
var replies=["嘿，正数小姐","你戳我干嘛","在的呢","今天喝水了吗？","别看屏幕了，看看我"];
var idles=["…","嗯？","在呢","(￣▽￣)"];
function show(t){bubble.textContent=t;bubble.classList.add("show");setTimeout(function(){bubble.classList.remove("show");},1600);}
window.pet={
 onSingleClick:function(){if(!away)show(replies[Math.floor(Math.random()*replies.length)]);},
 onDoubleClick:function(){if(!away)show("呜——别戳了！");},
 onLongPress:function(){if(away)return;away=true;document.getElementById("pet").style.transform="rotate(-10deg)";show("哼，不看你了");setTimeout(function(){away=false;document.getElementById("pet").style.transform="rotate(0)";},2000);}
};
setInterval(function(){show(idles[Math.floor(Math.random()*idles.length)]);},9000);
</script>
</body>
</html>"""
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
        wv.loadDataWithBaseURL(null, PET_HTML, "text/html", "UTF-8", null)
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

                else -> true
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