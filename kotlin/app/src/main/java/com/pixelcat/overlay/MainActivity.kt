package com.pixelcat.overlay

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 80, 40, 40)
            setBackgroundColor(Color.argb(0xAA, 0x2A, 0x2A, 0x3A))
        }

        val title = TextView(this).apply {
            text = "\uD83D\uDC31 Pixel Cat"
            textSize = 24f
            setTextColor(Color.argb(0xFF, 0xA8, 0xB8, 0xC8))
            setPadding(0, 0, 0, 20)
        }
        layout.addView(title)

        val desc = TextView(this).apply {
            text = "\u8BA9\u50CF\u7D20\u732B\u54AA\u6D6E\u5728\u4F60\u7684\u5C4F\u5E55\u4E0A\uFF5E"
            textSize = 14f
            setTextColor(Color.argb(0xCC, 0x8A, 0x9A, 0xAA))
            setPadding(0, 0, 0, 40)
        }
        layout.addView(desc)

        val btnStart = Button(this).apply {
            text = "\u53EC\u5524\u732B\u54AA \u2728"
            setOnClickListener { onStartService() }
        }
        layout.addView(btnStart)

        val btnStop = Button(this).apply {
            text = "\u6536\u8D77\u732B\u54AA"
            setOnClickListener { onStopService() }
            setPadding(0, 20, 0, 0)
        }
        layout.addView(btnStop)

        setContentView(layout)

        if (!hasOverlayPermission()) {
            requestOverlayPermission()
        }
    }

    private fun onStartService() {
        if (!hasOverlayPermission()) {
            requestOverlayPermission()
            Toast.makeText(this, "\u8BF7\u5148\u6388\u4E88\u60AC\u6D6E\u7A97\u6743\u9650", Toast.LENGTH_SHORT).show()
            return
        }

        val intent = Intent(this, PetOverlayService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
        finish()
    }

    private fun onStopService() {
        val intent = Intent(this, PetOverlayService::class.java)
        stopService(intent)
        Toast.makeText(this, "\u732B\u54AA\u5DF2\u6536\u8D77\uFF5E", Toast.LENGTH_SHORT).show()
    }

    private fun hasOverlayPermission(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
            Settings.canDrawOverlays(this)
        else true

    private fun requestOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivity(intent)
        }
    }
}
