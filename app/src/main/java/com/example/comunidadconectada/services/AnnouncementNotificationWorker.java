package com.example.comunidadconectada.services;

import android.content.Context;
import android.os.Build;
import android.os.SystemClock;

import androidx.annotation.NonNull;
import androidx.work.BackoffPolicy;
import androidx.work.Constraints;
import androidx.work.Data;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.OutOfQuotaPolicy;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.example.comunidadconectada.data.CommunityRepository;
import com.example.comunidadconectada.services.FcmDiagnostics.Phase;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.messaging.RemoteMessage;

import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;

import static com.example.comunidadconectada.services.FcmDiagnostics.Result.*;

public final class AnnouncementNotificationWorker extends Worker {
    private static final String UID = "uid";
    private static final String EXPIRY = "expiry";
    private static final String DELIVERED = "fcm_delivered";
    private static final int MAX_RECEIPTS = 100;
    private static final long READ_TIMEOUT_SECONDS = 8;
    private static final long BACKOFF_SECONDS = 10;
    private static final long DEFAULT_TTL_MS = TimeUnit.MINUTES.toMillis(5);
    private static final long MAX_TTL_MS = TimeUnit.DAYS.toMillis(1);

    public AnnouncementNotificationWorker(@NonNull Context context, @NonNull WorkerParameters parameters) {
        super(context, parameters);
    }

    static void enqueue(Context context, String uid, String community, String id, RemoteMessage message) {
        long now = System.currentTimeMillis();
        long sentAt = message.getSentTime() > 0 ? Math.min(now, message.getSentTime()) : now;
        long ttl = message.getTtl() > 0 ? Math.min(MAX_TTL_MS, TimeUnit.SECONDS.toMillis(message.getTtl())) : DEFAULT_TTL_MS;
        var request = new OneTimeWorkRequest.Builder(AnnouncementNotificationWorker.class)
                .setInputData(new Data.Builder().putString(UID, uid)
                        .putString(CommunityMessagingService.COMMUNITY, community)
                        .putString(CommunityMessagingService.RECORD, id).putLong(EXPIRY, sentAt + ttl).build())
                .setConstraints(new Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_SECONDS, TimeUnit.SECONDS);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && message.getPriority() == RemoteMessage.PRIORITY_HIGH) {
            request.setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST);
        }
        WorkManager.getInstance(context).enqueueUniqueWork(key(uid, community, id), ExistingWorkPolicy.KEEP, request.build());
    }

    @NonNull @Override public Result doWork() {
        Context context = getApplicationContext();
        var diagnostic = FcmDiagnostics.capture(context);
        long startedAt = SystemClock.elapsedRealtime();
        String uid = getInputData().getString(UID);
        String community = getInputData().getString(CommunityMessagingService.COMMUNITY);
        String id = getInputData().getString(CommunityMessagingService.RECORD);
        long expiry = getInputData().getLong(EXPIRY, 0);
        CommunityRepository repository = CommunityRepository.configured(context);
        var validation = expiry <= System.currentTimeMillis() ? EXPIRED
                : CommunityMessagingService.validateMessage(context, repository, uid, community, id);
        diagnostic.record(Phase.VALIDATION, startedAt, validation);
        if (validation != OK) return Result.success();

        String receipt = key(uid, community, id);
        var preferences = CommunityMessagingService.preferences(context);
        var delivered = new ArrayDeque<>(List.of(preferences.getString(DELIVERED, "").split(" ")));
        delivered.remove("");
        if (delivered.contains(receipt)) {
            diagnostic.record(Phase.NOTIFY, SystemClock.elapsedRealtime(), DUPLICATE);
            return Result.success();
        }
        DatabaseReference root = repository.community(community);
        Reading member = read(root.child("members").child(uid).child("active"), diagnostic, Phase.MEMBERSHIP_READ,
                snapshot -> AnnouncementDeliveryPolicy.membership(snapshot.exists(), snapshot.getValue(Boolean.class)));
        if (member.result() != OK) return decision(member, expiry);
        Reading announcement = read(root.child("announcements").child(id), diagnostic, Phase.ANNOUNCEMENT_READ,
                snapshot -> AnnouncementDeliveryPolicy.announcement(snapshot.exists(), snapshot.child("status").getValue(String.class)));
        if (announcement.result() != OK) return decision(announcement, expiry);
        if (!isStopped() && System.currentTimeMillis() < expiry && CommunityMessagingService.showValidatedAnnouncement(
                context, repository, uid, community, id, announcement.snapshot(), diagnostic)) {
            persistReceipt(preferences, receipt);
        }
        return Result.success();
    }

    private static void persistReceipt(android.content.SharedPreferences preferences, String receipt) {
        synchronized (AnnouncementNotificationWorker.class) {
            // Each successful job adds its own receipt without losing receipts written by concurrent jobs.
            var current = new ArrayDeque<>(List.of(preferences.getString(DELIVERED, "").split(" ")));
            current.remove("");
            if (current.contains(receipt)) return;
            while (current.size() >= MAX_RECEIPTS) current.removeFirst();
            current.addLast(receipt);
            preferences.edit().putString(DELIVERED, String.join(" ", current)).apply();
        }
    }

    private Result decision(Reading reading, long expiry) {
        return AnnouncementDeliveryPolicy.retry(reading.result(), getRunAttemptCount(), System.currentTimeMillis(), expiry)
                ? Result.retry() : Result.success();
    }

    private record Reading(DataSnapshot snapshot, FcmDiagnostics.Result result) {}

    private static Reading read(DatabaseReference reference, FcmDiagnostics diagnostic, Phase phase,
                                Function<DataSnapshot, FcmDiagnostics.Result> validate) {
        long startedAt = SystemClock.elapsedRealtime();
        DataSnapshot snapshot = null;
        FcmDiagnostics.Result result;
        try {
            snapshot = Tasks.await(reference.get(), READ_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            result = validate.apply(snapshot);
        } catch (TimeoutException timeout) {
            result = TIMEOUT;
        } catch (ExecutionException failure) {
            result = AnnouncementDeliveryPolicy.readFailure(failure.getCause());
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            result = INTERRUPTED;
        } catch (RuntimeException invalidSnapshot) {
            result = UNEXPECTED_ERROR;
        }
        diagnostic.record(phase, startedAt, result);
        return new Reading(snapshot, result);
    }

    private static String key(String uid, String community, String id) {
        return "announcement-" + UUID.nameUUIDFromBytes((uid + "/" + community + "/" + id).getBytes(StandardCharsets.UTF_8));
    }
}
