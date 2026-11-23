package com.example.comunidadconectada.domain;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;

public final class PollMathTest {
    @Test public void calculatesPercentagesAndZeroVotes() {
        assertEquals(List.of(0.25, 0.75), PollMath.shares(List.of(1L, 3L)));
        assertEquals(List.of(0.0, 0.0), PollMath.shares(List.of(0L, 0L)));
        assertEquals(List.of(0.0, 1.0), PollMath.shares(List.of(-1L, 4L)));
    }
}
