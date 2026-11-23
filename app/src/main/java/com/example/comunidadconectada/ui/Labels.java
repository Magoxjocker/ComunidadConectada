package com.example.comunidadconectada.ui;

import android.content.Context;

import com.example.comunidadconectada.R;

import java.util.ArrayList;
import java.util.List;

final class Labels {
    static final Labels SALE_CATEGORIES = new Labels(
            List.of("home", "technology", "clothing", "services", "other"),
            List.of(R.string.category_home, R.string.category_tech, R.string.category_clothes, R.string.category_services, R.string.other));
    static final Labels SALE_STATES = new Labels(List.of("available", "reserved", "sold"),
            List.of(R.string.available, R.string.reserved, R.string.sold));
    static final Labels PRIORITIES = new Labels(List.of("info", "important", "urgent"),
            List.of(R.string.priority_info, R.string.priority_important, R.string.priority_urgent));
    static final Labels ALERT_CATEGORIES = new Labels(List.of("security", "fire", "accident", "medical", "other"),
            List.of(R.string.alert_security, R.string.alert_fire, R.string.alert_accident, R.string.alert_medical, R.string.alert_other));
    static final Labels ALERT_STATES = new Labels(List.of("active", "attended", "closed"),
            List.of(R.string.alert_active, R.string.alert_attended, R.string.alert_closed));
    static final Labels REPORT_CATEGORIES = new Labels(List.of("lighting", "cleaning", "leak", "elevator", "garden", "security", "other"),
            List.of(R.string.report_lighting, R.string.report_cleaning, R.string.report_leak, R.string.report_elevator,
                    R.string.report_garden, R.string.alert_security, R.string.other));
    static final Labels REPORT_STATES = new Labels(List.of("pending", "working", "resolved"),
            List.of(R.string.report_pending, R.string.report_working, R.string.report_resolved));

    private final List<String> keys;
    private final List<Integer> labels;

    private Labels(List<String> keys, List<Integer> labels) {
        this.keys = keys;
        this.labels = labels;
    }

    String of(Context context, String key) {
        int index = keys.indexOf(key);
        return index < 0 ? "" : context.getString(labels.get(index));
    }

    List<String> forContext(Context context) {
        List<String> values = new ArrayList<>();
        for (int label : labels) values.add(context.getString(label));
        return values;
    }

    Field field(Context context, String name, int label, String initial) {
        return Field.choice(name, label, initial, keys, forContext(context));
    }

    String at(int position) { return keys.get(position); }

    static int priority(String key) {
        return switch (key) {
            case "urgent" -> 2;
            case "important" -> 1;
            default -> 0;
        };
    }
}
