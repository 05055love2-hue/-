package com.hammil.app

import android.app.DownloadManager
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.IBinder
import android.os.PowerManager
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import android.webkit.URLUtil
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import java.io.File
import java.util.concurrent.Executors

class DlService : Service() {

    companion object {
        const val CH_RUN = "run"
        const val CH_DONE = "done"
        private const val NID = 7
        @Volatile var ready = false
        @Volatile var engine = ""
        private val pool = Executors.newSingleThreadExecutor()
        private var active = 0

        private val URL_RE = Regex("""https?://[^\s<>"'،]+""")
        fun findUrl(s: String): String? = URL_RE.find(s)?.value?.trimEnd('.', ',', ')', ']', '!', '؟')

        fun start(c: Context, url: String, mode: String) {
            val i = Intent(c, DlService::class.java).putExtra("url", url).putExtra("mode", mode)
            if (Build.VERSION.SDK_INT >= 26) c.startForegroundService(i) else c.startService(i)
        }

        fun update(c: Context) {
            c.startService(Intent(c, DlService::class.java).putExtra("update", true))
        }

        /** يجهّز محرك التحميل (أول مرة ياخذ ثواني) */
        @Synchronized
        fun init(c: Context) {
            if (ready) return
            YoutubeDL.getInstance().init(c.applicationContext)
            try { FFmpeg.getInstance().init(c.applicationContext) } catch (_: Exception) {}
            ready = true
            engine = try { YoutubeDL.getInstance().version(c.applicationContext) ?: "" } catch (_: Exception) { "" }
        }

        fun updateEngine(c: Context): String {
            init(c)
            return try {
                val st = YoutubeDL.getInstance().updateYoutubeDL(c.applicationContext)
                engine = YoutubeDL.getInstance().version(c.applicationContext) ?: engine
                Jobs.prefs().edit().putLong("upd", System.currentTimeMillis()).apply()
                if (st.toString().contains("ALREADY", true)) "المحرك محدّث أصلاً ✅" else "تم تحديث المحرك ✅"
            } catch (e: Exception) { "ما قدرت أحدّث: " + (e.message ?: "").take(80) }
        }
    }

    private var wake: PowerManager.WakeLock? = null

    override fun onBind(i: Intent?): IBinder? = null

    override fun onStartCommand(i: Intent?, flags: Int, startId: Int): Int {
        goFront("جاري التجهيز…", 0, true)
        if (i?.getBooleanExtra("update", false) == true) {
            synchronized(Companion) { active++ }
            pool.execute {
                val msg = updateEngine(this)
                MainActivity.toast(msg)
                finishOne()
            }
            return START_NOT_STICKY
        }
        val url = i?.getStringExtra("url") ?: return START_NOT_STICKY.also { if (active == 0) stopSelf() }
        val mode = i.getStringExtra("mode") ?: "video"
        val job = Job(System.currentTimeMillis().toString(36) + (0..99).random(), url, mode, title = niceHost(url))
        Jobs.add(job)
        synchronized(Companion) { active++ }
        pool.execute { run(job); finishOne() }
        return START_NOT_STICKY
    }

    private fun finishOne() {
        synchronized(Companion) {
            active--
            if (active <= 0) {
                active = 0
                if (Build.VERSION.SDK_INT >= 24) stopForeground(STOP_FOREGROUND_REMOVE) else @Suppress("DEPRECATION") stopForeground(true)
                wake?.let { if (it.isHeld) it.release() }
                stopSelf()
            }
        }
    }

    private fun goFront(text: String, pct: Int, indet: Boolean) {
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val b = (if (Build.VERSION.SDK_INT >= 26) Notification.Builder(this, CH_RUN) else @Suppress("DEPRECATION") Notification.Builder(this))
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("حمّل")
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(open)
            .setProgress(100, pct, indet)
        val n = b.build()
        if (Build.VERSION.SDK_INT >= 29) startForeground(NID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        else startForeground(NID, n)
        if (wake == null) {
            wake = (getSystemService(POWER_SERVICE) as PowerManager).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "hammil:dl")
        }
        wake?.let { if (!it.isHeld) it.acquire(30 * 60 * 1000L) }
    }

