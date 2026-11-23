package com.example.comunidadconectada.services;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

import androidx.annotation.NonNull;
import androidx.annotation.MainThread;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

import com.example.comunidadconectada.BuildConfig;
import com.example.comunidadconectada.MainActivity;
import com.example.comunidadconectada.R;
import com.example.comunidadconectada.data.CommunityRepository;
import com.example.comunidadconectada.data.FcmTokenFingerprint;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.example.comunidadconectada.services.FcmDiagnostics.Phase;
import com.example.comunidadconectada.services.FcmDiagnostics.Result;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

import java.util.Map;
import java.util.concurrent.TimeUnit;

public final class CommunityMessagingService extends FirebaseMessagingService {
    public static final String COMMUNITY = "community";
    public static final String RECORD = "record";
    private static final String CHANNEL = "announcements";
    private static final String IMPORTANT_CHANNEL = "announcements_important";
    private static final String UID = "uid";
    private static final String TOKEN = "fcm_token";
    private static final String TOKEN_SYNC = "fcm_token_sync";
    private static final long TOKEN_REFRESH_INTERVAL = TimeUnit.HOURS.toMillis(12);
    private static final int ANNOUNCEMENT_NOTIFICATION_ID = 1;
    private static final long ACTIVE_CHECK_DELAY_MS = 500;
    // Registration state is accessed on the main thread, including Firebase task callbacks.
    private static boolean renewing;
    private static boolean writingRegistration;
    private static long registrationGeneration;

    public static void session(Context context, String uid, String community) {
        SharedPreferences preferences = preferences(context);
        if (!uid.equals(preferences.getString(UID, "")) || !community.equals(preferences.getString(COMMUNITY, ""))) {
            clearSession(context);
            preferences.edit().putString(UID, uid).putString(COMMUNITY, community).apply();
        }
    }

    public static void clearSession(Context context) {
        if (preferences(context).getString(UID, "").isEmpty()) return;
        preferences(context).edit().clear().apply();
        NotificationManagerCompat.from(context).cancelAll();
    }

    public static boolean allowed(Context context) {
        return (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU
                || ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED)
                && NotificationManagerCompat.from(context).areNotificationsEnabled();
    }

    public static void createChannel(Context context) {
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        createChannel(context, manager, CHANNEL, R.string.notification_channel,
                NotificationManager.IMPORTANCE_DEFAULT);
        createChannel(context, manager, IMPORTANT_CHANNEL, R.string.notification_channel_important,
                NotificationManager.IMPORTANCE_HIGH);
    }

    private static void createChannel(Context context, NotificationManager manager, String channelId,
                                      int name, int importance) {
        NotificationChannel channel = new NotificationChannel(channelId, context.getString(name), importance);
        channel.setDescription(context.getString(R.string.notification_channel_description));
        manager.createNotificationChannel(channel);
    }

    @MainThread public static void synchronize(Context context) {
        if (BuildConfig.USE_FIREBASE_EMULATORS || renewing) return;
        Context application = context.getApplicationContext();
        RegistrationScope scope = registrationScope(application);
        if (scope == null) return;
        FcmDiagnostics diagnostic = FcmDiagnostics.capture(application);
        if (!allowed(application)) {
            String known = preferences(application).getString(TOKEN, "");
            if (!known.isEmpty()) writeRegistration(application, scope, known, false, false, diagnostic);
            return;
        }
        long generation = registrationGeneration;
        long startedAt = SystemClock.elapsedRealtime();
        FirebaseMessaging.getInstance().getToken().addOnCompleteListener(task -> {
            diagnostic.record(Phase.TOKEN_GET, startedAt, task.isSuccessful() ? Result.OK : Result.TOKEN_FAILED);
            if (task.isSuccessful() && !renewing && generation == registrationGeneration) {
                writeRegistration(application, scope, task.getResult(), true, false, diagnostic);
            }
        });
    }

    @Override public void onNewToken(@NonNull String token) {
        Context application = getApplicationContext();
        new Handler(Looper.getMainLooper()).post(() -> {
            if (allowed(application)) synchronize(application);
        });
    }

