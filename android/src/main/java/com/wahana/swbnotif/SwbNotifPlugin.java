package com.wahana.swbnotif;

import android.app.Activity;
import android.app.AlarmManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import androidx.core.app.ActivityCompat;
import com.getcapacitor.JSArray;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import com.getcapacitor.annotation.Permission;
import com.getcapacitor.annotation.PermissionCallback;
import java.lang.ref.WeakReference;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

@CapacitorPlugin(
    name = "SwbNotif",
    permissions = { @Permission(alias = "notifications", strings = { "android.permission.POST_NOTIFICATIONS" }) }
)
public class SwbNotifPlugin extends Plugin {

    private static final String POST_NOTIFICATIONS = "android.permission.POST_NOTIFICATIONS";

    private static WeakReference<SwbNotifPlugin> instance;

    private SwbNotifStore store;

    @Override
    public void load() {
        instance = new WeakReference<>(this);
        store = new SwbNotifStore(getContext());
        processLaunchIntent();
    }

    @Override
    protected void handleOnNewIntent(Intent intent) {
        super.handleOnNewIntent(intent);
        processIntent(intent);
    }

    @PluginMethod
    public void checkPermission(PluginCall call) {
        boolean granted = SwbNotifHelper.canPostNotifications(getContext());
        call.resolve(permissionResult(granted));
    }

    @PluginMethod
    public void requestPermission(PluginCall call) {
        if (SwbNotifHelper.canPostNotifications(getContext())) {
            call.resolve(permissionResult(true));
            return;
        }
        if (Build.VERSION.SDK_INT >= 33) {
            requestPermissionForAlias("notifications", call, "notificationsCallback");
            return;
        }
        call.resolve(permissionResult(false));
    }

    @PermissionCallback
    private void notificationsCallback(PluginCall call) {
        call.resolve(permissionResult(SwbNotifHelper.canPostNotifications(getContext())));
    }

    @PluginMethod
    public void checkExactAlarmPermission(PluginCall call) {
        call.resolve(grantedResult(canScheduleExactAlarms()));
    }

