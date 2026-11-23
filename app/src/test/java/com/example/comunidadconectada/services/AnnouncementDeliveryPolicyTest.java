package com.example.comunidadconectada.services;

import static com.example.comunidadconectada.services.FcmDiagnostics.Result.*;
import static org.junit.Assert.*;

import org.junit.Test;

public final class AnnouncementDeliveryPolicyTest {
    @Test public void normalAndLegacyAnnouncementsUseTheStandardChannel() {
        assertFalse(AnnouncementDeliveryPolicy.important("info"));
        assertFalse(AnnouncementDeliveryPolicy.important(null));
        assertTrue(AnnouncementDeliveryPolicy.important("important"));
        assertTrue(AnnouncementDeliveryPolicy.important("urgent"));
    }

    @Test public void activeMemberAndPublishedAnnouncementAreAccepted() {
        assertEquals(OK, AnnouncementDeliveryPolicy.membership(true, true));
        assertEquals(OK, AnnouncementDeliveryPolicy.announcement(true, "published"));
        assertFalse(AnnouncementDeliveryPolicy.retry(OK, 0, 100, 200));
    }

    @Test public void rejectedValidationsAreTerminal() {
        assertEquals(MEMBERSHIP_INACTIVE, AnnouncementDeliveryPolicy.membership(true, false));
        assertEquals(MEMBERSHIP_MISSING, AnnouncementDeliveryPolicy.membership(false, null));
        assertEquals(NOT_PUBLISHED, AnnouncementDeliveryPolicy.announcement(true, "draft"));
        assertEquals(ANNOUNCEMENT_MISSING, AnnouncementDeliveryPolicy.announcement(false, null));
        assertFalse(AnnouncementDeliveryPolicy.retry(NOT_PUBLISHED, 0, 100, 200));
        assertFalse(AnnouncementDeliveryPolicy.retry(MEMBERSHIP_INACTIVE, 0, 100, 200));
    }

    @Test public void timeoutsHaveTwoRetriesWithinTheDeadline() {
        assertTrue(AnnouncementDeliveryPolicy.retry(TIMEOUT, 0, 100, 200));
        assertTrue(AnnouncementDeliveryPolicy.retry(TIMEOUT, 1, 100, 200));
        assertFalse(AnnouncementDeliveryPolicy.retry(TIMEOUT, 2, 100, 200));
        assertFalse(AnnouncementDeliveryPolicy.retry(TIMEOUT, 0, 200, 200));
    }

    @Test public void firebasePermissionDenialIsTerminalWhileOtherReadErrorsAreBounded() {
        assertEquals(PERMISSION_DENIED, AnnouncementDeliveryPolicy.readFailure(new Exception("Permission denied")));
        assertEquals(READ_FAILED, AnnouncementDeliveryPolicy.readFailure(new Exception("Disconnected")));
        assertFalse(AnnouncementDeliveryPolicy.retry(PERMISSION_DENIED, 0, 100, 200));
        assertTrue(AnnouncementDeliveryPolicy.retry(READ_FAILED, 0, 100, 200));
        assertFalse(AnnouncementDeliveryPolicy.retry(READ_FAILED, 2, 100, 200));
    }
}
