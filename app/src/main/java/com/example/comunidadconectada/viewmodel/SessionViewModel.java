package com.example.comunidadconectada.viewmodel;

import android.app.Application;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.comunidadconectada.R;
import com.example.comunidadconectada.data.CommunityRecord;
import com.example.comunidadconectada.data.CommunityRepository;
import com.example.comunidadconectada.ui.Errors;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthInvalidUserException;
import com.google.firebase.auth.FirebaseUser;

import java.util.Map;

public final class SessionViewModel extends AndroidViewModel {
    public enum Stage { CONFIGURATION, LOADING, SIGN_IN, VERIFY, PROFILE, PENDING, SUSPENDED, ACTIVE, ERROR }

    public record Session(Stage stage, String uid, String name, String unit, String email,
                          String community, boolean administrator) {
        static Session at(Stage stage) {
            return new Session(stage, "", "", "", "", "", false);
        }
    }

    private static final long SESSION_TIMEOUT_MILLIS = 15_000L;

    private final CommunityRepository repository;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable timeout = this::onValidationTimeout;
    private final MutableLiveData<Session> session = new MutableLiveData<>(Session.at(Stage.LOADING));
    private final MutableLiveData<Integer> feedback = new MutableLiveData<>();
    private final MutableLiveData<Boolean> working = new MutableLiveData<>(false);
    private final FirebaseAuth.AuthStateListener listener = auth -> refresh(true);
    private CommunityRepository.Subscription accountWatcher;
    private CommunityRepository.Subscription memberWatcher;
    private String draftUid;
    private String draftName;
    private String draftUnit;
    private int refreshGeneration;

    public SessionViewModel(@NonNull Application application) {
        super(application);
        repository = CommunityRepository.configured(application);
        if (repository == null) session.setValue(Session.at(Stage.CONFIGURATION));
        else repository.auth().addAuthStateListener(listener);
    }

    public LiveData<Session> session() { return session; }
    public LiveData<Integer> feedback() { return feedback; }
    public LiveData<Boolean> working() { return working; }
    public CommunityRepository repository() { return repository; }

    public int configurationMessage() {
        return CommunityRepository.hasDefaultFirebaseApp(getApplication())
                ? R.string.config_database_message : R.string.config_message;
    }

    public void signIn(String email, String password) {
        if (!begin()) return;
        repository.auth().signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(result -> finish(result, R.string.login_error, 0));
    }

    public void register(String name, String unit, String email, String password) {
        if (!begin()) return;
        repository.auth().createUserWithEmailAndPassword(email, password).addOnCompleteListener(result -> {
            FirebaseUser user = repository.auth().getCurrentUser();
            if (!result.isSuccessful() || user == null) {
                finish(result, R.string.error_register, 0);
                return;
            }
            draftUid = user.getUid();
            draftName = name;
            draftUnit = unit;
            refresh(false);
            repository.action("registerProfile", Map.of("name", name, "unit", unit))
                    .addOnCompleteListener(profile -> sendRegistrationVerification(user, profile));
        });
    }

    private void sendRegistrationVerification(FirebaseUser user, Task<?> profile) {
        if (profile.isSuccessful()) clearDraft();
        user.sendEmailVerification().addOnCompleteListener(sent -> finish(sent, R.string.generic_error,
                profile.isSuccessful() ? R.string.link_sent : R.string.registration_profile_retry));
    }

    public void resetPassword(String email) {
        if (!begin()) return;
        repository.auth().sendPasswordResetEmail(email).addOnCompleteListener(result -> {
            boolean accepted = result.isSuccessful() || result.getException() instanceof FirebaseAuthInvalidUserException;
            feedback.setValue(accepted ? R.string.reset_sent : Errors.message(result.getException(), R.string.generic_error));
            working.setValue(false);
        });
    }

    public void resendVerification() {
        if (repository == null || repository.auth().getCurrentUser() == null || !begin()) return;
        repository.auth().getCurrentUser().sendEmailVerification()
                .addOnCompleteListener(result -> finish(result, R.string.generic_error, R.string.link_sent));
    }

    public void completeProfile(String name, String unit) {
        if (!begin()) return;
        repository.action("registerProfile", Map.of("name", name, "unit", unit)).addOnCompleteListener(result -> {
            finish(result, R.string.generic_error, 0);
            if (result.isSuccessful()) {
                clearDraft();
                refresh(false);
            }
        });
    }

    public void requestMembership(String community) {
        if (!begin()) return;
        repository.action("requestMembership", Map.of("community", community))
                .addOnCompleteListener(result -> finish(result, R.string.membership_request_error, R.string.membership_requested));
    }

