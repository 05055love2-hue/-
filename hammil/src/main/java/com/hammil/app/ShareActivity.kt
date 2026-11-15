package com.hammil.app

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Toast

/** تستقبل الرابط من زر «مشاركة» وتبدأ التحميل بدون ما تفتح شي */
class ShareActivity : Activity() {
    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        val text = (intent?.getStringExtra(Intent.EXTRA_TEXT) ?: "") + " " + (intent?.getStringExtra(Intent.EXTRA_SUBJECT) ?: "")
        val url = DlService.findUrl(text)
        if (url == null) {
            Toast.makeText(this, "ما لقيت رابط في اللي أرسلته", Toast.LENGTH_LONG).show()
        } else {
            val mode = Jobs.prefs().getString("shareMode", "video") ?: "video"
            DlService.start(this, url, mode)
            Toast.makeText(this, if (mode == "audio") "🎵 بدأ تحميل الصوت…" else "⬇️ بدأ التحميل…", Toast.LENGTH_SHORT).show()
        }
        finish()
    }
}
