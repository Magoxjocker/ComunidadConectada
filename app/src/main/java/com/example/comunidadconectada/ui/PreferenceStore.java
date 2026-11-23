package com.example.comunidadconectada.ui;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;

import androidx.appcompat.app.AppCompatDelegate;

public final class PreferenceStore {
    private static final String TEXT_SIZE = "text";
    private static final String NIGHT_MODE = "night";
    private static final String HIGH_CONTRAST = "contrast";
    private static final float[] TEXT_MULTIPLIERS = {1f, 1.15f, 1.3f};
    private static final int[] NIGHT_MODES = {
            AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM,
            AppCompatDelegate.MODE_NIGHT_YES,
            AppCompatDelegate.MODE_NIGHT_NO
    };

    private final SharedPreferences preferences;

    public PreferenceStore(Context context) {
        preferences = context.getSharedPreferences("accessibility", Context.MODE_PRIVATE);
    }

    public Context applyFont(Context context) {
        float multiplier = TEXT_MULTIPLIERS[textSize()];
        if (multiplier == 1f) return context;
        Configuration configuration = new Configuration(context.getResources().getConfiguration());
        configuration.fontScale *= multiplier;
        return context.createConfigurationContext(configuration);
    }

    public void applyNightMode() {
        AppCompatDelegate.setDefaultNightMode(NIGHT_MODES[nightMode()]);
    }

    public int textSize() { return preferences.getInt(TEXT_SIZE, 0); }
    public int nightMode() { return preferences.getInt(NIGHT_MODE, 0); }
    public boolean highContrast() { return preferences.getBoolean(HIGH_CONTRAST, false); }
    public void setTextSize(int selection) { preferences.edit().putInt(TEXT_SIZE, selection).apply(); }
    public void setNightMode(int selection) { preferences.edit().putInt(NIGHT_MODE, selection).apply(); }
    public void setHighContrast(boolean enabled) { preferences.edit().putBoolean(HIGH_CONTRAST, enabled).apply(); }
}
