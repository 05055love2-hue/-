package com.widgety.app;

import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

/** يسمح لنا نقرأ الأغنية الشغالة، ويحدّث الويدجت أول ما تتغير — برمجة أبو نواف */
public class MediaListener extends NotificationListenerService {
    @Override
    public void onListenerConnected() {
        Widgets.refreshMusic(this);
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        Widgets.refreshMusic(this);
    }

    @Override
    public void onNotificationRemoved(StatusBarNotification sbn) {
        Widgets.refreshMusic(this);
    }
}
