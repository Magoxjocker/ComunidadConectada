package com.example.comunidadconectada.services;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.example.comunidadconectada.services.FcmDiagnostics.Entry;
import com.example.comunidadconectada.services.FcmDiagnostics.Phase;
import com.example.comunidadconectada.services.FcmDiagnostics.Result;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public final class FcmDiagnosticsTest {
    @Test public void historyKeepsOnlyTheLatestEntries() {
        List<Entry> history = new ArrayList<>();
        for (int timestamp = 1; timestamp <= FcmDiagnostics.MAX_ENTRIES + 3; timestamp++) {
            history.add(new Entry(timestamp, Phase.RECEIVED, 0, Result.OK));
        }

        var retained = FcmDiagnostics.readEntries(FcmDiagnostics.encode(history));

        assertEquals(FcmDiagnostics.MAX_ENTRIES, retained.size());
        assertEquals(4, retained.getFirst().timestamp());
        assertEquals(FcmDiagnostics.MAX_ENTRIES + 3, retained.getLast().timestamp());
    }

    @Test public void exportedHistoryIncludesOnlyFourValidatedFields() {
        String stored = "1|RECEIVED|2|OK\n2|RECEIVED|3|private-text\n3|RECEIVED|-1|OK\n4|UNKNOWN|1|OK";

        assertEquals("1970-01-01T00:00:00.001Z · RECEIVED · 2 ms · OK",
                FcmDiagnostics.format(FcmDiagnostics.readEntries(stored)));
    }

    @Test public void captureExpiresAfterOneDay() {
        long start = 100;
        long expiresAt = start + TimeUnit.HOURS.toMillis(24);

        assertFalse(FcmDiagnostics.windowOpen(0, start));
        assertFalse(FcmDiagnostics.windowOpen(start, start - 1));
        assertTrue(FcmDiagnostics.windowOpen(start, expiresAt - 1));
        assertFalse(FcmDiagnostics.windowOpen(start, expiresAt));
    }
}
