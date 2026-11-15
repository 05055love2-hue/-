package com.hammil.app

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.ClipboardManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import com.yausername.youtubedl_android.YoutubeDL
import org.json.JSONObject

class MainActivity : Activity() {

    companion object {
        private val main = Handler(Looper.getMainLooper())
        private var self: MainActivity? = null
        fun toast(s: String) = main.post {
            self?.let { Toast.makeText(it, s, Toast.LENGTH_LONG).show(); it.js("window.onMsg&&onMsg(" + JSONObject.quote(s) + ")") }
        }
    }

    private lateinit var web: WebView
    private var pending = false

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        self = this
        web = WebView(this)
        web.setBackgroundColor(0xff0b0f19.toInt())
        setContentView(web)
        web.settings.javaScriptEnabled = true
        web.settings.domStorageEnabled = true
        web.addJavascriptInterface(Bridge(), "Dl")
        web.webChromeClient = WebChromeClient()
        web.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(v: WebView, u: String): Boolean {
                if (u.startsWith("file:")) return false
                try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(u))) } catch (_: Exception) {}
                return true
            }
        }
        web.loadUrl("file:///android_asset/index.html")
        Jobs.listener = {
            if (!pending) { pending = true; main.postDelayed({ pending = false; js("window.onJobs&&onJobs(" + Jobs.json() + ")") }, 250) }
        }
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        if (Build.VERSION.SDK_INT < 29 && checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED)
            requestPermissions(arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE), 2)
        // نجهّز المحرك من الحين عشان أول تحميل يكون أسرع
        Thread { try { DlService.init(this); js("window.onEngine&&onEngine(" + JSONObject.quote(DlService.engine) + ")") } catch (_: Throwable) {} }.start()
    }

    override fun onDestroy() { if (self == this) { self = null; Jobs.listener = null }; super.onDestroy() }

    fun js(s: String) = main.post { try { web.evaluateJavascript(s, null) } catch (_: Exception) {} }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() { js("window.onBack?onBack():Dl.exit()") }

    inner class Bridge {
        @JavascriptInterface fun jobs(): String = Jobs.json()
        @JavascriptInterface fun engine(): String = DlService.engine
        @JavascriptInterface fun exit() { main.post { finish() } }

        @JavascriptInterface fun start(text: String, mode: String): String {
            val u = DlService.findUrl(text) ?: return "no"
            DlService.start(this@MainActivity, u, mode)
            return "ok"
        }

        @JavascriptInterface fun paste(): String = try {
            val cm = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
            cm.primaryClip?.getItemAt(0)?.coerceToText(this@MainActivity)?.toString() ?: ""
        } catch (_: Exception) { "" }

        @JavascriptInterface fun getPref(k: String, d: String): String = Jobs.prefs().getString(k, d) ?: d
        @JavascriptInterface fun setPref(k: String, v: String) { Jobs.prefs().edit().putString(k, v).apply() }

        @JavascriptInterface fun open(id: String) {
            val j = Jobs.get(id) ?: return
            main.post {
                try {
                    if (j.uri.isEmpty()) startActivity(Intent(android.app.DownloadManager.ACTION_VIEW_DOWNLOADS))
                    else startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse(j.uri), j.mime).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
                } catch (_: Exception) { Toast.makeText(this@MainActivity, "ما فيه تطبيق يفتح هذا الملف", Toast.LENGTH_SHORT).show() }
            }
        }

        @JavascriptInterface fun share(id: String) {
            val j = Jobs.get(id) ?: return
            if (j.uri.isEmpty()) return
            main.post {
                try {
                    val i = Intent(Intent.ACTION_SEND).setType(j.mime.ifEmpty { "*/*" })
                        .putExtra(Intent.EXTRA_STREAM, Uri.parse(j.uri)).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    startActivity(Intent.createChooser(i, "شارك"))
                } catch (_: Exception) {}
            }
        }

        @JavascriptInterface fun cancel(id: String) {
            val j = Jobs.get(id) ?: return
            j.state = "canceled"; j.err = "ألغيته"
            try { YoutubeDL.getInstance().destroyProcessById(id) } catch (_: Exception) {}
            Jobs.changed(true)
        }

        @JavascriptInterface fun retry(id: String) {
            val j = Jobs.get(id) ?: return
            Jobs.remove(id)
            DlService.start(this@MainActivity, j.url, j.mode)
        }

        @JavascriptInterface fun copy(s: String) {
            main.post {
                (getSystemService(CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(android.content.ClipData.newPlainText("err", s))
                Toast.makeText(this@MainActivity, "تم النسخ", Toast.LENGTH_SHORT).show()
            }
        }
        @JavascriptInterface fun remove(id: String) = Jobs.remove(id)
        @JavascriptInterface fun clearDone() = Jobs.clearDone()
        @JavascriptInterface fun update() = DlService.update(this@MainActivity)
    }
}
