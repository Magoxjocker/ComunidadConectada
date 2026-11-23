package com.example.comunidadconectada.domain;

import org.junit.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.Assert.*;

public final class InputValidationTest {
    @Test public void requiresANonblankNoteOnlyWhenResolvingReports() {
        assertFalse(InputValidation.validReportResolution("resolved", " \t\u00a0\n"));
        assertFalse(InputValidation.validReportResolution("resolved", null));
        assertTrue(InputValidation.validReportResolution("resolved", "Luminaria reparada"));
        assertTrue(InputValidation.validReportResolution("working", ""));
    }

    @Test public void foldsAccentsAndEnyeInSearch() {
        assertEquals("nandu", InputValidation.searchKey("  Ñandú  "));
        assertEquals("arbol", InputValidation.searchKey("ÁRBOL"));
        assertEquals("", InputValidation.clean(null));
        assertEquals("", InputValidation.clean("\u00a0\t"));
    }

    @Test public void normalizesEmailButPreservesPasswordSemantics() {
        String email = InputValidation.email(" VECINA@Comunidad.CL ");
        assertEquals("vecina@comunidad.cl", email);
        assertTrue(InputValidation.validEmail(email));
        assertFalse(InputValidation.validEmail("vecina sin@comunidad.cl"));
        assertTrue(InputValidation.strongPassword("Bicicleta12"));
        assertFalse(InputValidation.strongPassword(" Bicicleta12"));
        assertFalse(InputValidation.strongPassword("SoloLetras"));
    }

    @Test public void rejectsDuplicatePollChoicesAndKeepsOriginalText() {
        assertEquals(List.of("Árbol", "Iluminación"), InputValidation.pollOptions("  Árbol\n\nIluminación "));
        assertTrue(InputValidation.pollOptions("Ñandú\nNANDU").isEmpty());
        assertTrue(InputValidation.pollOptions("Una sola").isEmpty());
    }

    @Test public void parsesFutureChileanDatesAndRejectsInvalidDates() {
        long now = Instant.parse("2026-11-20T12:00:00Z").toEpochMilli();
        assertEquals(Long.valueOf(Instant.parse("2026-11-21T15:30:00Z").toEpochMilli()),
                InputValidation.closeDate("21/11/2026 12:30", now));
        assertNull(InputValidation.closeDate("31/11/2026 12:30", now));
        assertNull(InputValidation.closeDate("19/11/2026 12:30", now));
    }
}
