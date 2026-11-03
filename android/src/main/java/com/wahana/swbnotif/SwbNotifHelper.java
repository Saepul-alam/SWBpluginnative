package com.wahana.swbnotif;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.media.AudioAttributes;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

final class SwbNotifHelper {

    static final String DEFAULT_CHANNEL_ID = "swbnotif_default";
    static final String EXTRA_OPTIONS = "swbnotif.options";
    static final String EXTRA_ACTION_ID = "swbnotif.action";
    static final String EXTRA_ID = "swbnotif.id";
    static final String EXTRA_TAP = "swbnotif.tap";

    private SwbNotifHelper() {}

    static int generateId() {
        return (int) (System.currentTimeMillis() % Integer.MAX_VALUE);
    }

    static void validateOptions(JSONObject options) throws IllegalArgumentException {
        if (options == null) {
            throw new IllegalArgumentException("Notification options cannot be null");
        }
        String title = options.optString("title", null);
        if (title == null || title.trim().isEmpty()) {
            throw new IllegalArgumentException("title is required and cannot be empty");
        }
        if (title.length() > SwbNotifLimits.MAX_TITLE_LENGTH) {
            throw new IllegalArgumentException("title exceeds maximum length of " + SwbNotifLimits.MAX_TITLE_LENGTH + " characters");
        }

        String body = options.optString("body", null);
        if (body != null && body.length() > SwbNotifLimits.MAX_BODY_LENGTH) {
            throw new IllegalArgumentException("body exceeds maximum length of " + SwbNotifLimits.MAX_BODY_LENGTH + " characters");
        }

        if (options.has("extra") && !options.isNull("extra")) {
            JSONObject extra = options.optJSONObject("extra");
            if (extra != null) {
                int bytes = extra.toString().getBytes(StandardCharsets.UTF_8).length;
                if (bytes > SwbNotifLimits.MAX_EXTRA_BYTES) {
                    throw new IllegalArgumentException(
                        "extra payload exceeds maximum size of " + SwbNotifLimits.MAX_EXTRA_BYTES + " bytes"
                    );
                }
            }
        }
    }

    static long parseTime(JSONObject options) {
        Object value = options.opt("scheduleAt");
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        if (value instanceof String) {
            String raw = (String) value;
            if (raw == null || raw.trim().isEmpty()) {
                return System.currentTimeMillis();
            }
            try {
                return Long.parseLong(raw);
            } catch (NumberFormatException ignored) {}
            String[] patterns = {
                "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
                "yyyy-MM-dd'T'HH:mm:ssXXX",
                "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
                "yyyy-MM-dd'T'HH:mm:ss'Z'",
                "yyyy-MM-dd'T'HH:mm:ss.SSS",
                "yyyy-MM-dd'T'HH:mm:ss"
            };
            for (String pattern : patterns) {
                try {
                    SimpleDateFormat format = new SimpleDateFormat(pattern, Locale.US);
                    if (pattern.endsWith("'Z'")) {
                        format.setTimeZone(TimeZone.getTimeZone("UTC"));
                    }
                    Date date = format.parse(raw);
                    if (date != null) {
                        return date.getTime();
                    }
                } catch (ParseException ignored) {}
            }
        }
        return System.currentTimeMillis();
    }

    static long everyMillis(String every) {
        if ("minute".equals(every)) {
            return 60L * 1000L;
        }
        if ("hour".equals(every)) {
            return 60L * 60L * 1000L;
        }
        if ("day".equals(every)) {
            return 24L * 60L * 60L * 1000L;
        }
        if ("week".equals(every)) {
            return 7L * 24L * 60L * 60L * 1000L;
        }
        return 0L;
    }

