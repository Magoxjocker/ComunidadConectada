package com.example.comunidadconectada.ui;

import android.os.Bundle;

import java.util.ArrayList;
import java.util.List;

public record Field(String key, int label, Type type, String initial, int minimum, int maximum,
                    List<String> values, List<String> labels) {
    public enum Type { TEXT, MULTILINE, EMAIL, PRICE, DATE, OPTIONS, CHOICE, CHECK }

    public static Field text(String key, int label, String initial, int maximum) {
        return basic(key, label, Type.TEXT, initial, 1, maximum);
    }

    public static Field paragraph(String key, int label, String initial, int minimum, int maximum) {
        return basic(key, label, Type.MULTILINE, initial, minimum, maximum);
    }

    public static Field email(String key, int label) {
        return basic(key, label, Type.EMAIL, "", 1, 254);
    }

    public static Field price(String key, int label, String initial) {
        return basic(key, label, Type.PRICE, initial, 0, 1_000_000_000);
    }

    public static Field date(String key, int label) {
        return basic(key, label, Type.DATE, "", 1, 16);
    }

    public static Field options(String key, int label) {
        return basic(key, label, Type.OPTIONS, "", 1, 606);
    }

    public static Field choice(String key, int label, String initial, List<String> values, List<String> labels) {
        return new Field(key, label, Type.CHOICE, initial, 0, 0, values, labels);
    }

    public static Field check(String key, int label, boolean initial) {
        return basic(key, label, Type.CHECK, Boolean.toString(initial), 0, 0);
    }

    private static Field basic(String key, int label, Type type, String initial, int minimum, int maximum) {
        return new Field(key, label, type, initial, minimum, maximum, List.of(), List.of());
    }

    Bundle toBundle() {
        Bundle value = new Bundle();
        value.putString("key", key);
        value.putInt("label", label);
        value.putString("type", type.name());
        value.putString("initial", initial);
        value.putInt("minimum", minimum);
        value.putInt("maximum", maximum);
        value.putStringArrayList("values", new ArrayList<>(values));
        value.putStringArrayList("labels", new ArrayList<>(labels));
        return value;
    }

    static Field fromBundle(Bundle value) {
        return new Field(value.getString("key"), value.getInt("label"), Type.valueOf(value.getString("type")),
                value.getString("initial"), value.getInt("minimum"), value.getInt("maximum"),
                value.getStringArrayList("values"), value.getStringArrayList("labels"));
    }
}
