package com.example.comunidadconectada.domain;

import java.util.ArrayList;
import java.util.List;

public final class PollMath {
    private PollMath() {}

    public static List<Double> shares(List<?> counts) {
        long total = 0;
        for (Object count : counts) total += asCount(count);
        List<Double> shares = new ArrayList<>();
        for (Object count : counts) shares.add(total == 0 ? 0.0 : (double) asCount(count) / total);
        return shares;
    }

    public static long asCount(Object value) {
        return value instanceof Number count ? Math.max(0, count.longValue()) : 0L;
    }
}