    @MainThread public static Task<Result> renewRegistration(Context context, String rejectedFingerprint) {
        Context application = context.getApplicationContext();
        if (!FcmDiagnostics.snapshot(application).recording()) FcmDiagnostics.start(application);
        FcmDiagnostics diagnostic = FcmDiagnostics.capture(application);
        long startedAt = SystemClock.elapsedRealtime();
        RegistrationScope scope = registrationScope(application);
        Result validation = renewalValidation(application, scope, rejectedFingerprint);
        diagnostic.record(Phase.TOKEN_RENEWAL, startedAt, validation);
        if (validation != Result.OK) return Tasks.forResult(validation);
        renewing = true;
        registrationGeneration++;
        FirebaseMessaging messaging = FirebaseMessaging.getInstance();
        long deleteStartedAt = SystemClock.elapsedRealtime();
        return messaging.deleteToken().continueWithTask(deleted -> {
            diagnostic.record(Phase.TOKEN_DELETE, deleteStartedAt, deleted.isSuccessful() ? Result.OK : Result.TOKEN_FAILED);
            if (!deleted.isSuccessful()) return Tasks.forResult(Result.TOKEN_FAILED);
            return obtainReplacement(application, scope, rejectedFingerprint, messaging, diagnostic);
        }).continueWith(outcome -> {
            renewing = false;
            Result result = outcome.isSuccessful() ? outcome.getResult() : Result.UNEXPECTED_ERROR;
            diagnostic.record(Phase.TOKEN_RENEWAL, startedAt, result);
            return result;
        });
    }

    private static Result renewalValidation(Context context, RegistrationScope scope, String rejectedFingerprint) {
        if (renewing || writingRegistration) return Result.BUSY;
        if (!FcmTokenFingerprint.valid(rejectedFingerprint)) return Result.INVALID_HASH;
        if (BuildConfig.USE_FIREBASE_EMULATORS) return Result.REPOSITORY_UNAVAILABLE;
        if (scope == null) return Result.NO_SESSION;
        return allowed(context) ? Result.OK : Result.NOTIFICATIONS_DISABLED;
    }

    private static Task<Result> obtainReplacement(Context context, RegistrationScope scope, String rejectedFingerprint,
                                                  FirebaseMessaging messaging, FcmDiagnostics diagnostic) {
        long startedAt = SystemClock.elapsedRealtime();
        return messaging.getToken().continueWithTask(obtained -> {
            String token = obtained.isSuccessful() ? obtained.getResult() : null;
            Result retrieval = token != null && !token.isBlank() ? Result.OK : Result.TOKEN_FAILED;
            diagnostic.record(Phase.TOKEN_GET, startedAt, retrieval);
            if (retrieval != Result.OK) return Tasks.forResult(retrieval);
            long comparisonStartedAt = SystemClock.elapsedRealtime();
            Result comparison = FcmTokenFingerprint.renewed(rejectedFingerprint, token)
                    ? Result.OK : Result.TOKEN_UNCHANGED;
            diagnostic.record(Phase.TOKEN_COMPARE, comparisonStartedAt, comparison);
            if (comparison != Result.OK) return Tasks.forResult(comparison);
            return writeRegistration(context, scope, token, true, true, diagnostic);
        });
    }

    @Override public void onMessageReceived(@NonNull RemoteMessage message) {
        long receivedAt = SystemClock.elapsedRealtime();
        FcmDiagnostics diagnostic = FcmDiagnostics.capture(this);
        diagnostic.record(Phase.RECEIVED, receivedAt, Result.OK);
        long validationStartedAt = SystemClock.elapsedRealtime();
        Map<String, String> payload = message.getData();
        String uid = payload.get(UID);
        String community = payload.get(COMMUNITY);
        String id = payload.get("id");
        CommunityRepository repository = CommunityRepository.configured(this);
        Result validation = validateMessage(this, repository, uid, community, id);
        diagnostic.record(Phase.VALIDATION, validationStartedAt, validation);
        if (validation != Result.OK) return;
        AnnouncementNotificationWorker.enqueue(this, uid, community, id, message);
    }