    static void createChannel(Context context, JSONObject options) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return;
        }
        String id = options.optString("id", DEFAULT_CHANNEL_ID);
        String name = options.optString("name", id);
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        if (manager == null) {
            return;
        }
        NotificationChannel channel = new NotificationChannel(id, name, importance(options.optString("importance")));
        String description = options.optString("description", null);
        if (description != null) {
            channel.setDescription(description);
        }
        if (options.has("vibration")) {
            if (options.optBoolean("vibration", true)) {
                channel.enableVibration(true);
            } else {
                channel.enableVibration(false);
            }
        }
        if (options.has("lights")) {
            channel.enableLights(options.optBoolean("lights", true));
        }
        String lightColor = options.optString("lightColor", null);
        if (lightColor != null) {
            try {
                channel.setLightColor(Color.parseColor(lightColor));
            } catch (IllegalArgumentException ignored) {}
        }
        String sound = options.optString("sound", null);
        if (sound != null) {
            Uri uri = resolveSound(context, sound);
            if (uri == null) {
                channel.setSound(null, null);
            } else {
                channel.setSound(uri, new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION).build());
            }
        }
        channel.setLockscreenVisibility(visibility(options.optString("visibility")));
        manager.createNotificationChannel(channel);
    }

    static void deleteChannel(Context context, String id) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return;
        }
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        if (manager != null) {
            manager.deleteNotificationChannel(id);
        }
    }

    static JSONArray listChannels(Context context) {
        JSONArray result = new JSONArray();
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return result;
        }
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        if (manager == null) {
            return result;
        }
        for (NotificationChannel channel : manager.getNotificationChannels()) {
            JSONObject item = new JSONObject();
            try {
                item.put("id", channel.getId());
                item.put("name", channel.getName().toString());
                if (channel.getDescription() != null) {
                    item.put("description", channel.getDescription());
                }
                item.put("importance", importanceName(channel.getImportance()));
                item.put("vibration", channel.shouldVibrate());
                item.put("lights", channel.shouldShowLights());
                item.put("visibility", visibilityName(channel.getLockscreenVisibility()));
                if (channel.getSound() != null) {
                    item.put("sound", channel.getSound().toString());
                }
            } catch (JSONException ignored) {}
            result.put(item);
        }
        return result;
    }

    static Notification build(Context context, JSONObject options) {
        String channelId = options.optString("channelId", DEFAULT_CHANNEL_ID);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager manager = context.getSystemService(NotificationManager.class);
            if (manager != null && manager.getNotificationChannel(channelId) == null) {
                try {
                    createChannel(context, new JSONObject().put("id", channelId).put("name", channelId));
                } catch (JSONException ignored) {}
            }
        }

        int id = options.optInt("id");
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, channelId)
            .setSmallIcon(resolveSmallIcon(context, options.optString("smallIcon", null)))
            .setContentTitle(options.optString("title", ""))
            .setContentText(options.optString("body", ""))
            .setAutoCancel(options.optBoolean("autoCancel", true))
            .setOngoing(options.optBoolean("ongoing", false))
            .setPriority(priority(options.optString("priority")));

        String largeBody = options.optString("largeBody", null);
        if (largeBody != null && !largeBody.trim().isEmpty()) {
            builder.setStyle(new NotificationCompat.BigTextStyle().bigText(largeBody));
        }
        String summaryText = options.optString("summaryText", null);
        if (summaryText != null && !summaryText.trim().isEmpty()) {
            builder.setSubText(summaryText);
        }
        String largeIcon = options.optString("largeIcon", null);
        if (largeIcon != null && !largeIcon.trim().isEmpty()) {
            int largeIconRes = resolveDrawable(context, largeIcon);
            if (largeIconRes != 0) {
                builder.setLargeIcon(android.graphics.BitmapFactory.decodeResource(context.getResources(), largeIconRes));
            }
        }
        String iconColor = options.optString("iconColor", null);
        if (iconColor != null && !iconColor.trim().isEmpty()) {
            try {
                builder.setColor(Color.parseColor(iconColor));
            } catch (IllegalArgumentException ignored) {}
        }
        if (options.has("badge")) {
            builder.setNumber(options.optInt("badge", 0));
        }
        String group = options.optString("group", null);
        if (group != null && !group.trim().isEmpty()) {
            builder.setGroup(group);
        }

        String sound = options.optString("sound", null);
        if ("none".equals(sound)) {
            builder.setSilent(true);
        } else if (sound != null) {
            Uri uri = resolveSound(context, sound);
            if (uri != null) {
                builder.setSound(uri);
            }
        }

        PendingIntent tapIntent = buildTapIntent(context, options);
        if (tapIntent != null) {
            builder.setContentIntent(tapIntent);
        }

        PendingIntent dismissIntent = buildDismissIntent(context, options);
        if (dismissIntent != null) {
            builder.setDeleteIntent(dismissIntent);
        }

        addActions(context, builder, options, id);

        return builder.build();
    }

    static void show(Context context, JSONObject options) {
        Notification notification = build(context, options);
        NotificationManagerCompat.from(context).notify(options.optInt("id"), notification);
    }

    static void cancel(Context context, int id) {
        NotificationManagerCompat.from(context).cancel(id);
    }

    static void cancelAll(Context context) {
        NotificationManagerCompat.from(context).cancelAll();
    }

    static boolean canPostNotifications(Context context) {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) {
            return false;
        }
        if (Build.VERSION.SDK_INT >= 33) {
            return (
                context.checkSelfPermission("android.permission.POST_NOTIFICATIONS") == android.content.pm.PackageManager.PERMISSION_GRANTED
            );
        }
        return true;
    }

    static PendingIntent buildTapIntent(Context context, JSONObject options) {
        Intent intent = context.getPackageManager().getLaunchIntentForPackage(context.getPackageName());
        if (intent == null) {
            return null;
        }
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        intent.putExtra(EXTRA_TAP, options.toString());
        intent.putExtra(EXTRA_ID, options.optInt("id"));
        intent.putExtra(EXTRA_ACTION_ID, "tap");
        return PendingIntent.getActivity(context, options.optInt("id"), intent, pendingIntentFlags());
    }

    static PendingIntent buildDismissIntent(Context context, JSONObject options) {
        Intent intent = new Intent(context, NotificationActionReceiver.class);
        intent.setAction(NotificationActionReceiver.ACTION_DISMISS);
        intent.putExtra(EXTRA_ID, options.optInt("id"));
        int requestCode = ("dismiss:" + options.optInt("id")).hashCode();
        return PendingIntent.getBroadcast(context, requestCode, intent, pendingIntentFlags());
    }

    private static void addActions(Context context, NotificationCompat.Builder builder, JSONObject options, int id) {
        String groupId = options.optString("actionGroupId", null);
        if (groupId == null || groupId.trim().isEmpty()) {
            return;
        }
        JSONArray actions = new SwbNotifStore(context).getActionGroup(groupId);
        if (actions == null) {
            return;
        }
        int count = Math.min(actions.length(), SwbNotifLimits.MAX_ACTIONS_PER_GROUP);
        for (int i = 0; i < count; i++) {
            JSONObject action = actions.optJSONObject(i);
            if (action == null) {
                continue;
            }
            String actionId = action.optString("id", "action" + i);
            String title = action.optString("title", actionId);
            Intent intent = new Intent(context, NotificationActionReceiver.class);
            intent.setAction(NotificationActionReceiver.ACTION);
            intent.putExtra(EXTRA_OPTIONS, options.toString());
            intent.putExtra(EXTRA_ACTION_ID, actionId);
            intent.putExtra(EXTRA_ID, id);
            int requestCode = (String.valueOf(id) + ":" + actionId).hashCode();
            PendingIntent pendingIntent = PendingIntent.getBroadcast(context, requestCode, intent, pendingIntentFlags());
            builder.addAction(0, title, pendingIntent);
        }
    }

    static int pendingIntentFlags() {
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }
        return flags;
    }

    private static int resolveSmallIcon(Context context, String name) {
        int resource = resolveDrawable(context, name);
        if (resource != 0) {
            return resource;
        }
        int appIcon = context.getApplicationInfo().icon;
        return appIcon != 0 ? appIcon : android.R.drawable.ic_dialog_info;
    }

    private static int resolveDrawable(Context context, String name) {
        if (name == null || name.trim().isEmpty()) {
            return 0;
        }
        int resource = context.getResources().getIdentifier(name, "drawable", context.getPackageName());
        if (resource == 0) {
            resource = context.getResources().getIdentifier(name, "mipmap", context.getPackageName());
        }
        return resource;
    }

    private static Uri resolveSound(Context context, String sound) {
        if (sound == null || sound.trim().isEmpty()) {
            return RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
        }
        if ("none".equals(sound)) {
            return null;
        }
        int resource = context.getResources().getIdentifier(sound, "raw", context.getPackageName());
        if (resource != 0) {
            return Uri.parse("android.resource://" + context.getPackageName() + "/" + resource);
        }
        if (sound.contains("://")) {
            return Uri.parse(sound);
        }
        return Settings.System.DEFAULT_NOTIFICATION_URI;
    }

    private static int importance(String value) {
        if ("none".equals(value)) {
            return NotificationManager.IMPORTANCE_NONE;
        }
        if ("min".equals(value)) {
            return NotificationManager.IMPORTANCE_MIN;
        }
        if ("low".equals(value)) {
            return NotificationManager.IMPORTANCE_LOW;
        }
        if ("high".equals(value) || "max".equals(value)) {
            return NotificationManager.IMPORTANCE_HIGH;
        }
        return NotificationManager.IMPORTANCE_DEFAULT;
    }

    private static String importanceName(int importance) {
        if (importance <= NotificationManager.IMPORTANCE_NONE) {
            return "none";
        }
        if (importance <= NotificationManager.IMPORTANCE_MIN) {
            return "min";
        }
        if (importance <= NotificationManager.IMPORTANCE_LOW) {
            return "low";
        }
        if (importance >= NotificationManager.IMPORTANCE_HIGH) {
            return "high";
        }
        return "default";
    }

    private static int visibility(String value) {
        if ("private".equals(value)) {
            return Notification.VISIBILITY_PRIVATE;
        }
        if ("secret".equals(value)) {
            return Notification.VISIBILITY_SECRET;
        }
        return Notification.VISIBILITY_PUBLIC;
    }

    private static String visibilityName(int value) {
        if (value == Notification.VISIBILITY_PRIVATE) {
            return "private";
        }
        if (value == Notification.VISIBILITY_SECRET) {
            return "secret";
        }
        return "public";
    }

    private static int priority(String value) {
        if ("min".equals(value)) {
            return NotificationCompat.PRIORITY_MIN;
        }
        if ("low".equals(value)) {
            return NotificationCompat.PRIORITY_LOW;
        }
        if ("high".equals(value)) {
            return NotificationCompat.PRIORITY_HIGH;
        }
        if ("max".equals(value)) {
            return NotificationCompat.PRIORITY_MAX;
        }
        return NotificationCompat.PRIORITY_DEFAULT;
    }
}
