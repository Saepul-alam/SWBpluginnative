package com.wahana.swbnotif;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

final class SwbNotifStore {

    private static final String PREFS_NAME = "swbnotif";
    private static final String KEY_SCHEMA_VERSION = "schema_version";
    private static final int CURRENT_SCHEMA_VERSION = 2;

    private static final String KEY_SCHEDULED = "scheduled";
    private static final String KEY_DELIVERED = "delivered";
    private static final String KEY_ACTION_GROUPS = "action_groups";
    private static final String KEY_LAST_ACTION = "last_action";
    private static final String KEY_PUSH_TOKEN = "push_token";
    private static final String KEY_BADGE = "badge";

    private final SharedPreferences prefs;

    SwbNotifStore(Context context) {
        this.prefs = context.getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        migrateSchemaIfNeeded();
    }

    private synchronized void migrateSchemaIfNeeded() {
        int currentVersion = prefs.getInt(KEY_SCHEMA_VERSION, 1);
        if (currentVersion < CURRENT_SCHEMA_VERSION) {
            // Migration from schema 1 to 2:
            // Ensure scheduled and delivered collections obey capacity constraints and valid JSON
            List<JSONObject> scheduled = readArray(KEY_SCHEDULED);
            if (scheduled.size() > SwbNotifLimits.MAX_SCHEDULED) {
                scheduled = new ArrayList<>(scheduled.subList(0, SwbNotifLimits.MAX_SCHEDULED));
                writeArray(KEY_SCHEDULED, scheduled);
            }

            List<JSONObject> delivered = readArray(KEY_DELIVERED);
            if (delivered.size() > SwbNotifLimits.MAX_DELIVERED) {
                delivered = new ArrayList<>(delivered.subList(delivered.size() - SwbNotifLimits.MAX_DELIVERED, delivered.size()));
                writeArray(KEY_DELIVERED, delivered);
            }

            prefs.edit().putInt(KEY_SCHEMA_VERSION, CURRENT_SCHEMA_VERSION).apply();
        }
    }

    synchronized List<JSONObject> getScheduled() {
        return readArray(KEY_SCHEDULED);
    }

    synchronized void upsertScheduled(JSONObject notification) {
        int id = notification.optInt("id");
        List<JSONObject> items = readArray(KEY_SCHEDULED);
        boolean replaced = false;
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i).optInt("id") == id) {
                items.set(i, notification);
                replaced = true;
                break;
            }
        }
        if (!replaced) {
            if (items.size() >= SwbNotifLimits.MAX_SCHEDULED) {
                throw new IllegalStateException("Maximum scheduled notification limit (" + SwbNotifLimits.MAX_SCHEDULED + ") reached");
            }
            items.add(notification);
        }
        writeArray(KEY_SCHEDULED, items);
    }

    synchronized void removeScheduled(int id) {
        List<JSONObject> items = readArray(KEY_SCHEDULED);
        for (int i = items.size() - 1; i >= 0; i--) {
            if (items.get(i).optInt("id") == id) {
                items.remove(i);
            }
        }
        writeArray(KEY_SCHEDULED, items);
    }

    synchronized void clearScheduled() {
        writeArray(KEY_SCHEDULED, new ArrayList<>());
    }

    synchronized JSONObject findScheduled(int id) {
        for (JSONObject item : readArray(KEY_SCHEDULED)) {
            if (item.optInt("id") == id) {
                return item;
            }
        }
        return null;
    }

    synchronized List<JSONObject> getDelivered() {
        return readArray(KEY_DELIVERED);
    }

    synchronized void addDelivered(JSONObject notification) {
        int id = notification.optInt("id");
        List<JSONObject> items = readArray(KEY_DELIVERED);
        for (int i = items.size() - 1; i >= 0; i--) {
            if (items.get(i).optInt("id") == id) {
                items.remove(i);
            }
        }
        items.add(notification);
        while (items.size() > SwbNotifLimits.MAX_DELIVERED) {
            items.remove(0);
        }
        writeArray(KEY_DELIVERED, items);
    }

    synchronized void removeDelivered(int id) {
        List<JSONObject> items = readArray(KEY_DELIVERED);
        for (int i = items.size() - 1; i >= 0; i--) {
            if (items.get(i).optInt("id") == id) {
                items.remove(i);
            }
        }
        writeArray(KEY_DELIVERED, items);
    }

    synchronized void clearDelivered() {
        writeArray(KEY_DELIVERED, new ArrayList<>());
    }

    synchronized void saveActionGroup(String id, JSONArray actions) {
        JSONObject groups = readObject(KEY_ACTION_GROUPS);
        try {
            groups.put(id, actions);
        } catch (JSONException ignored) {}
        prefs.edit().putString(KEY_ACTION_GROUPS, groups.toString()).apply();
    }

    synchronized void deleteActionGroup(String id) {
        JSONObject groups = readObject(KEY_ACTION_GROUPS);
        groups.remove(id);
        prefs.edit().putString(KEY_ACTION_GROUPS, groups.toString()).apply();
    }

    synchronized JSONArray getActionGroup(String id) {
        return readObject(KEY_ACTION_GROUPS).optJSONArray(id);
    }

    synchronized void setLastAction(JSONObject action) {
        prefs.edit().putString(KEY_LAST_ACTION, action.toString()).apply();
    }

    synchronized JSONObject consumeLastAction() {
        String raw = prefs.getString(KEY_LAST_ACTION, null);
        prefs.edit().remove(KEY_LAST_ACTION).apply();
        if (raw == null) {
            return null;
        }
        try {
            return new JSONObject(raw);
        } catch (JSONException e) {
            return null;
        }
    }

    synchronized void clearLastAction() {
        prefs.edit().remove(KEY_LAST_ACTION).apply();
    }

    synchronized void setPushToken(String token) {
        prefs.edit().putString(KEY_PUSH_TOKEN, token).apply();
    }

    synchronized String getPushToken() {
        return prefs.getString(KEY_PUSH_TOKEN, null);
    }

    synchronized void deletePushToken() {
        prefs.edit().remove(KEY_PUSH_TOKEN).apply();
    }

    synchronized void setBadge(int count) {
        prefs.edit().putInt(KEY_BADGE, count).apply();
    }

    synchronized int getBadge() {
        return prefs.getInt(KEY_BADGE, 0);
    }

    private List<JSONObject> readArray(String key) {
        List<JSONObject> items = new ArrayList<>();
        String raw = prefs.getString(key, "[]");
        try {
            JSONArray array = new JSONArray(raw);
            for (int i = 0; i < array.length(); i++) {
                JSONObject item = array.optJSONObject(i);
                if (item != null) {
                    items.add(item);
                }
            }
        } catch (JSONException ignored) {}
        return items;
    }

    private void writeArray(String key, List<JSONObject> items) {
        JSONArray array = new JSONArray();
        for (JSONObject item : items) {
            array.put(item);
        }
        prefs.edit().putString(key, array.toString()).apply();
    }

    private JSONObject readObject(String key) {
        String raw = prefs.getString(key, "{}");
        try {
            return new JSONObject(raw);
        } catch (JSONException e) {
            return new JSONObject();
        }
    }
}
