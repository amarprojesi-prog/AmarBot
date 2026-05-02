package com.amarbot

import android.accessibilityservice.AccessibilityService
import android.content.SharedPreferences
import android.graphics.Rect
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class AmarAccessibilityService : AccessibilityService() {
    companion object {
        var instance: AmarAccessibilityService? = null
        var logCallback: ((String) -> Unit)? = null
    }

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var prefs: SharedPreferences
    private var isRunning = false
    private var lastMsg = ""
    private var state = "LIST"
    private var currentUser = ""

    private val loop = object : Runnable {
        override fun run() {
            if (isRunning) { tick(); handler.postDelayed(this, 1500) }
        }
    }

    override fun onServiceConnected() {
        instance = this
        prefs = getSharedPreferences("amarbot", MODE_PRIVATE)
        log("Servis baglandi")
        if (prefs.getBoolean("bot_running", false)) startBot()
    }

    override fun onDestroy() { instance = null; stopBot() }

    fun startBot() { isRunning = true; state = "LIST"; log("Bot basladi"); handler.postDelayed(loop, 2000) }
    fun stopBot() { isRunning = false; handler.removeCallbacks(loop); log("Bot durdu") }

    private fun tick() {
        val root = rootInActiveWindow ?: return
        val pkg = root.packageName?.toString() ?: ""
        if (!pkg.contains("amar", true) && !pkg.contains("cqgame", true)) { log("Amar kapali"); return }
        when (state) {
            "LIST" -> scanList(root)
            "CHAT" -> readAndReply(root)
            "BACK" -> goBack(root)
        }
    }

    private fun scanList(root: AccessibilityNodeInfo) {
        val tab = findText(root, "Okunmamis") ?: findText(root, "Okunmamış")
        tab?.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        val w = resources.displayMetrics.widthPixels
        val h = resources.displayMetrics.heightPixels
        val nodes = mutableListOf<AccessibilityNodeInfo>()
        collect(root, nodes)
        val chat = nodes.firstOrNull { n ->
            val b = Rect(); n.getBoundsInScreen(b)
            n.isClickable && b.height() > 80 && b.width() > w/2 && b.top > h*0.15
        }
        if (chat != null) {
            chat.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            state = "CHAT"; log("Sohbete girildi")
        } else log("Okunmamis mesaj yok")
    }

    private fun readAndReply(root: AccessibilityNodeInfo) {
        val incoming = findLastIncoming(root)
        if (incoming.isNullOrEmpty()) { log("Mesaj yok, geri donuluyor"); goBack(root); return }
        if (incoming == lastMsg) { log("Yeni mesaj yok, geri donuluyor"); goBack(root); return }
        lastMsg = incoming
        currentUser = findTitle(root) ?: "user_${System.currentTimeMillis()}"
        log("Mesaj: $incoming")
        Thread {
            try {
                val reply = CohereClient.getReply(currentUser, incoming)
                log("Yanit: $reply")
                handler.post { sendMsg(root, reply) }
            } catch (e: Exception) {
                log("Hata: ${e.message}")
                handler.post { goBack(root) }
            }
        }.start()
        state = "BACK"
    }

    private fun sendMsg(root: AccessibilityNodeInfo, msg: String) {
        val nodes = mutableListOf<AccessibilityNodeInfo>()
        collect(rootInActiveWindow ?: return, nodes)
        val input = nodes.firstOrNull { it.isEditable || it.className?.contains("EditText") == true }
        if (input == null) { log("Giris kutusu bulunamadi"); goBack(root); return }
        input.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        Thread.sleep(300)
        val b = Bundle()
        b.putString(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, msg)
        input.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, b)
        Thread.sleep(400)
        val w = resources.displayMetrics.widthPixels
        val h = resources.displayMetrics.heightPixels
        val nodes2 = mutableListOf<AccessibilityNodeInfo>()
        collect(rootInActiveWindow ?: return, nodes2)
        val send = nodes2.firstOrNull { n ->
            val br = Rect(); n.getBoundsInScreen(br)
            n.isClickable && br.left > w*0.7 && br.top > h*0.75
        }
        send?.performAction(AccessibilityNodeInfo.ACTION_CLICK) ?: input.performAction(AccessibilityNodeInfo.ACTION_IME_ENTER)
        log("Gonderildi!")
        handler.postDelayed({ state = "BACK" }, 600)
    }

    private fun goBack(root: AccessibilityNodeInfo) {
        performGlobalAction(GLOBAL_ACTION_BACK)
        lastMsg = ""; currentUser = ""; state = "LIST"; log("Geri donuldu")
    }

    private fun findLastIncoming(root: AccessibilityNodeInfo): String? {
        val sw = resources.displayMetrics.widthPixels
        val nodes = mutableListOf<AccessibilityNodeInfo>()
        collect(root, nodes)
        return nodes.lastOrNull { n ->
            if (n.text.isNullOrEmpty()) return@lastOrNull false
            val b = Rect(); n.getBoundsInScreen(b)
            b.left < sw/2 && b.width() > 40 && n.className?.contains("Text") == true
        }?.text?.toString()
    }

    private fun findTitle(root: AccessibilityNodeInfo): String? {
        val sh = resources.displayMetrics.heightPixels
        val nodes = mutableListOf<AccessibilityNodeInfo>()
        collect(root, nodes)
        return nodes.firstOrNull { n ->
            if (n.text.isNullOrEmpty()) return@firstOrNull false
            val b = Rect(); n.getBoundsInScreen(b)
            b.top < sh*0.15 && n.className?.contains("Text") == true
        }?.text?.toString()
    }

    private fun findText(root: AccessibilityNodeInfo, text: String): AccessibilityNodeInfo? {
        if (root.text?.toString()?.contains(text, true) == true) return root
        for (i in 0 until root.childCount) { findText(root.getChild(i) ?: continue, text)?.let { return it } }
        return null
    }

    private fun collect(node: AccessibilityNodeInfo?, list: MutableList<AccessibilityNodeInfo>) {
        if (node == null) return; list.add(node)
        for (i in 0 until node.childCount) collect(node.getChild(i), list)
    }

    private fun log(msg: String) {
        val t = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())
        logCallback?.invoke("[$t] $msg")
        android.util.Log.d("AmarBot", msg)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() { log("Servis kesildi") }
}