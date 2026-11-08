package com.github.gcolin.desk;

import java.util.ArrayList;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;

final class EventDeskJson {

    private EventDeskJson() {}

    static String toJson(Object value) {
        Object wrapped = JSONObject.wrap(value);
        return wrapped == null ? "null" : wrapped.toString();
    }

    static EventDeskOp.SyncRequest parseSyncRequest(String message) {
        JSONObject json = new JSONObject(message);
        EventDeskOp.SyncRequest request = new EventDeskOp.SyncRequest();
        request.setType(optString(json, "type"));
        request.setEventId(optInteger(json, "eventId"));
        request.setOps(parseOps(json.optJSONArray("ops")));
        request.setBatches(parseBatches(json.optJSONArray("batches")));
        request.setEventIds(parseIntegers(json.optJSONArray("eventIds")));
        return request;
    }

    private static List<EventDeskOp> parseOps(JSONArray array) {
        List<EventDeskOp> ops = new ArrayList<>();
        if (array == null) {
            return ops;
        }
        for (int i = 0; i < array.length(); i++) {
            JSONObject obj = array.optJSONObject(i);
            if (obj == null) {
                continue;
            }
            EventDeskOp op = new EventDeskOp();
            op.setId(optString(obj, "id"));
            op.setSubId(optInteger(obj, "subId"));
            op.setOptionId(optInteger(obj, "optionId"));
            if (obj.has("present") && !obj.isNull("present")) {
                op.setPresent(obj.getBoolean("present"));
            }
            op.setStatus(optString(obj, "status"));
            op.setClientTs(optLong(obj, "clientTs"));
            ops.add(op);
        }
        return ops;
    }

    private static List<EventDeskOp.SyncBatch> parseBatches(JSONArray array) {
        List<EventDeskOp.SyncBatch> batches = new ArrayList<>();
        if (array == null) {
            return batches;
        }
        for (int i = 0; i < array.length(); i++) {
            JSONObject obj = array.optJSONObject(i);
            if (obj == null) {
                continue;
            }
            EventDeskOp.SyncBatch batch = new EventDeskOp.SyncBatch();
            batch.setEventId(optInteger(obj, "eventId"));
            batch.setOps(parseOps(obj.optJSONArray("ops")));
            batches.add(batch);
        }
        return batches;
    }

    private static List<Integer> parseIntegers(JSONArray array) {
        List<Integer> values = new ArrayList<>();
        if (array == null) {
            return values;
        }
        for (int i = 0; i < array.length(); i++) {
            if (array.isNull(i)) {
                continue;
            }
            values.add(array.getInt(i));
        }
        return values;
    }

    private static String optString(JSONObject obj, String key) {
        if (!obj.has(key) || obj.isNull(key)) {
            return null;
        }
        String value = obj.optString(key, null);
        return value == null || value.isEmpty() ? null : value;
    }

    private static Integer optInteger(JSONObject obj, String key) {
        if (!obj.has(key) || obj.isNull(key)) {
            return null;
        }
        return obj.getInt(key);
    }

    private static Long optLong(JSONObject obj, String key) {
        if (!obj.has(key) || obj.isNull(key)) {
            return null;
        }
        return obj.getLong(key);
    }
}
