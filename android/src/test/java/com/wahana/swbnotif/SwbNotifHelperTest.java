package com.wahana.swbnotif;

import static org.junit.Assert.*;

import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Test;

public class SwbNotifHelperTest {

    @Test
    public void testValidateOptionsValid() throws JSONException {
        JSONObject options = new JSONObject();
        options.put("title", "Clean Notification");
        options.put("body", "Body content");
        SwbNotifHelper.validateOptions(options);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testValidateOptionsEmptyTitleThrows() throws JSONException {
        JSONObject options = new JSONObject();
        options.put("title", "   ");
        SwbNotifHelper.validateOptions(options);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testValidateOptionsMissingTitleThrows() throws JSONException {
        JSONObject options = new JSONObject();
        SwbNotifHelper.validateOptions(options);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testValidateOptionsLongTitleThrows() throws JSONException {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < SwbNotifLimits.MAX_TITLE_LENGTH + 10; i++) {
            sb.append("A");
        }
        JSONObject options = new JSONObject();
        options.put("title", sb.toString());
        SwbNotifHelper.validateOptions(options);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testValidateOptionsLongBodyThrows() throws JSONException {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < SwbNotifLimits.MAX_BODY_LENGTH + 10; i++) {
            sb.append("B");
        }
        JSONObject options = new JSONObject();
        options.put("title", "Valid Title");
        options.put("body", sb.toString());
        SwbNotifHelper.validateOptions(options);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testValidateOptionsLargeExtraThrows() throws JSONException {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < SwbNotifLimits.MAX_EXTRA_BYTES + 100; i++) {
            sb.append("C");
        }
        JSONObject extra = new JSONObject();
        extra.put("payload", sb.toString());

        JSONObject options = new JSONObject();
        options.put("title", "Valid Title");
        options.put("extra", extra);
        SwbNotifHelper.validateOptions(options);
    }

    @Test
    public void testEveryMillis() {
        assertEquals(60L * 1000L, SwbNotifHelper.everyMillis("minute"));
        assertEquals(60L * 60L * 1000L, SwbNotifHelper.everyMillis("hour"));
        assertEquals(24L * 60L * 60L * 1000L, SwbNotifHelper.everyMillis("day"));
        assertEquals(7L * 24L * 60L * 60L * 1000L, SwbNotifHelper.everyMillis("week"));
        assertEquals(0L, SwbNotifHelper.everyMillis("month"));
    }

    @Test
    public void testParseTimeNumeric() throws JSONException {
        JSONObject options = new JSONObject();
        long now = 1700000000000L;
        options.put("scheduleAt", now);
        assertEquals(now, SwbNotifHelper.parseTime(options));
    }

    @Test
    public void testParseTimeStringTimestamp() throws JSONException {
        JSONObject options = new JSONObject();
        options.put("scheduleAt", "1700000000000");
        assertEquals(1700000000000L, SwbNotifHelper.parseTime(options));
    }
}
