package com.example.comunidadconectada.services;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.SystemClock;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.StringJoiner;
import java.util.concurrent.TimeUnit;

public final class FcmDiagnostics {
    private static final String PREFERENCES = "fcm_diagnostics";
    private static final String STARTED_AT = "started_at";
    private static final String RECORDING = "recording";
    private static final String HISTORY = "history";
    private static final long CAPTURE_DURATION_MS = TimeUnit.HOURS.toMillis(24);
    static final int MAX_ENTRIES = 40;
    private static final Object LOCK = new Object();

    public enum Phase { RECEIVED, VALIDATION, MEMBERSHIP_READ, ANNOUNCEMENT_READ, RECHECK, NOTIFY, ACTIVE_CHECK, TOKEN_RENEWAL, TOKEN_DELETE, TOKEN_GET, TOKEN_COMPARE, TOKEN_REGISTER }

    public enum Result {
        OK, BUSY, TOKEN_FAILED, WRITE_FAILED, TOKEN_UNCHANGED, INVALID_HASH, REPOSITORY_UNAVAILABLE, NOTIFICATIONS_DISABLED, NO_SESSION, UID_MISMATCH, COMMUNITY_MISMATCH,
        INVALID_ID, MEMBERSHIP_MISSING, MEMBERSHIP_INACTIVE, ANNOUNCEMENT_MISSING, NOT_PUBLISHED, EXPIRED, DUPLICATE,
        CONTENT_MISSING, TIMEOUT, READ_FAILED, PERMISSION_DENIED, INTERRUPTED, UNEXPECTED_ERROR,
        ACTIVE, NOT_ACTIVE, CHANNEL_MISSING, CHANNEL_BLOCKED, CHECK_FAILED
    }

    public record Snapshot(boolean recording, String summary) {}

    private final SharedPreferences preferences;
    private final long startedAt;

    private FcmDiagnostics(SharedPreferences preferences, long startedAt) {
        this.preferences = preferences;
        this.startedAt = startedAt;
    }

    static FcmDiagnostics capture(Context context) {
        synchronized (LOCK) {
            try {
                SharedPreferences preferences = preferences(context);
                long startedAt = currentWindow(preferences, System.currentTimeMillis());
                return new FcmDiagnostics(preferences, preferences.getBoolean(RECORDING, false) ? startedAt : 0);
            } catch (RuntimeException unavailable) {
                return new FcmDiagnostics(null, 0);
            }
        }
    }

    boolean enabled() {
        return startedAt > 0;
    }

    void record(Phase phase, long phaseStartedAt, Result result) {
        if (!enabled()) return;
        synchronized (LOCK) {
            try {
                long now = System.currentTimeMillis();
                if (currentWindow(preferences, now) != startedAt || !preferences.getBoolean(RECORDING, false)) return;
                Entry entry = new Entry(now, phase, SystemClock.elapsedRealtime() - phaseStartedAt, result);
                ArrayDeque<Entry> entries = readEntries(preferences.getString(HISTORY, ""));
                addEntry(entries, entry);
                preferences.edit().putString(HISTORY, encode(entries)).apply();
            } catch (RuntimeException unavailable) {
                // A local diagnostic failure must not interrupt notification delivery.
            }
        }
    }

    public static void start(Context context) {
        synchronized (LOCK) {
            preferences(context).edit().clear().putLong(STARTED_AT, System.currentTimeMillis())
                    .putBoolean(RECORDING, true).apply();
        }
    }

    public static void stop(Context context) {
        synchronized (LOCK) {
            preferences(context).edit().remove(RECORDING).apply();
        }
    }

    public static void clear(Context context) {
        synchronized (LOCK) {
            preferences(context).edit().clear().apply();
        }
    }

    public static Snapshot snapshot(Context context) {
        synchronized (LOCK) {
            SharedPreferences preferences = preferences(context);
            if (currentWindow(preferences, System.currentTimeMillis()) == 0) return new Snapshot(false, "");
            var entries = readEntries(preferences.getString(HISTORY, ""));
            return new Snapshot(preferences.getBoolean(RECORDING, false), format(entries));
        }
    }

    private static long currentWindow(SharedPreferences preferences, long now) {
        long startedAt = preferences.getLong(STARTED_AT, 0);
        if (startedAt == 0 || windowOpen(startedAt, now)) return startedAt;
        preferences.edit().clear().apply();
        return 0;
    }

    static boolean windowOpen(long startedAt, long now) {
        return startedAt > 0 && now >= startedAt && now - startedAt < CAPTURE_DURATION_MS;
    }

    private static SharedPreferences preferences(Context context) {
        return context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE);
    }

    record Entry(long timestamp, Phase phase, long duration, Result result) {}

    static ArrayDeque<Entry> readEntries(String stored) {
        ArrayDeque<Entry> entries = new ArrayDeque<>();
        for (String line : stored.split("\n")) {
            String[] fields = line.split("\\|", -1);
            if (fields.length != 4) continue;
            try {
                long timestamp = Long.parseLong(fields[0]);
                long duration = Long.parseLong(fields[2]);
                if (timestamp > 0 && duration >= 0) {
                    addEntry(entries, new Entry(timestamp, Phase.valueOf(fields[1]), duration, Result.valueOf(fields[3])));
                }
            } catch (IllegalArgumentException ignored) {
                // Ignore damaged entries and values outside the diagnostic enums.
            }
        }
        return entries;
    }

    private static void addEntry(ArrayDeque<Entry> entries, Entry entry) {
        if (entries.size() == MAX_ENTRIES) entries.removeFirst();
        entries.addLast(entry);
    }

    static String encode(Iterable<Entry> entries) {
        StringJoiner history = new StringJoiner("\n");
        for (Entry entry : entries) {
            history.add(entry.timestamp() + "|" + entry.phase() + "|" + entry.duration() + "|" + entry.result());
        }
        return history.toString();
    }

    static String format(Iterable<Entry> entries) {
        StringJoiner summary = new StringJoiner("\n");
        for (Entry entry : entries) {
            summary.add(Instant.ofEpochMilli(entry.timestamp()) + " · " + entry.phase() + " · "
                    + entry.duration() + " ms · " + entry.result());
        }
        return summary.toString();
    }
}
