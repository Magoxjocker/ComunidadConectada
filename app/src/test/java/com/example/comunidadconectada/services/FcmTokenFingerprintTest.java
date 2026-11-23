package com.example.comunidadconectada.services;

import com.example.comunidadconectada.data.FcmTokenFingerprint;
import org.junit.Test;
import static org.junit.Assert.*;

public final class FcmTokenFingerprintTest {
    @Test public void fingerprintMatchesStoredSha256Format() {
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
                FcmTokenFingerprint.sha256("abc"));
    }

    @Test public void identicalTokenIsRejectedAndDifferentTokenIsAccepted() {
        String rejected = FcmTokenFingerprint.sha256("rejected-fixture");
        assertFalse(FcmTokenFingerprint.renewed(rejected, "rejected-fixture"));
        assertTrue(FcmTokenFingerprint.renewed(rejected, "replacement-fixture"));
    }

    @Test public void missingInputsCannotConfirmRenewal() {
        assertFalse(FcmTokenFingerprint.renewed("invalid", "replacement-fixture"));
        assertFalse(FcmTokenFingerprint.renewed(FcmTokenFingerprint.sha256("old"), ""));
    }
}