    static Result validateMessage(Context context, CommunityRepository repository, String uid, String community, String id) {
        if (repository == null) return Result.REPOSITORY_UNAVAILABLE;
        if (!allowed(context)) return Result.NOTIFICATIONS_DISABLED;
        Result scope = sessionResult(context, repository, uid, community);
        if (scope != Result.OK) return scope;
        return id != null && id.matches("[A-Za-z0-9_-]{1,170}") ? Result.OK : Result.INVALID_ID;
    }

    static boolean showValidatedAnnouncement(Context context, CommunityRepository repository, String uid, String community, String id,
                                           DataSnapshot announcement, FcmDiagnostics diagnostic) {
        long startedAt = SystemClock.elapsedRealtime();
        Result scope = sessionResult(context, repository, uid, community);
        if (scope != Result.OK) {
            diagnostic.record(Phase.RECHECK, startedAt, scope);
            return false;
        }
        String title = announcement.child("title").getValue(String.class);
        String description = announcement.child("description").getValue(String.class);
        if (title == null || description == null) {
            diagnostic.record(Phase.RECHECK, startedAt, Result.CONTENT_MISSING);
            return false;
        }
        diagnostic.record(Phase.RECHECK, startedAt, Result.OK);
        int previewLength = Math.min(160, description.codePointCount(0, description.length()));
        String summary = description.substring(0, description.offsetByCodePoints(0, previewLength));
        if (!allowed(context)) return false;
        String channelId = AnnouncementDeliveryPolicy.important(announcement.child("priority").getValue(String.class))
                ? IMPORTANT_CHANNEL : CHANNEL;
        showAnnouncement(context, community, id, title, summary, channelId, diagnostic);
        return true;
    }

