package com.wahana.swbnotif;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import java.util.List;
import org.json.JSONObject;

final class SwbNotifScheduler {

    private SwbNotifScheduler() {}

    static void schedule(Context context, JSONObject options) {
        AlarmManager manager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (manager == null) {
            return;
        }
        int id = options.optInt("id");
        long at = SwbNotifHelper.parseTime(options);
        long now = System.currentTimeMillis();
        if (at < now) {
            at = now + 1000L;
        }

        Intent intent = new Intent(context, SwbNotifReceiver.class);
        intent.setAction(SwbNotifReceiver.ACTION);
        intent.putExtra(SwbNotifHelper.EXTRA_OPTIONS, options.toString());
        PendingIntent pendingIntent = PendingIntent.getBroadcast(context, id, intent, SwbNotifHelper.pendingIntentFlags());

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !manager.canScheduleExactAlarms()) {
                manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pendingIntent);
            } else {
                manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pendingIntent);
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            manager.setExact(AlarmManager.RTC_WAKEUP, at, pendingIntent);
        } else {
            manager.set(AlarmManager.RTC_WAKEUP, at, pendingIntent);
        }
    }

    static void cancel(Context context, int id) {
        AlarmManager manager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (manager == null) {
            return;
        }
        Intent intent = new Intent(context, SwbNotifReceiver.class);
        intent.setAction(SwbNotifReceiver.ACTION);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(context, id, intent, SwbNotifHelper.pendingIntentFlags());
        manager.cancel(pendingIntent);
        pendingIntent.cancel();
    }

    static void cancelAll(Context context) {
        SwbNotifStore store = new SwbNotifStore(context);
        for (JSONObject item : store.getScheduled()) {
            cancel(context, item.optInt("id"));
        }
    }

    /**
     * Re-registers every stored notification. Called after a reboot, an app
     * update or a timezone change because the system loses all alarms.
     */
    static void rescheduleAll(Context context) {
        SwbNotifStore store = new SwbNotifStore(context);
        List<JSONObject> scheduled = store.getScheduled();
        long now = System.currentTimeMillis();
        for (JSONObject item : scheduled) {
            long at = SwbNotifHelper.parseTime(item);
            String every = item.optString("every", null);
            if (at <= now && every != null && !every.isEmpty()) {
                long interval = SwbNotifHelper.everyMillis(every);
                if (interval > 0) {
                    long next = at;
                    while (next <= now) {
                        next += interval;
                    }
                    try {
                        item.put("scheduleAt", next);
                    } catch (Exception ignored) {}
                    store.upsertScheduled(item);
                }
            } else if (at <= now) {
                try {
                    item.put("scheduleAt", now + 1000L);
                } catch (Exception ignored) {}
                store.upsertScheduled(item);
            }
            schedule(context, item);
        }
    }
}
