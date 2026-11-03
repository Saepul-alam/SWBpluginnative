package com.wahana.swbnotif;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import org.json.JSONObject;

public class NotificationActionReceiver extends BroadcastReceiver {

    static final String ACTION = "com.wahana.swbnotif.ACTION";
    static final String ACTION_DISMISS = "com.wahana.swbnotif.DISMISS";

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (ACTION_DISMISS.equals(action)) {
            int id = intent.getIntExtra(SwbNotifHelper.EXTRA_ID, 0);
            SwbNotifPlugin.emitNotificationDismissed(id);
            return;
        }

        String raw = intent.getStringExtra(SwbNotifHelper.EXTRA_OPTIONS);
        String actionId = intent.getStringExtra(SwbNotifHelper.EXTRA_ACTION_ID);
        if (raw == null || actionId == null) {
            return;
        }

        JSONObject notification;
        try {
            notification = new JSONObject(raw);
        } catch (Exception e) {
            return;
        }

        JSONObject event = new JSONObject();
        try {
            long receivedAt = System.currentTimeMillis();
            if (!notification.has("receivedAt")) {
                notification.put("receivedAt", receivedAt);
            }
            event.put("actionId", actionId);
            event.put("notification", notification);
        } catch (Exception ignored) {}

        new SwbNotifStore(context).setLastAction(event);
        SwbNotifPlugin.emitNotificationActionPerformed(event);

        Intent launch = context.getPackageManager().getLaunchIntentForPackage(context.getPackageName());
        if (launch != null) {
            launch.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            launch.putExtra(SwbNotifHelper.EXTRA_ACTION_ID, actionId);
            launch.putExtra(SwbNotifHelper.EXTRA_ID, notification.optInt("id"));
            try {
                context.startActivity(launch);
            } catch (Exception ignored) {}
        }
    }
}
