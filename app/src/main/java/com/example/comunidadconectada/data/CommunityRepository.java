package com.example.comunidadconectada.data;

import android.content.Context;

import androidx.annotation.NonNull;

import com.google.android.gms.tasks.Task;
import com.example.comunidadconectada.BuildConfig;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.Query;
import com.google.firebase.database.ValueEventListener;
import com.google.android.gms.tasks.Tasks;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public final class CommunityRepository {
    public static final int PAGE_SIZE = 50;
    private static final String EMULATOR_HOST = "127.0.0.1";
    private static CommunityRepository instance;

    private final FirebaseAuth authentication = FirebaseAuth.getInstance();
    private final FirebaseDatabase database = FirebaseDatabase.getInstance();
    private final SparkCommands commands;

    private CommunityRepository() {
        if (BuildConfig.USE_FIREBASE_EMULATORS) {
            authentication.useEmulator(EMULATOR_HOST, 9099);
            database.useEmulator(EMULATOR_HOST, 9000);
        }
        commands = new SparkCommands(database.getReference(), authentication);
    }

    public static boolean hasDefaultFirebaseApp(Context context) {
        return FirebaseApp.getApps(context).stream()
                .anyMatch(app -> FirebaseApp.DEFAULT_APP_NAME.equals(app.getName()));
    }

    public static synchronized CommunityRepository configured(Context context) {
        if (instance != null) return instance;
        if (BuildConfig.USE_FIREBASE_EMULATORS && !hasDefaultFirebaseApp(context)) {
            FirebaseApp.initializeApp(context.getApplicationContext(), new FirebaseOptions.Builder()
                    .setProjectId("demo-comunidad")
                    .setApplicationId("1:123:android:0000000000000000")
                    .setApiKey("AIza00000000000000000000000000000000000")
                    .setDatabaseUrl("https://demo-comunidad-default-rtdb.firebaseio.com")
                    .build());
        }
        if (!hasDefaultFirebaseApp(context)) return null;
        String databaseUrl = FirebaseApp.getInstance().getOptions().getDatabaseUrl();
        if (databaseUrl == null || databaseUrl.trim().isEmpty()) return null;
        instance = new CommunityRepository();
        return instance;
    }

    public FirebaseAuth auth() {
        return authentication;
    }

    public Task<Map<?, ?>> action(String action, Map<String, Object> fields) {
        return commands.execute(action, fields);
    }

    public Task<Map<?, ?>> mqttCredentials() {
        var user = authentication.getCurrentUser();
        if (user == null) return Tasks.forException(
                new OperationFailure(DatabaseError.PERMISSION_DENIED, null));
        return database.getReference("mqttAccess").child(user.getUid()).get().onSuccessTask(snapshot ->
                Tasks.forResult(snapshot.getValue() instanceof Map<?, ?> fields ? fields : Map.of()));
    }

    public static final class OperationFailure extends Exception {
        private final int code;

        OperationFailure(DatabaseError error) {
            this(error.getCode(), error.getMessage());
        }

        OperationFailure(int code, String detail) {
            super(detail);
            this.code = code;
        }

        public int code() {
            return code;
        }
    }

    public DatabaseReference account(String uid) {
        return database.getReference("accounts").child(uid);
    }

    public DatabaseReference community(String community) {
        return database.getReference("communityData").child(community);
    }

    public Query connected() {
        return database.getReference(".info/connected");
    }

    public static Query latest(DatabaseReference collection) {
        return collection.orderByChild("createdAt").limitToLast(PAGE_SIZE);
    }

    public static Subscription watch(Query query, Consumer<DataSnapshot> result, Consumer<DatabaseError> failure) {
        ValueEventListener listener = new ValueEventListener() {
            @Override public void onDataChange(@NonNull DataSnapshot snapshot) {
                result.accept(snapshot);
            }

            @Override public void onCancelled(@NonNull DatabaseError error) {
                failure.accept(error);
            }
        };
        query.addValueEventListener(listener);
        return () -> query.removeEventListener(listener);
    }

    public static List<CommunityRecord> records(DataSnapshot snapshot) {
        List<CommunityRecord> records = new ArrayList<>();
        for (DataSnapshot child : snapshot.getChildren()) records.add(CommunityRecord.from(child));
        return records;
    }

    public interface Subscription {
        void close();
    }
}
