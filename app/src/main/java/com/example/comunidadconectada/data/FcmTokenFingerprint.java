package com.example.comunidadconectada.data;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public final class FcmTokenFingerprint {
    private FcmTokenFingerprint() {}

    public static String sha256(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte octet : digest) {
                hex.append(Character.forDigit((octet & 0xff) >>> 4, 16));
                hex.append(Character.forDigit(octet & 0x0f, 16));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 no está disponible.", exception);
        }
    }

    public static boolean valid(String fingerprint) {
        return fingerprint != null && fingerprint.matches("[a-f0-9]{64}");
    }

    public static boolean renewed(String rejected, String token) {
        return valid(rejected) && token != null && !token.isBlank() && !rejected.equals(sha256(token));
    }
}
