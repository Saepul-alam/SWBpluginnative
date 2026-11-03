package com.wahana.swbnotif;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import org.json.JSONObject;

public class SwbNotifReceiver extends BroadcastReceiver {

    static final String ACTION = "com.wahana.swbnotif.DELIVER";

    @Override
    public void onReceive(Context context, Intent intent) {
        String raw = intent.getStringExtra(SwbNotifHelper.EXTRA_OPTIONS);
        if (raw == null) {
            return;
        }

        SwbNotifStore store = new SwbNotifStore(context);
        JSONObject options;
        try {
            options = new JSONObject(raw);
        } catch (Exception e) {
            return;
        }

        int id = options.optInt("id");
        if (store.findScheduled(id) == null) {
            return;
        }

        try {
            SwbNotifHelper.show(context, options);
        } catch (Exception e) {
            return;
        }

        JSONObject delivered = copy(options);
        try {
            delivered.put("receivedAt", System.currentTimeMillis());
        } catch (Exception ignored) {}
        store.addDelivered(delivered);
        SwbNotifPlugin.emitNotificationReceived(delivered);

        String every = options.optString("every", null);
        if (every == null || every.isEmpty()) {
            store.removeScheduled(id);
            return;
        }

        int remaining = options.has("count") ? options.optInt("count") : -1;
        if (remaining == 0) {
            store.removeScheduled(id);
            return;
        }

        long interval = SwbNotifHelper.everyMillis(every);
        if (interval <= 0) {
            store.removeScheduled(id);
            return;
        }

        try {
            if (remaining > 0) {
                options.put("count", remaining - 1);
            }
            long next = System.currentTimeMillis() + interval;
            options.put("scheduleAt", next);
        } catch (Exception ignored) {}

        store.upsertScheduled(options);
        SwbNotifScheduler.schedule(context, options);
    }

    private static JSONObject copy(JSONObject source) {
        try {
            return new JSONObject(source.toString());
        } catch (Exception e) {
            return new JSONObject();
        }
    }
}
