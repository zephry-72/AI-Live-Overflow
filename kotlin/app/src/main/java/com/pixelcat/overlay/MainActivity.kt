package com.pixelcat.overlay

import android.app.Activity
import android.content.Intent
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
            setBackgroundColor(0xAA_2A2A3A.toInt())
        }

        val title = TextView(this).apply {
            text = "🐱 Pixel Cat"
            textSize = 24f
            setTextColor(0xFF_A8B8C8.toInt())
            setPadding(0, 0, 0, 20)
        }
        layout.addView(title)

        val desc = TextView(this).apply {
            text = "让像素猫咪浮在你的屏幕上～"
            textSize = 14f
            setTextColor(0xCC_8A9AAA.toInt())
            setPadding(0, 0, 0, 40)
        }
        layout.addView(desc)

        val btnStart = Button(this).apply {
            text = "召唤猫咪 ✨"
            setOnClickListener { onStartService() }
        }
        layout.addView(btnStart)

        val btnStop = Button(this).apply {
            text = "收起猫咪"
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
            Toast.makeText(this, "请先授予悬浮窗权限", Toast.LENGTH_SHORT).show()
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
        Toast.makeText(this, "猫咪已收起～", Toast.LENGTH_SHORT).show()
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
