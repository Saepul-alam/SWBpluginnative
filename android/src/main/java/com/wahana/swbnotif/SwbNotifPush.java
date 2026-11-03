package com.wahana.swbnotif;

import com.getcapacitor.JSObject;
import com.getcapacitor.PluginCall;
import com.google.firebase.messaging.FirebaseMessaging;
import org.json.JSONObject;

/**
 * Isolated so the plugin still loads when the consuming app does not include
 * Firebase Messaging (the dependency is `compileOnly`).
 */
final class SwbNotifPush {

    private SwbNotifPush() {}

    static void register(final PluginCall call, final SwbNotifStore store) {
        FirebaseMessaging
            .getInstance()
            .getToken()
            .addOnCompleteListener(
                task -> {
                    String token = null;
                    if (task.isSuccessful()) {
                        token = task.getResult();
                    }
                    if (token != null) {
                        store.setPushToken(token);
                    }
                    JSObject result = new JSObject();
                    result.put("token", token == null ? JSONObject.NULL : token);
                    call.resolve(result);
                }
            );
    }

    static void unregister(final PluginCall call, final SwbNotifStore store) {
        FirebaseMessaging
            .getInstance()
            .deleteToken()
            .addOnCompleteListener(
                task -> {
                    store.deletePushToken();
                    call.resolve();
                }
            );
    }
}