    private fun niceHost(u: String): String = try { Uri.parse(u).host?.removePrefix("www.")?.removePrefix("m.") ?: u } catch (_: Exception) { u }

    private fun run(job: Job) {
        if (job.state == "canceled") return
        job.state = "running"; Jobs.changed(true)
        try {
            goFront("جاري التجهيز…", 0, true)
            init(this)
            // نحدّث المحرك مرة في اليوم عشان المواقع تتغير باستمرار
            val last = Jobs.prefs().getLong("upd", 0)
            if (System.currentTimeMillis() - last > 24 * 3600 * 1000L) {
                goFront("تحديث المحرك…", 0, true)
                try { YoutubeDL.getInstance().updateYoutubeDL(applicationContext) } catch (_: Exception) {}
                Jobs.prefs().edit().putLong("upd", System.currentTimeMillis()).apply()
            }
            val dir = File(cacheDir, "dl/" + job.id).apply { deleteRecursively(); mkdirs() }
            val q = Jobs.prefs().getString("quality", "720") ?: "720"
            val r = YoutubeDLRequest(job.url)
            r.addOption("--no-playlist")
            r.addOption("--no-mtime")
            r.addOption("-o", dir.absolutePath + "/%(title).80B.%(ext)s")
            if (job.mode == "audio") {
                r.addOption("-f", "ba[ext=m4a]/ba/b")
                r.addOption("-x")
                r.addOption("--audio-format", "mp3")
                r.addOption("--audio-quality", "0")
            } else {
                val h = if (q == "best") "" else "[height<=$q]"
                r.addOption("-f", "bv*$h[ext=mp4][vcodec^=avc]+ba[ext=m4a]/b$h[ext=mp4]/bv*$h+ba/b$h/bv*+ba/b")
                r.addOption("--merge-output-format", "mp4")
            }
            var lastUi = 0L
            YoutubeDL.getInstance().execute(r, job.id) { p, eta, line ->
                if (job.title == niceHost(job.url)) {
                    val m = Regex("""Destination: .*/([^/]+)$""").find(line)
                    if (m != null) job.title = m.groupValues[1].substringBeforeLast('.')
                }
                val now = System.currentTimeMillis()
                if (now - lastUi > 400) {
                    lastUi = now
                    job.progress = p.coerceIn(0f, 100f); job.eta = eta
                    goFront(job.title.take(40) + " • " + p.toInt() + "%", p.toInt(), p <= 0f)
                    Jobs.changed()
                }
            }
            val f = dir.listFiles()?.filter { it.isFile && !it.name.endsWith(".part") && !it.name.endsWith(".ytdl") }?.maxByOrNull { it.length() }
                ?: throw Exception("ما نزل أي ملف")
            job.title = f.nameWithoutExtension
            goFront("حفظ في الجهاز…", 100, true)
            save(job, f)
            dir.deleteRecursively()
            job.state = "done"; job.progress = 100f
            Jobs.changed(true)
            doneNote(job)
        } catch (e: Throwable) {
            if (job.state == "canceled") { Jobs.changed(true); return }
            val msg = (e.message ?: e.toString())
            // رابط ملف عادي (مثل pdf أو apk): نحمّله بمدير التحميل حق الجوال
            if (msg.contains("Unsupported URL", true) || msg.contains("No video formats", true)) {
                if (plainDownload(job)) return
            }
            job.state = "error"; job.err = friendly(msg)
            job.raw = "المحرك: " + engine + "\n" + e.javaClass.simpleName + ": " + msg.takeLast(1500)
            Jobs.changed(true)
        }
    }

