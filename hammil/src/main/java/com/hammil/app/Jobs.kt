package com.hammil.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** قائمة التحميلات: تنحفظ في الجهاز وتنعرض في الواجهة */
class Job(
    val id: String,
    val url: String,
    val mode: String,           // video | audio
    var title: String = "",
    var state: String = "queued", // queued | running | done | error | canceled
    var progress: Float = 0f,
    var eta: Long = 0,
    var uri: String = "",
    var mime: String = "",
    var size: Long = 0,
    var err: String = "",
    val t: Long = System.currentTimeMillis(),
) {
    fun json(): JSONObject = JSONObject()
        .put("id", id).put("url", url).put("mode", mode).put("title", title)
        .put("state", state).put("progress", progress.toDouble()).put("eta", eta)
        .put("uri", uri).put("mime", mime).put("size", size).put("err", err).put("t", t)

    companion object {
        fun from(o: JSONObject) = Job(
            o.optString("id"), o.optString("url"), o.optString("mode", "video"), o.optString("title"),
            o.optString("state"), o.optDouble("progress", 0.0).toFloat(), o.optLong("eta"),
            o.optString("uri"), o.optString("mime"), o.optLong("size"), o.optString("err"), o.optLong("t"),
        )
    }
}

object Jobs {
    val list = mutableListOf<Job>()
    var listener: (() -> Unit)? = null
    private var ctx: Context? = null
    private var lastSave = 0L

    @Synchronized
    fun load(c: Context) {
        ctx = c.applicationContext
        list.clear()
        try {
            val a = JSONArray(prefs().getString("jobs", "[]"))
            for (i in 0 until a.length()) {
                val j = Job.from(a.getJSONObject(i))
                if (j.state == "running" || j.state == "queued") { j.state = "error"; j.err = "انقطع التحميل، جرّب مرة ثانية" }
                list.add(j)
            }
        } catch (_: Exception) {}
    }

    fun prefs() = ctx!!.getSharedPreferences("hammil", Context.MODE_PRIVATE)

    @Synchronized
    fun add(j: Job) { list.add(0, j); while (list.size > 60) list.removeAt(list.size - 1); changed(true) }

    @Synchronized
    fun get(id: String) = list.firstOrNull { it.id == id }

    @Synchronized
    fun remove(id: String) { list.removeAll { it.id == id }; changed(true) }

    @Synchronized
    fun clearDone() { list.removeAll { it.state != "running" && it.state != "queued" }; changed(true) }

    @Synchronized
    fun json(): String = JSONArray().also { a -> list.forEach { a.put(it.json()) } }.toString()

    fun changed(force: Boolean = false) {
        val now = System.currentTimeMillis()
        if (force || now - lastSave > 3000) {
            lastSave = now
            try { prefs().edit().putString("jobs", json()).apply() } catch (_: Exception) {}
        }
        listener?.invoke()
    }
}
