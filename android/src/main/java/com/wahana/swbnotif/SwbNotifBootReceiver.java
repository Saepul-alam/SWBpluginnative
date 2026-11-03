package com.wahana.swbnotif;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class SwbNotifBootReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (action == null) {
            return;
        }
        if (
            Intent.ACTION_BOOT_COMPLETED.equals(action) ||
            Intent.ACTION_MY_PACKAGE_REPLACED.equals(action) ||
            Intent.ACTION_TIME_CHANGED.equals(action) ||
            Intent.ACTION_TIMEZONE_CHANGED.equals(action)
        ) {
            SwbNotifScheduler.rescheduleAll(context);
        }
    }
}