    private fun friendly(m: String): String = when {
        m.contains("Unsupported URL", true) -> "هذا الرابط ما فيه مقطع أقدر أحمّله"
        m.contains("Private", true) || m.contains("login", true) || m.contains("sign in", true) -> "المقطع خاص أو يحتاج تسجيل دخول"
        m.contains("Unable to resolve host", true) || m.contains("network", true) || m.contains("timed out", true) -> "تأكد من الإنترنت وجرّب مرة ثانية"
        m.contains("HTTP Error 404", true) -> "المقطع محذوف أو الرابط غلط"
        m.contains("HTTP Error 403", true) -> "الموقع رفض التحميل، اضغط «حدّث المحرك» وجرّب"
        else -> "ما قدرت أحمّله. اضغط «حدّث المحرك» وجرّب مرة ثانية"
    }

    private fun plainDownload(job: Job): Boolean = try {
        val u = Uri.parse(job.url)
        val name = URLUtil.guessFileName(job.url, null, null)
        val req = DownloadManager.Request(u)
            .setTitle(name)
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, name)
        (getSystemService(DOWNLOAD_SERVICE) as DownloadManager).enqueue(req)
        job.title = name; job.state = "done"; job.progress = 100f; job.err = "انحفظ في مجلد التنزيلات"
        job.mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(name.substringAfterLast('.', "").lowercase()) ?: ""
        Jobs.changed(true)
        true
    } catch (_: Exception) { false }

    /** ينقل الملف لمعرض الجوال: الفيديو في الصور (DCIM/حمّل) والصوت في Music/حمّل */
    private fun save(job: Job, f: File) {
        val ext = f.extension.lowercase()
        val audio = job.mode == "audio" || ext in listOf("mp3", "m4a", "opus", "ogg", "wav")
        val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: if (audio) "audio/mpeg" else "video/mp4"
        job.mime = mime; job.size = f.length()
        if (Build.VERSION.SDK_INT >= 29) {
            val col = if (audio) MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                      else MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            val cv = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, f.name)
                put(MediaStore.MediaColumns.MIME_TYPE, mime)
                put(MediaStore.MediaColumns.RELATIVE_PATH, (if (audio) Environment.DIRECTORY_MUSIC else Environment.DIRECTORY_DCIM) + "/حمّل")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
                put(MediaStore.MediaColumns.DATE_TAKEN, System.currentTimeMillis())
            }
            val uri = contentResolver.insert(col, cv) ?: throw Exception("ما قدرت أحفظ الملف")
            contentResolver.openOutputStream(uri)!!.use { o -> f.inputStream().use { it.copyTo(o) } }
            cv.clear(); cv.put(MediaStore.MediaColumns.IS_PENDING, 0)
            contentResolver.update(uri, cv, null, null)
            job.uri = uri.toString()
        } else {
            @Suppress("DEPRECATION")
            val d = File(Environment.getExternalStoragePublicDirectory(if (audio) Environment.DIRECTORY_MUSIC else Environment.DIRECTORY_DCIM), "حمّل").apply { mkdirs() }
            val out = File(d, f.name)
            f.copyTo(out, true)
            MediaScannerConnection.scanFile(this, arrayOf(out.absolutePath), arrayOf(mime), null)
            job.uri = Uri.fromFile(out).toString()
        }
    }

    private fun doneNote(job: Job) {
        val view = Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse(job.uri), job.mime).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        val pi = PendingIntent.getActivity(this, job.id.hashCode(), view, PendingIntent.FLAG_IMMUTABLE)
        val b = (if (Build.VERSION.SDK_INT >= 26) Notification.Builder(this, CH_DONE) else @Suppress("DEPRECATION") Notification.Builder(this))
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle("✅ اكتمل التحميل")
            .setContentText(job.title)
            .setAutoCancel(true)
            .setContentIntent(pi)
        try { (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).notify(job.id.hashCode(), b.build()) } catch (_: Exception) {}
    }
}
