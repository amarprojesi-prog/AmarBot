package com.amarbot

import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.provider.Settings
import android.app.Activity
import android.widget.*

class MainActivity : Activity() {
    private lateinit var prefs: SharedPreferences
    private lateinit var tvStatus: TextView
    private lateinit var tvLog: TextView
    private lateinit var btnToggle: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        prefs = getSharedPreferences("amarbot", MODE_PRIVATE)
        tvStatus = findViewById(R.id.tvStatus)
        tvLog = findViewById(R.id.tvLog)
        btnToggle = findViewById(R.id.btnToggle)

        findViewById<Button>(R.id.btnAccessibility).setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }

        btnToggle.setOnClickListener {
            val running = prefs.getBoolean("bot_running", false)
            if (!running) {
                prefs.edit().putBoolean("bot_running", true).apply()
                AmarAccessibilityService.instance?.startBot()
                updateUI(true)
            } else {
                prefs.edit().putBoolean("bot_running", false).apply()
                AmarAccessibilityService.instance?.stopBot()
                updateUI(false)
            }
        }

        AmarAccessibilityService.logCallback = { msg ->
            runOnUiThread {
                val cur = tvLog.text.toString().split("\n").takeLast(40).joinToString("\n")
                tvLog.text = cur + "\n" + msg
            }
        }
    }

    override fun onResume() {
        super.onResume()
        updateUI(prefs.getBoolean("bot_running", false))
    }

    private fun updateUI(running: Boolean) {
        if (running) {
            tvStatus.text = "Aktif - Mesajlar taranıyor"
            btnToggle.text = "Botu Durdur"
        } else {
            tvStatus.text = "Pasif"
            btnToggle.text = "Botu Baslat"
        }
    }
}