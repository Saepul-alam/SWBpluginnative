package com.wahana.swbnotif;

import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;
import java.util.Map;
import org.json.JSONObject;

public class SwbNotifMessagingService extends FirebaseMessagingService {

    @Override
    public void onNewToken(String token) {
        SwbNotifStore store = new SwbNotifStore(this);
        store.setPushToken(token);
        SwbNotifPlugin.emitPushToken(token);
    }

    @Override
    public void onMessageReceived(RemoteMessage message) {
        RemoteMessage.Notification notification = message.getNotification();
        Map<String, String> data = message.getData();

        JSONObject options = new JSONObject();
        int id = SwbNotifHelper.generateId();
        try {
            options.put("id", id);
            options.put("title", firstNonNull(data.get("title"), notification != null ? notification.getTitle() : null, ""));
            options.put("body", firstNonNull(data.get("body"), notification != null ? notification.getBody() : null, ""));
            String channelId = data.get("channelId");
            if (channelId != null) {
                options.put("channelId", channelId);
            }
            String sound = data.get("sound");
            if (sound != null) {
                options.put("sound", sound);
            }
            String icon = data.get("smallIcon");
            if (icon != null) {
                options.put("smallIcon", icon);
            }

            JSONObject extra = new JSONObject();
            for (Map.Entry<String, String> entry : data.entrySet()) {
                extra.put(entry.getKey(), entry.getValue());
            }
            options.put("extra", extra);
        } catch (Exception ignored) {}

        try {
            SwbNotifHelper.show(this, options);
        } catch (Exception e) {
            return;
        }

        JSONObject delivered;
        try {
            delivered = new JSONObject(options.toString());
            delivered.put("receivedAt", System.currentTimeMillis());
        } catch (Exception e) {
            delivered = options;
        }

        new SwbNotifStore(this).addDelivered(delivered);
        SwbNotifPlugin.emitNotificationReceived(delivered);
    }

    private static String firstNonNull(String first, String second, String fallback) {
        if (first != null) {
            return first;
        }
        if (second != null) {
            return second;
        }
        return fallback;
    }
}