    @PluginMethod
    public void requestExactAlarmPermission(PluginCall call) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !canScheduleExactAlarms()) {
            Activity activity = getActivity();
            if (activity != null) {
                try {
                    Intent intent = new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM);
                    intent.setData(Uri.parse("package:" + getContext().getPackageName()));
                    activity.startActivity(intent);
                } catch (Exception ignored) {}
            }
        }
        call.resolve(grantedResult(canScheduleExactAlarms()));
    }

    @PluginMethod
    public void createChannel(PluginCall call) {
        JSONObject options = toJson(call);
        if (options == null || options.optString("id", null) == null || options.optString("id").trim().isEmpty()) {
            call.reject("A channel id is required", ErrorCode.INVALID_ARGUMENT.name());
            return;
        }
        if (options.optString("name", null) == null || options.optString("name").trim().isEmpty()) {
            call.reject("A channel name is required", ErrorCode.INVALID_ARGUMENT.name());
            return;
        }
        SwbNotifHelper.createChannel(getContext(), options);
        call.resolve();
    }

    @PluginMethod
    public void deleteChannel(PluginCall call) {
        String id = call.getString("id");
        if (id == null || id.trim().isEmpty()) {
            call.reject("A channel id is required", ErrorCode.INVALID_ARGUMENT.name());
            return;
        }
        SwbNotifHelper.deleteChannel(getContext(), id);
        call.resolve();
    }

    @PluginMethod
    public void listChannels(PluginCall call) {
        JSObject result = new JSObject();
        result.put("channels", SwbNotifHelper.listChannels(getContext()));
        call.resolve(result);
    }

    @PluginMethod
    public void schedule(PluginCall call) {
        JSONObject options = toJson(call);
        if (options == null) {
            call.reject("Invalid notification options", ErrorCode.INVALID_ARGUMENT.name());
            return;
        }
        try {
            int id = scheduleOne(options);
            call.resolve(new JSObject().put("id", id));
        } catch (IllegalArgumentException e) {
            call.reject(e.getMessage(), ErrorCode.INVALID_ARGUMENT.name());
        } catch (IllegalStateException e) {
            call.reject(e.getMessage(), ErrorCode.STORAGE_LIMIT_EXCEEDED.name());
        } catch (SecurityException e) {
            call.reject(e.getMessage(), ErrorCode.PERMISSION_DENIED.name());
        } catch (JSONException e) {
            call.reject(e.getMessage(), ErrorCode.INVALID_ARGUMENT.name());
        }
    }

    @PluginMethod
    public void scheduleMultiple(PluginCall call) {
        JSONArray notifications = call.getArray("notifications");
        if (notifications == null) {
            call.reject("notifications array is required", ErrorCode.INVALID_ARGUMENT.name());
            return;
        }
        if (notifications.length() > SwbNotifLimits.MAX_SCHEDULED) {
            call.reject(
                "Cannot schedule more than " + SwbNotifLimits.MAX_SCHEDULED + " notifications at once",
                ErrorCode.STORAGE_LIMIT_EXCEEDED.name()
            );
            return;
        }

        Set<Integer> seenIds = new HashSet<>();
        for (int i = 0; i < notifications.length(); i++) {
            JSONObject options = notifications.optJSONObject(i);
            if (options != null && options.has("id") && !options.isNull("id")) {
                int id = options.optInt("id");
                if (seenIds.contains(id)) {
                    call.reject("Duplicate notification id " + id + " in schedule list", ErrorCode.INVALID_ARGUMENT.name());
                    return;
                }
                seenIds.add(id);
            }
        }

        JSArray ids = new JSArray();
        try {
            for (int i = 0; i < notifications.length(); i++) {
                JSONObject options = notifications.optJSONObject(i);
                if (options == null) {
                    continue;
                }
                ids.put(scheduleOne(options));
            }
            call.resolve(new JSObject().put("ids", ids));
        } catch (IllegalArgumentException e) {
            call.reject(e.getMessage(), ErrorCode.INVALID_ARGUMENT.name());
        } catch (IllegalStateException e) {
            call.reject(e.getMessage(), ErrorCode.STORAGE_LIMIT_EXCEEDED.name());
        } catch (SecurityException e) {
            call.reject(e.getMessage(), ErrorCode.PERMISSION_DENIED.name());
        } catch (JSONException e) {
            call.reject(e.getMessage(), ErrorCode.INVALID_ARGUMENT.name());
        }
    }

    @PluginMethod
    public void getPending(PluginCall call) {
        JSArray notifications = new JSArray();
        for (JSONObject item : store.getScheduled()) {
            try {
                notifications.put(JSObject.fromJSONObject(item));
            } catch (JSONException ignored) {}
        }
        call.resolve(new JSObject().put("notifications", notifications));
    }

    @PluginMethod
    public void cancel(PluginCall call) {
        Integer id = call.getInt("id");
        if (id == null) {
            call.reject("A notification id is required", ErrorCode.INVALID_ARGUMENT.name());
            return;
        }
        store.removeScheduled(id);
        SwbNotifScheduler.cancel(getContext(), id);
        SwbNotifHelper.cancel(getContext(), id);
        store.removeDelivered(id);
        call.resolve();
    }

    @PluginMethod
    public void cancelAll(PluginCall call) {
        SwbNotifScheduler.cancelAll(getContext());
        SwbNotifHelper.cancelAll(getContext());
        store.clearScheduled();
        store.clearDelivered();
        call.resolve();
    }

    @PluginMethod
    public void getDelivered(PluginCall call) {
        JSArray notifications = new JSArray();
        for (JSONObject item : store.getDelivered()) {
            try {
                notifications.put(JSObject.fromJSONObject(item));
            } catch (JSONException ignored) {}
        }
        call.resolve(new JSObject().put("notifications", notifications));
    }

    @PluginMethod
    public void removeDelivered(PluginCall call) {
        JSArray ids = call.getArray("ids");
        if (ids == null) {
            call.reject("ids array is required", ErrorCode.INVALID_ARGUMENT.name());
            return;
        }
        for (int i = 0; i < ids.length(); i++) {
            int id = ids.optInt(i);
            SwbNotifHelper.cancel(getContext(), id);
            store.removeDelivered(id);
        }
        call.resolve();
    }

    @PluginMethod
    public void removeAllDelivered(PluginCall call) {
        List<JSONObject> delivered = store.getDelivered();
        for (JSONObject item : delivered) {
            SwbNotifHelper.cancel(getContext(), item.optInt("id"));
        }
        store.clearDelivered();
        call.resolve();
    }

    @PluginMethod
    public void setBadgeCount(PluginCall call) {
        Integer count = call.getInt("count", 0);
        if (count == null || count < 0) {
            call.reject("count must be a non-negative number", ErrorCode.INVALID_ARGUMENT.name());
            return;
        }
        store.setBadge(count);
        call.resolve();
    }

    @PluginMethod
    public void getBadgeCount(PluginCall call) {
        call.resolve(new JSObject().put("count", store.getBadge()));
    }

    @PluginMethod
    public void createActionGroup(PluginCall call) {
        JSONObject data = toJson(call);
        if (data == null) {
            call.reject("Invalid action group", ErrorCode.INVALID_ARGUMENT.name());
            return;
        }
        String id = data.optString("id", null);
        JSONArray actions = data.optJSONArray("actions");
        if (id == null || id.trim().isEmpty() || actions == null) {
            call.reject("An id and actions are required", ErrorCode.INVALID_ARGUMENT.name());
            return;
        }
        if (actions.length() > SwbNotifLimits.MAX_ACTIONS_PER_GROUP) {
            call.reject(
                "An action group cannot have more than " + SwbNotifLimits.MAX_ACTIONS_PER_GROUP + " actions",
                ErrorCode.INVALID_ARGUMENT.name()
            );
            return;
        }
        store.saveActionGroup(id, actions);
        call.resolve();
    }

    @PluginMethod
    public void deleteActionGroup(PluginCall call) {
        String id = call.getString("id");
        if (id == null || id.trim().isEmpty()) {
            call.reject("An action group id is required", ErrorCode.INVALID_ARGUMENT.name());
            return;
        }
        store.deleteActionGroup(id);
        call.resolve();
    }

    @PluginMethod
    public void registerPush(PluginCall call) {
        try {
            SwbNotifPush.register(call, store);
        } catch (Throwable error) {
            call.reject(
                "Firebase Messaging is not available. Add com.google.firebase:firebase-messaging and google-services.json to the app.",
                ErrorCode.PUSH_UNAVAILABLE.name()
            );
        }
    }

    @PluginMethod
    public void getPushToken(PluginCall call) {
        String token = store.getPushToken();
        call.resolve(new JSObject().put("token", token == null ? JSONObject.NULL : token));
    }

    @PluginMethod
    public void unregisterPush(PluginCall call) {
        try {
            SwbNotifPush.unregister(call, store);
        } catch (Throwable error) {
            store.deletePushToken();
            call.resolve();
        }
    }

    @PluginMethod
    public void getLastAction(PluginCall call) {
        JSONObject action = store.consumeLastAction();
        if (action == null) {
            call.resolve();
            return;
        }
        try {
            call.resolve(JSObject.fromJSONObject(action));
        } catch (JSONException e) {
            call.resolve();
        }
    }

    @PluginMethod
    public void clearLastAction(PluginCall call) {
        store.clearLastAction();
        call.resolve();
    }

    static void emitNotificationReceived(JSONObject notification) {
        emit("notificationReceived", notification);
    }

    static void emitNotificationActionPerformed(JSONObject event) {
        emit("notificationActionPerformed", event);
    }

    static void emitPushToken(String token) {
        JSObject data = new JSObject();
        data.put("token", token == null ? JSONObject.NULL : token);
        emit("pushTokenReceived", data);
    }

    static void emitNotificationDismissed(int id) {
        JSObject data = new JSObject();
        data.put("id", id);
        emit("notificationDismissed", data);
    }

    private static void emit(String event, JSONObject payload) {
        SwbNotifPlugin plugin = instance == null ? null : instance.get();
        if (plugin == null) {
            return;
        }
        final JSObject data;
        try {
            data = JSObject.fromJSONObject(payload);
        } catch (JSONException e) {
            return;
        }
        Activity activity = plugin.getActivity();
        if (activity != null) {
            activity.runOnUiThread(() -> plugin.notifyListeners(event, data));
        } else {
            plugin.notifyListeners(event, data);
        }
    }

    private int scheduleOne(JSONObject options) throws JSONException, IllegalArgumentException, IllegalStateException {
        SwbNotifHelper.validateOptions(options);

        if (!SwbNotifHelper.canPostNotifications(getContext())) {
            throw new SecurityException("Notification permission has not been granted");
        }

        int id = options.has("id") && !options.isNull("id") ? options.optInt("id") : SwbNotifHelper.generateId();
        options.put("id", id);

        if (options.has("scheduleAt")) {
            long at = SwbNotifHelper.parseTime(options);
            String every = options.optString("every", null);
            if (every != null && !every.isEmpty()) {
                long interval = SwbNotifHelper.everyMillis(every);
                long now = System.currentTimeMillis();
                if (interval > 0 && at <= now) {
                    while (at <= now) {
                        at += interval;
                    }
                }
            }
            options.put("scheduleAt", at);
            store.upsertScheduled(options);
            SwbNotifScheduler.schedule(getContext(), options);
            return id;
        }

        SwbNotifHelper.show(getContext(), options);
        JSONObject delivered = new JSONObject(options.toString());
        delivered.put("receivedAt", System.currentTimeMillis());
        store.addDelivered(delivered);
        emitNotificationReceived(delivered);
        return id;
    }

    private boolean canScheduleExactAlarms() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            return true;
        }
        AlarmManager manager = (AlarmManager) getContext().getSystemService(Context.ALARM_SERVICE);
        return manager == null || manager.canScheduleExactAlarms();
    }

    private JSObject permissionResult(boolean granted) {
        JSObject result = new JSObject();
        result.put("granted", granted);
        result.put("display", displayStatus(granted));
        return result;
    }

    private JSObject grantedResult(boolean granted) {
        return new JSObject().put("granted", granted);
    }

    private String displayStatus(boolean granted) {
        if (granted) {
            return "granted";
        }
        if (Build.VERSION.SDK_INT >= 33) {
            Activity activity = getActivity();
            if (activity != null && ActivityCompat.shouldShowRequestPermissionRationale(activity, POST_NOTIFICATIONS)) {
                return "prompt-with-rationale";
            }
            return "prompt";
        }
        return "denied";
    }

    private JSONObject toJson(PluginCall call) {
        try {
            return new JSONObject(call.getData().toString());
        } catch (JSONException e) {
            return null;
        }
    }

    private void processLaunchIntent() {
        Activity activity = getActivity();
        if (activity != null) {
            processIntent(activity.getIntent());
        }
    }

    private void processIntent(Intent intent) {
        if (intent == null) {
            return;
        }
        String raw = intent.getStringExtra(SwbNotifHelper.EXTRA_TAP);
        if (raw == null) {
            return;
        }
        intent.removeExtra(SwbNotifHelper.EXTRA_TAP);
        try {
            JSONObject notification = new JSONObject(raw);
            if (!notification.has("receivedAt")) {
                notification.put("receivedAt", System.currentTimeMillis());
            }
            JSONObject event = new JSONObject();
            event.put("actionId", "tap");
            event.put("notification", notification);
            if (store != null) {
                store.setLastAction(event);
            }
            emitNotificationActionPerformed(event);
        } catch (JSONException ignored) {}
    }
}
