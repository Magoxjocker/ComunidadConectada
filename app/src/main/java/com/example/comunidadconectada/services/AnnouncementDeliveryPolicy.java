package com.example.comunidadconectada.services;

import com.example.comunidadconectada.services.FcmDiagnostics.Result;

import static com.example.comunidadconectada.services.FcmDiagnostics.Result.*;

final class AnnouncementDeliveryPolicy {
    private static final int MAX_ATTEMPTS = 3;

    private AnnouncementDeliveryPolicy() {}

    static boolean important(String priority) {
        return "important".equals(priority) || "urgent".equals(priority);
    }

    static Result membership(boolean exists, Boolean active) {
        if (!exists) return MEMBERSHIP_MISSING;
        return Boolean.TRUE.equals(active) ? OK : MEMBERSHIP_INACTIVE;
    }

    static Result announcement(boolean exists, String status) {
        if (!exists) return ANNOUNCEMENT_MISSING;
        return "published".equals(status) ? OK : NOT_PUBLISHED;
    }

    static Result readFailure(Throwable failure) {
        String reason = failure == null ? null : failure.getMessage();
        return "Permission denied".equalsIgnoreCase(reason)
                || "Firebase Database error: Permission denied".equalsIgnoreCase(reason)
                ? PERMISSION_DENIED : READ_FAILED;
    }

    static boolean retry(Result result, int attempt, long now, long expiry) {
        return now < expiry && attempt < MAX_ATTEMPTS - 1 && (result == TIMEOUT || result == READ_FAILED);
    }
}