    private static void showAnnouncement(Context context, String community, String id, String title, String summary,
                                         String channelId, FcmDiagnostics diagnostic) {
        createChannel(context);
        Intent intent = new Intent(context, MainActivity.class).setAction(Intent.ACTION_VIEW)
                .setData(new Uri.Builder().scheme("comunidad").authority("anuncios").appendPath(community).appendPath(id).build())
                .putExtra(COMMUNITY, community).putExtra(RECORD, id)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent open = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        NotificationCompat.Builder notification = new NotificationCompat.Builder(context, channelId)
                .setSmallIcon(R.drawable.ic_announce).setContentTitle(title).setContentText(summary)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(summary)).setContentIntent(open)
                .setVisibility(NotificationCompat.VISIBILITY_PRIVATE).setAutoCancel(true).setOnlyAlertOnce(true);
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        String tag = community + "/" + id;
        long startedAt = SystemClock.elapsedRealtime();
        long postedAt = System.currentTimeMillis();
        try {
            manager.notify(tag, ANNOUNCEMENT_NOTIFICATION_ID, notification.build());
        } catch (RuntimeException failed) {
            diagnostic.record(Phase.NOTIFY, startedAt, Result.UNEXPECTED_ERROR);
            throw failed;
        }
        diagnostic.record(Phase.NOTIFY, startedAt, Result.OK);
        if (diagnostic.enabled()) {
            long checkStartedAt = SystemClock.elapsedRealtime();
            new Handler(Looper.getMainLooper()).postDelayed(() ->
                    diagnostic.record(Phase.ACTIVE_CHECK, checkStartedAt, activeResult(manager, tag, channelId, postedAt)),
                    ACTIVE_CHECK_DELAY_MS);
        }
    }

    private static Result activeResult(NotificationManager manager, String tag, String channelId, long postedAt) {
        try {
            for (var active : manager.getActiveNotifications()) {
                if (active.getId() == ANNOUNCEMENT_NOTIFICATION_ID && tag.equals(active.getTag())
                        && active.getPostTime() >= postedAt) return Result.ACTIVE;
            }
            if (!manager.areNotificationsEnabled()) return Result.NOTIFICATIONS_DISABLED;
            NotificationChannel channel = manager.getNotificationChannel(channelId);
            if (channel == null) return Result.CHANNEL_MISSING;
            return channel.getImportance() == NotificationManager.IMPORTANCE_NONE
                    ? Result.CHANNEL_BLOCKED : Result.NOT_ACTIVE;
        } catch (RuntimeException unavailable) {
            return Result.CHECK_FAILED;
        }
    }

    private record RegistrationScope(CommunityRepository repository, String uid, String community) {
        Result check(Context context) {
            return sessionResult(context, repository, uid, community);
        }

        Task<Map<?, ?>> write(String token, boolean enabled) {
            return repository.action("notificationDevice", Map.of("token", token, "enabled", enabled,
                    "expectedUid", uid, "expectedCommunity", community));
        }
    }

    private static RegistrationScope registrationScope(Context context) {
        CommunityRepository repository = CommunityRepository.configured(context);
        if (repository == null) return null;
        SharedPreferences preferences = preferences(context);
        var scope = new RegistrationScope(repository, preferences.getString(UID, ""), preferences.getString(COMMUNITY, ""));
        return scope.check(context) == Result.OK ? scope : null;
    }

    private static Task<Result> writeRegistration(Context context, RegistrationScope scope, String token, boolean enabled,
                                                  boolean renewal, FcmDiagnostics diagnostic) {
        long startedAt = SystemClock.elapsedRealtime();
        Result validation = scope.check(context);
        if (validation == Result.OK && enabled && !allowed(context)) validation = Result.NOTIFICATIONS_DISABLED;
        if (validation == Result.OK && writingRegistration) validation = Result.BUSY;
        if (validation == Result.OK && (token == null || token.isBlank())) validation = Result.TOKEN_FAILED;
        if (validation != Result.OK) {
            diagnostic.record(Phase.TOKEN_REGISTER, startedAt, validation);
            return Tasks.forResult(validation);
        }
        SharedPreferences preferences = preferences(context);
        String previous = preferences.getString(TOKEN, "");
        long elapsedSinceSync = System.currentTimeMillis() - preferences.getLong(TOKEN_SYNC, 0L);
        if (!renewal && enabled && token.equals(previous) && elapsedSinceSync < TOKEN_REFRESH_INTERVAL) {
            return Tasks.forResult(Result.OK);
        }
        writingRegistration = true;
        return scope.write(token, enabled).continueWithTask(written -> {
            writingRegistration = false;
            Result result = written.isSuccessful() ? scope.check(context) : Result.WRITE_FAILED;
            diagnostic.record(Phase.TOKEN_REGISTER, startedAt, result);
            if (result != Result.OK) return Tasks.forResult(result);
            if (enabled) {
                preferences.edit().putString(TOKEN, token).putLong(TOKEN_SYNC, System.currentTimeMillis()).apply();
                if (!renewal && !previous.isEmpty() && !previous.equals(token)) {
                    return writeCleanup(scope, previous, diagnostic);
                }
            } else preferences.edit().remove(TOKEN).remove(TOKEN_SYNC).apply();
            return Tasks.forResult(Result.OK);
        });
    }

    private static Task<Result> writeCleanup(RegistrationScope scope, String previous, FcmDiagnostics diagnostic) {
        writingRegistration = true;
        long startedAt = SystemClock.elapsedRealtime();
        return scope.write(previous, false).continueWith(cleaned -> {
            writingRegistration = false;
            diagnostic.record(Phase.TOKEN_REGISTER, startedAt, cleaned.isSuccessful() ? Result.OK : Result.WRITE_FAILED);
            return Result.OK;
        });
    }

    private static boolean matches(Context context, CommunityRepository repository, String uid, String community) {
        return sessionResult(context, repository, uid, community) == Result.OK;
    }

    private static Result sessionResult(Context context, CommunityRepository repository, String uid, String community) {
        FirebaseUser user = repository.auth().getCurrentUser();
        if (user == null) return Result.NO_SESSION;
        if (uid == null || !user.getUid().equals(uid)) return Result.UID_MISMATCH;
        if (community == null || community.isEmpty()) return Result.COMMUNITY_MISMATCH;
        SharedPreferences scope = preferences(context);
        if (!uid.equals(scope.getString(UID, ""))) return Result.UID_MISMATCH;
        return community.equals(scope.getString(COMMUNITY, "")) ? Result.OK : Result.COMMUNITY_MISMATCH;
    }

    static SharedPreferences preferences(Context context) {
        return context.getSharedPreferences("notification_scope", Context.MODE_PRIVATE);
    }
}
