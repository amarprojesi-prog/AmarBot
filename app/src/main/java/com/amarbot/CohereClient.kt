package com.amarbot

import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

object CohereClient {
    private const val API_URL = "https://api.cohere.ai/v1/chat"
    private const val API_KEY = "cohere_vfV6ZLAogPNHdWrVlFuz672AEGPXbhsYYNzO7VAC4HSL7v"
    private val histories = mutableMapOf<String, MutableList<Pair<String,String>>>()

    fun getReply(userId: String, msg: String): String {
        val hist = histories.getOrPut(userId) { mutableListOf() }
        val system = "Sen Amar uygulamasinda bir kadin yayincisin. Sana mesaj atan erkelere flortoz, sicak ve ilgi cekici sekilde Turkce yanit veriyorsun. Yanitlarin kisa (1-2 cumle), samimi ve biraz gizemli olsun. Karsindakini merak uyandır, konusmaya devam ettir. Asla mustehcen olma ama flortoz ol. Emoji kullanabilirsin."
        val chatHist = JSONArray()
        for ((r,m) in hist) { val o = JSONObject(); o.put("role",r); o.put("message",m); chatHist.put(o) }
        val body = JSONObject()
        body.put("model","command-r-plus")
        body.put("message", msg)
        body.put("preamble", system)
        if (chatHist.length() > 0) body.put("chat_history", chatHist)
        body.put("temperature", 0.8)
        val conn = URL(API_URL).openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.setRequestProperty("Authorization", "Bearer $API_KEY")
        conn.setRequestProperty("Content-Type", "application/json")
        conn.doOutput = true
        conn.connectTimeout = 12000
        conn.readTimeout = 15000
        OutputStreamWriter(conn.outputStream).use { it.write(body.toString()) }
        if (conn.responseCode != 200) throw Exception("HTTP " + conn.responseCode)
        val reply = JSONObject(conn.inputStream.bufferedReader().readText()).getString("text").trim()
        hist.add(Pair("USER", msg))
        hist.add(Pair("CHATBOT", reply))
        if (hist.size > 20) repeat(2) { hist.removeAt(0) }
        return reply
    }
}