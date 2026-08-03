package com.zero.pet

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.Toast

class MainActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val startButton = Button(this).apply {
            text = "唤醒零点"
            textSize = 20f
            setOnClickListener {
                if (Settings.canDrawOverlays(this@MainActivity)) {
                    startForegroundService(Intent(this@MainActivity, PetOverlayService::class.java))
                    Toast.makeText(this@MainActivity, "零点住进来了", Toast.LENGTH_SHORT).show()
                    finish()
                } else {
                    val intent = Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:$packageName")
                    )
                    startActivity(intent)
                    Toast.makeText(this@MainActivity, "先允许悬浮窗，零点才能住进来", Toast.LENGTH_LONG).show()
                }
            }
        }

        setContentView(startButton)
    }
}
