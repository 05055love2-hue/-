package com.hammil.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        Jobs.load(this)
        if (Build.VERSION.SDK_INT >= 26) {
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(NotificationChannel(DlService.CH_RUN, "التحميل الحالي", NotificationManager.IMPORTANCE_LOW))
            nm.createNotificationChannel(NotificationChannel(DlService.CH_DONE, "اكتمل التحميل", NotificationManager.IMPORTANCE_DEFAULT))
        }
    }
}
