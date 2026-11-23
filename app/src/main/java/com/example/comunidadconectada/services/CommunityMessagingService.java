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

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

import com.example.comunidadconectada.BuildConfig;
import com.example.comunidadconectada.MainActivity;
import com.example.comunidadconectada.R;
import com.example.comunidadconectada.data.CommunityRepository;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public final class CommunityMessagingService extends FirebaseMessagingService {
    public static final String COMMUNITY = "community";
    public static final String RECORD = "record";
    private static final String CHANNEL = "announcements";
    private static final String UID = "uid";
    private static final String TOKEN = "fcm_token";
    private static final String TOKEN_SYNC = "fcm_token_sync";
    private static final long TOKEN_REFRESH_INTERVAL = TimeUnit.HOURS.toMillis(12);

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
        NotificationChannel channel = new NotificationChannel(CHANNEL,
                context.getString(R.string.notification_channel), NotificationManager.IMPORTANCE_DEFAULT);
        channel.setDescription(context.getString(R.string.notification_channel_description));
        context.getSystemService(NotificationManager.class).createNotificationChannel(channel);
    }

    public static void synchronize(Context context) {
        if (BuildConfig.USE_FIREBASE_EMULATORS) return;
        Context application = context.getApplicationContext();
        if (!allowed(application)) {
            String known = preferences(application).getString(TOKEN, "");
            if (!known.isEmpty()) register(application, known, false);
            return;
        }
        FirebaseMessaging.getInstance().getToken().addOnSuccessListener(token -> register(application, token, true));
    }

    @Override public void onNewToken(@NonNull String token) {
        if (allowed(this)) register(this, token, true);
    }

    @Override public void onMessageReceived(@NonNull RemoteMessage message) {
        Map<String, String> payload = message.getData();
        String uid = payload.get(UID);
        String community = payload.get(COMMUNITY);
        String id = payload.get("id");
        CommunityRepository repository = CommunityRepository.configured(this);
        if (repository == null || !allowed(this) || !matches(this, repository, uid, community)
                || id == null || !id.matches("[A-Za-z0-9_-]{1,170}")) return;
        DataSnapshot announcement = readAnnouncement(repository, uid, community, id);
        if (announcement == null || !matches(this, repository, uid, community)) return;
        String title = announcement.child("title").getValue(String.class);
        String description = announcement.child("description").getValue(String.class);
        if (title == null || description == null) return;
        int previewLength = Math.min(160, description.codePointCount(0, description.length()));
        String summary = description.substring(0, description.offsetByCodePoints(0, previewLength));
        showAnnouncement(community, id, title, summary);
    }

    private void showAnnouncement(String community, String id, String title, String summary) {
        createChannel(this);
        Intent intent = new Intent(this, MainActivity.class).setAction(Intent.ACTION_VIEW)
                .setData(new Uri.Builder().scheme("comunidad").authority("anuncios").appendPath(community).appendPath(id).build())
                .putExtra(COMMUNITY, community).putExtra(RECORD, id)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent open = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        NotificationCompat.Builder notification = new NotificationCompat.Builder(this, CHANNEL)
                .setSmallIcon(R.drawable.ic_announce).setContentTitle(title).setContentText(summary)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(summary)).setContentIntent(open)
                .setVisibility(NotificationCompat.VISIBILITY_PRIVATE).setAutoCancel(true).setOnlyAlertOnce(true);
        getSystemService(NotificationManager.class).notify(community + "/" + id, 1, notification.build());
    }

    private static void register(Context context, String token, boolean enabled) {
        CommunityRepository repository = CommunityRepository.configured(context);
        if (repository == null) return;
        SharedPreferences preferences = preferences(context);
        String uid = preferences.getString(UID, "");
        String community = preferences.getString(COMMUNITY, "");
        if (!matches(context, repository, uid, community)) return;
        long now = System.currentTimeMillis();
        String previous = preferences.getString(TOKEN, "");
        if (enabled && token.equals(previous) && now - preferences.getLong(TOKEN_SYNC, 0L) < TOKEN_REFRESH_INTERVAL) return;
        repository.action("notificationDevice", Map.of("token", token, "enabled", enabled)).addOnSuccessListener(result -> {
            if (!matches(context, repository, uid, community)) return;
            if (enabled) {
                preferences.edit().putString(TOKEN, token).putLong(TOKEN_SYNC, now).apply();
                if (!previous.isEmpty() && !previous.equals(token)) {
                    repository.action("notificationDevice", Map.of("token", previous, "enabled", false));
                }
            } else preferences.edit().remove(TOKEN).remove(TOKEN_SYNC).apply();
        });
    }

    private static DataSnapshot readAnnouncement(CommunityRepository repository, String uid, String community, String id) {
        try {
            var scopedCommunity = repository.community(community);
            var membership = Tasks.await(scopedCommunity.child("members").child(uid).child("active").get(),
                    2, TimeUnit.SECONDS);
            if (!Boolean.TRUE.equals(membership.getValue(Boolean.class))) return null;
            var announcement = Tasks.await(scopedCommunity.child("announcements").child(id).get(), 2, TimeUnit.SECONDS);
            return "published".equals(announcement.child("status").getValue(String.class)) ? announcement : null;
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return null;
        } catch (ExecutionException | TimeoutException unavailable) {
            return null;
        }
    }

    private static boolean matches(Context context, CommunityRepository repository, String uid, String community) {
        FirebaseUser user = repository.auth().getCurrentUser();
        return user != null && uid != null && user.getUid().equals(uid) && community != null && !community.isEmpty()
                && uid.equals(preferences(context).getString(UID, ""))
                && community.equals(preferences(context).getString(COMMUNITY, ""));
    }

    private static SharedPreferences preferences(Context context) {
        return context.getSharedPreferences("notification_scope", Context.MODE_PRIVATE);
    }
}