    public void refresh(boolean forceToken) {
        if (repository == null) return;
        int generation = ++refreshGeneration;
        handler.removeCallbacks(timeout);
        closeWatchers();
        FirebaseUser user = repository.auth().getCurrentUser();
        if (user == null) {
            show(Session.at(Stage.SIGN_IN));
            return;
        }
        boolean wasVerifying = session.getValue().stage() == Stage.VERIFY;
        feedback.setValue(null);
        show(Session.at(Stage.LOADING));
        handler.postDelayed(timeout, SESSION_TIMEOUT_MILLIS);
        if (!forceToken) {
            loadUser(user, generation, false, wasVerifying);
            return;
        }
        user.reload().addOnCompleteListener(result -> {
            if (generation != refreshGeneration) return;
            if (result.isSuccessful()) loadUser(user, generation, true, wasVerifying);
            else reportSessionError(result.getException());
        });
    }

    private void loadUser(FirebaseUser user, int generation, boolean forceToken, boolean wasVerifying) {
        if (!user.isEmailVerified()) {
            show(new Session(Stage.VERIFY, user.getUid(), "", "", user.getEmail(), "", false));
            if (forceToken && wasVerifying) feedback.setValue(R.string.wrong_verify);
            return;
        }
        user.getIdToken(forceToken).addOnCompleteListener(result -> {
            if (generation != refreshGeneration) return;
            if (!result.isSuccessful()) {
                reportSessionError(result.getException());
                return;
            }
            Map<String, Object> claims = result.getResult().getClaims();
            accountWatcher = CommunityRepository.watch(repository.account(user.getUid()), snapshot -> {
                if (generation == refreshGeneration) loadAccount(user, CommunityRecord.from(snapshot), claims, generation);
            }, error -> {
                if (generation == refreshGeneration) reportSessionError(error.toException());
            });
        });
    }

    private void loadAccount(FirebaseUser user, CommunityRecord account, Map<String, Object> claims, int generation) {
        if (memberWatcher != null) memberWatcher.close();
        if (account.fields().isEmpty()) {
            show(new Session(Stage.PROFILE, user.getUid(), "", "", user.getEmail(), "", false));
            if (user.getUid().equals(draftUid) && !Boolean.TRUE.equals(working.getValue())) {
                completeProfile(draftName, draftUnit);
            }
            return;
        }
        String community = account.text("community");
        if (community.isEmpty()) {
            show(sessionFor(Stage.PENDING, user, account, false));
            return;
        }
        boolean claimMatches = "admin".equals(claims.get("role")) && community.equals(claims.get("community"));
        memberWatcher = CommunityRepository.watch(repository.community(community).child("members").child(user.getUid()), snapshot -> {
            if (generation != refreshGeneration) return;
            CommunityRecord member = CommunityRecord.from(snapshot);
            Stage stage = member.fields().isEmpty() ? Stage.PENDING : member.flag("active") ? Stage.ACTIVE : Stage.SUSPENDED;
            show(sessionFor(stage, user, account, claimMatches && "admin".equals(member.text("role"))));
        }, error -> {
            if (generation == refreshGeneration) reportSessionError(error.toException());
        });
    }

    private static Session sessionFor(Stage stage, FirebaseUser user, CommunityRecord account, boolean administrator) {
        return new Session(stage, user.getUid(), account.text("name"), account.text("unit"), user.getEmail(),
                account.text("community"), administrator);
    }

    public void signOut() {
        if (repository == null) return;
        clearDraft();
        repository.auth().signOut();
    }

    public void clearFeedback() {
        feedback.setValue(null);
    }

    private void clearDraft() {
        draftUid = null;
        draftName = null;
        draftUnit = null;
    }

    private boolean begin() {
        if (repository == null) {
            feedback.setValue(configurationMessage());
            return false;
        }
        if (Boolean.TRUE.equals(working.getValue())) return false;
        feedback.setValue(null);
        working.setValue(true);
        return true;
    }

    private void finish(Task<?> result, int fallback, int success) {
        working.setValue(false);
        if (result.isSuccessful() && success != 0) feedback.setValue(success);
        else if (!result.isSuccessful()) feedback.setValue(Errors.message(result.getException(), fallback));
    }

    private void onValidationTimeout() {
        refreshGeneration++;
        closeWatchers();
        show(Session.at(Stage.ERROR));
        feedback.setValue(null);
    }

    private void reportSessionError(Exception failure) {
        refreshGeneration++;
        closeWatchers();
        if (failure instanceof FirebaseAuthInvalidUserException) {
            signOut();
            return;
        }
        show(Session.at(Stage.ERROR));
        feedback.setValue(Errors.message(failure, R.string.generic_error));
    }

    private void show(Session value) {
        if (value.stage() != Stage.LOADING) handler.removeCallbacks(timeout);
        if (!value.equals(session.getValue())) session.setValue(value);
    }

    private void closeWatchers() {
        if (accountWatcher != null) accountWatcher.close();
        if (memberWatcher != null) memberWatcher.close();
        accountWatcher = null;
        memberWatcher = null;
    }

    @Override protected void onCleared() {
        handler.removeCallbacks(timeout);
        refreshGeneration++;
        if (repository != null) repository.auth().removeAuthStateListener(listener);
        closeWatchers();
    }
}
