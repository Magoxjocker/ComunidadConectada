package com.example.comunidadconectada;

import android.content.Context;
import android.view.View;
import android.widget.EditText;

import androidx.test.core.app.ActivityScenario;
import androidx.lifecycle.ViewModelProvider;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.espresso.NoMatchingViewException;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.example.comunidadconectada.data.CommunityRepository;
import com.example.comunidadconectada.data.CommunityRecord;
import com.example.comunidadconectada.viewmodel.FeedViewModel;
import com.example.comunidadconectada.viewmodel.OperationViewModel;
import com.google.android.gms.tasks.TaskCompletionSource;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.ServerValue;

import org.hamcrest.Matcher;
import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;
import java.util.concurrent.TimeUnit;

import static androidx.test.espresso.Espresso.closeSoftKeyboard;
import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.Espresso.pressBack;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.replaceText;
import static androidx.test.espresso.action.ViewActions.scrollTo;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.RootMatchers.isDialog;
import static androidx.test.espresso.matcher.RootMatchers.isPlatformPopup;
import static androidx.test.espresso.matcher.ViewMatchers.isAssignableFrom;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withParent;
import static androidx.test.espresso.matcher.ViewMatchers.isEnabled;
import static androidx.test.espresso.assertion.ViewAssertions.doesNotExist;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static org.hamcrest.Matchers.allOf;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@RunWith(AndroidJUnit4.class)
public final class CommunityFlowTest {
    private static final String FIXTURE_PASSWORD = "SoloFixture_2026!";

    @Test public void otherAuthorizedSessionSeesMessageFromAdministrator() throws Exception {
        Assume.assumeTrue(BuildConfig.USE_FIREBASE_EMULATORS);
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        FirebaseAuth auth = CommunityRepository.configured(context).auth();
        String message = "Mensaje QA " + UUID.randomUUID();
        signIn(auth, "android-admin@example.test");
        try (ActivityScenario<MainActivity> activity = ActivityScenario.launch(StartupTest.launchIntent())) {
            openChat();
            sendMessage(message);
            awaitVisible(allOf(withId(R.id.body), withText(message)));
        }
        auth.signOut();
        signIn(auth, "android-vecino@example.test");
        try (ActivityScenario<MainActivity> activity = ActivityScenario.launch(StartupTest.launchIntent())) {
            openChat();
            awaitVisible(allOf(withId(R.id.body), withText(message)));
        } finally {
            auth.signOut();
        }
    }

    @Test public void navigatesFromNeighborHomeAndReturnsToLoginAfterSignOut() throws Exception {
        FirebaseAuth auth = localAuthentication();
        auth.signOut();
        try (ActivityScenario<MainActivity> activity = ActivityScenario.launch(StartupTest.launchIntent())) {
            assertLogin();
            onView(withText(R.string.create_account)).perform(androidx.test.espresso.action.ViewActions.scrollTo(), click());
            onView(withText(R.string.back)).perform(androidx.test.espresso.action.ViewActions.scrollTo(), click());
            assertLogin();
            signIn(auth, "android-vecino@example.test");
            awaitTitle(R.string.home);
            awaitVisible(withText(containsString("Comunidad QA")));
            openChat();
            awaitTitle(R.string.chat);
            tab(R.id.nav_sales, R.string.sales);
            tab(R.id.nav_alerts, R.string.alerts);
            tab(R.id.nav_home, R.string.home);
            tab(R.id.nav_more, R.string.more);
            for (int section : new int[]{R.string.announcements, R.string.polls, R.string.maintenance,
                    R.string.profile, R.string.accessibility}) {
                section(section);
                if (section == R.string.profile) verifyProfileEdit();
                pressBack();
                awaitTitle(R.string.more);
            }
            onView(withText(R.string.admin)).check(doesNotExist());
            panelButton(R.string.sign_out);
            assertLogin();
            onView(withId(R.id.nav_home)).check(matches(org.hamcrest.Matchers.not(isDisplayed())));
        } finally {
            auth.signOut();
        }
    }

    @Test public void startsAtAdminPanelAndRestoresBackHistoryAfterRecreation() throws Exception {
        FirebaseAuth auth = localAuthentication();
        signIn(auth, "android-admin@example.test");
        try (ActivityScenario<MainActivity> activity = ActivityScenario.launch(StartupTest.launchIntent())) {
            awaitTitle(R.string.admin);
            awaitVisible(withText(containsString("Comunidad QA")));
            section(R.string.neighbors);
            activity.recreate();
            awaitTitle(R.string.neighbors);
            pressBack();
            awaitTitle(R.string.admin);
            section(R.string.announcements);
            awaitVisible(withText(R.string.announcement_new));
            pressBack();
            awaitTitle(R.string.admin);
            tab(R.id.nav_home, R.string.home);
            pressBack();
            awaitTitle(R.string.admin);
        } finally {
            auth.signOut();
        }
    }

    @Test public void keepsVerificationAndPendingAccountsOutsideProtectedScreens() throws Exception {
        FirebaseAuth auth = localAuthentication();
        signIn(auth, "android-verificar@example.test");
        try (ActivityScenario<MainActivity> activity = ActivityScenario.launch(StartupTest.launchIntent())) {
            awaitVisible(withText(R.string.verification_title));
            onView(withId(R.id.nav_home)).check(matches(org.hamcrest.Matchers.not(isDisplayed())));
            panelButton(R.string.sign_out);
            assertLogin();
            signIn(auth, "android-pendiente@example.test");
            awaitVisible(withText(R.string.pending_title));
            onView(withId(R.id.nav_home)).check(matches(org.hamcrest.Matchers.not(isDisplayed())));
        } finally {
            auth.signOut();
        }
    }

    @Test public void validatesFormsRecoversAfterLoginErrorAndCreatesUnverifiedAccount() throws Exception {
        FirebaseAuth auth = localAuthentication();
        auth.signOut();
        try (ActivityScenario<MainActivity> activity = ActivityScenario.launch(StartupTest.launchIntent())) {
            onView(withText(R.string.login)).perform(scrollTo()).check(matches(not(isEnabled())));
            fill(R.id.auth_email, "correo-invalido");
            fill(R.id.auth_password, "a");
            onView(withText(R.string.login)).perform(scrollTo()).check(matches(not(isEnabled())));
            fill(R.id.auth_email, "android-admin@example.test");
            onView(withText(R.string.login)).perform(scrollTo()).check(matches(isEnabled())).perform(click());
            awaitVisible(withText(R.string.login_error));
            onView(withText(R.string.login)).perform(scrollTo()).check(matches(isEnabled()));
            panelButton(R.string.forgot_password);
            awaitVisible(withText(R.string.reset_sent));

            fill(R.id.auth_password, FIXTURE_PASSWORD);
            panelButton(R.string.login);
            awaitTitle(R.string.admin);
            tab(R.id.nav_more, R.string.more);
            panelButton(R.string.sign_out);
            awaitVisible(withText(R.string.login));
            panelButton(R.string.create_account);

            submitRegistration().perform(scrollTo()).check(matches(not(isEnabled())));
            fill(R.id.auth_name, "Vecina de pruebas");
            fill(R.id.auth_unit, "B-15");
            fill(R.id.auth_email, "registro-" + UUID.randomUUID() + "@example.test");
            fill(R.id.auth_password, "VecinoSeguro19");
            fill(R.id.auth_confirmation, "incompatible");
            submitRegistration().perform(scrollTo()).check(matches(not(isEnabled())));
            fill(R.id.auth_confirmation, "VecinoSeguro19");
            submitRegistration().perform(scrollTo()).check(matches(isEnabled())).perform(click());
            awaitVisible(withText(R.string.verification_title));
            awaitVisible(withText(R.string.link_sent));
            onView(withId(R.id.nav_home)).check(matches(not(isDisplayed())));
        } finally {
            auth.signOut();
        }
    }

    @Test public void exchangesMessagesAcrossTwoEmulators() throws Exception {
        String participant = InstrumentationRegistry.getArguments().getString("chatPeer");
        String exchange = InstrumentationRegistry.getArguments().getString("chatExchange");
        Assume.assumeTrue(participant != null && exchange != null);
        FirebaseAuth auth = localAuthentication();
        boolean sender = "admin".equals(participant);
        signIn(auth, sender ? "android-admin@example.test" : "android-vecino@example.test");
        String challenge = "QA simultánea " + exchange;
        String reply = "Confirmación QA " + exchange;
        try (ActivityScenario<MainActivity> activity = ActivityScenario.launch(StartupTest.launchIntent())) {
            openChat();
            if (sender) {
                sendMessage(challenge);
                awaitVisible(allOf(withId(R.id.body), withText(reply)), 60);
            } else {
                awaitVisible(allOf(withId(R.id.body), withText(challenge)), 60);
                sendMessage(reply);
                awaitVisible(allOf(withId(R.id.body), withText(reply)), 60);
            }
        } finally {
            auth.signOut();
        }
    }

    @Test public void ownReportsPageByDateAndKeepAllRecordsAtTheBoundary() throws Exception {
        FirebaseAuth auth = localAuthentication();
        signIn(auth, "android-vecino@example.test");
        String uid = auth.getCurrentUser().getUid();
        AtomicReference<FeedViewModel.State> observed = new AtomicReference<>();
        try (ActivityScenario<MainActivity> activity = ActivityScenario.launch(StartupTest.launchIntent())) {
            awaitTitle(R.string.home);
            activity.onActivity(screen -> {
                FeedViewModel feed = new ViewModelProvider(screen).get("qaOwnedReports", FeedViewModel.class);
                feed.state().observe(screen, observed::set);
                feed.loadOwnReports(screen.repository().community("androidQa").child("reports"), uid);
            });
            FeedViewModel.State first = awaitFeed(observed, state -> state.records().size() == 50);
            assertTrue(first.older());
            assertEquals(uid + "_0newest", first.records().get(49).id());
            assertEquals(uid + "_z49", first.records().get(47).id());
            assertEquals(uid + "_z50", first.records().get(48).id());
            activity.onActivity(screen -> new ViewModelProvider(screen).get("qaOwnedReports", FeedViewModel.class).loadOlder());
            FeedViewModel.State all = awaitFeed(observed, state -> state.records().size() == 51);
            assertFalse(all.older());
            assertEquals(51L, all.records().stream().map(CommunityRecord::id).distinct().count());
            assertEquals(first.records(), all.records().subList(1, 51));
        } finally {
            auth.signOut();
        }
    }

    @Test public void rejectsBlankResolutionAndSavesAValidNote() throws Exception {
        FirebaseAuth auth = localAuthentication();
        signIn(auth, "android-admin@example.test");
        DatabaseReference report = localRepository().community("androidQa").child("reports/androidNeighbor_0newest");
        try (ActivityScenario<MainActivity> activity = ActivityScenario.launch(StartupTest.launchIntent())) {
            awaitTitle(R.string.admin);
            section(R.string.maintenance);
            awaitVisible(withText("Reporte QA reciente"));
            onView(withText("Reporte QA reciente")).perform(click());
            onView(withText(R.string.report_update)).inRoot(isDialog()).perform(click());
            onView(isAssignableFrom(android.widget.Spinner.class)).inRoot(isDialog()).perform(click());
            onView(withText(R.string.report_resolved)).inRoot(isPlatformPopup()).perform(click());
            onView(withText(R.string.save)).inRoot(isDialog()).perform(click());
            awaitVisible(withText(R.string.report_note_required));
            assertEquals("pending", readOnce(report).child("status").getValue());
            onView(isAssignableFrom(EditText.class)).inRoot(isDialog()).perform(replaceText("Resolución QA confirmada"));
            closeSoftKeyboard();
            onView(withText(R.string.save)).inRoot(isDialog()).perform(click());
            awaitVisible(withText(R.string.saved));
            var saved = readOnce(report);
            assertEquals("resolved", saved.child("status").getValue());
            assertEquals(1L, saved.child("history").getChildrenCount());
            assertEquals("Resolución QA confirmada", saved.child("history").getChildren().iterator().next().child("note").getValue());
        } finally {
            auth.signOut();
        }
    }

    @Test public void canLeavePendingFormAndReconnectWithoutSubmittingTwice() throws Exception {
        FirebaseAuth auth = localAuthentication();
        signIn(auth, "android-vecino@example.test");
        DatabaseReference community = localRepository().community("androidQa");
        var database = community.getDatabase();
        String uid = auth.getCurrentUser().getUid();
        String message = "Mensaje sin conexión QA " + UUID.randomUUID();
        OperationViewModel.Key operation = new OperationViewModel.Key(uid, "androidQa", "sendMessage", "");
        AtomicReference<DatabaseReference> queuedRecord = new AtomicReference<>();
        AtomicReference<String> failedRequestId = new AtomicReference<>();
        AtomicInteger submissions = new AtomicInteger();
        try (ActivityScenario<MainActivity> activity = ActivityScenario.launch(StartupTest.launchIntent())) {
            openChat();
            onView(withText(R.string.write_message)).perform(click());
            activity.onActivity(screen -> new ViewModelProvider(screen).get(OperationViewModel.class)
                    .submit(operation, Map.of("text", message), R.string.saved, requestId -> {
                        failedRequestId.set(requestId);
                        return Tasks.forException(new IllegalStateException("Fallo de prueba antes de escribir"));
                    }));
            awaitVisible(withText(R.string.generic_error));
            database.goOffline();
            activity.onActivity(screen -> new ViewModelProvider(screen).get(OperationViewModel.class)
                    .submit(operation, Map.of("text", message), R.string.saved, requestId -> {
                        assertEquals(failedRequestId.get(), requestId);
                        submissions.incrementAndGet();
                        DatabaseReference record = community.child("messages").child(uid + "_" + requestId);
                        queuedRecord.set(record);
                        return record.updateChildren(Map.of("text", message, "owner", uid,
                                "author", "Vecina QA",
                                "unit", "A-2",
                                "community", "androidQa", "createdAt", ServerValue.TIMESTAMP));
                    }));
            awaitVisible(withText(R.string.operation_pending));
            onView(withText(R.string.back)).inRoot(isDialog()).perform(click());
            onView(withText(R.string.write_message)).perform(click());
            onView(withText(R.string.send)).inRoot(isDialog()).check(matches(not(isEnabled())));
            onView(isAssignableFrom(EditText.class)).inRoot(isDialog()).check(matches(withText(message)));
            activity.onActivity(screen -> new ViewModelProvider(screen).get(OperationViewModel.class)
                    .submit(operation, Map.of("text", message), R.string.saved, requestId -> {
                        throw new AssertionError("Una operación pendiente no debe emitir otra escritura");
                    }));
            onView(withText(R.string.back)).inRoot(isDialog()).perform(click());
            database.goOnline();
            awaitVisible(withText(R.string.saved));
            assertEquals(1, submissions.get());
            assertEquals(message, readOnce(queuedRecord.get()).child("text").getValue());
        } finally {
            database.goOnline();
            auth.signOut();
        }
    }

    private static DataSnapshot readOnce(DatabaseReference reference) throws Exception {
        var completion = new TaskCompletionSource<DataSnapshot>();
        var subscription = CommunityRepository.watch(reference, completion::trySetResult,
                error -> completion.trySetException(error.toException()));
        try {
            return Tasks.await(completion.getTask(), 15, TimeUnit.SECONDS);
        } finally {
            subscription.close();
        }
    }

    private static FeedViewModel.State awaitFeed(AtomicReference<FeedViewModel.State> observed,
                                                 Predicate<FeedViewModel.State> expected) throws InterruptedException {
        long deadline = android.os.SystemClock.elapsedRealtime() + TimeUnit.SECONDS.toMillis(20);
        while (android.os.SystemClock.elapsedRealtime() < deadline) {
            FeedViewModel.State state = observed.get();
            if (state != null && expected.test(state)) return state;
            Thread.sleep(50);
        }
        throw new AssertionError("No llegó la página de reportes esperada");
    }

    private static androidx.test.espresso.ViewInteraction submitRegistration() {
        return onView(allOf(withText(R.string.create_account), isAssignableFrom(com.google.android.material.button.MaterialButton.class)));
    }

    private static void verifyProfileEdit() throws InterruptedException {
        panelButton(R.string.edit_name);
        onView(allOf(isAssignableFrom(EditText.class), isDisplayed())).inRoot(isDialog()).perform(replaceText("Vecina actualizada"));
        closeSoftKeyboard();
        onView(withText(R.string.save)).inRoot(isDialog()).perform(click());
        awaitVisible(allOf(withId(R.id.summary), withText(org.hamcrest.Matchers.containsString("Vecina actualizada"))));
    }

    private static void fill(int inputId, String value) {
        onView(withId(inputId)).perform(scrollTo(), replaceText(value));
        closeSoftKeyboard();
    }

    private static void sendMessage(String message) {
        onView(withText(R.string.write_message)).perform(click());
        onView(allOf(isAssignableFrom(EditText.class), isDisplayed())).inRoot(isDialog()).perform(replaceText(message));
        closeSoftKeyboard();
        onView(withText(R.string.send)).inRoot(isDialog()).perform(click());
    }

    private static FirebaseAuth localAuthentication() {
        return localRepository().auth();
    }

    private static CommunityRepository localRepository() {
        Assume.assumeTrue(BuildConfig.USE_FIREBASE_EMULATORS);
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        return CommunityRepository.configured(context);
    }

    private static void assertLogin() throws InterruptedException {
        awaitVisible(withText(R.string.login));
        onView(withText(R.string.forgot_password)).perform(scrollTo()).check(matches(not(isEnabled())));
    }

    private static void tab(int id, int title) throws InterruptedException {
        onView(withId(id)).perform(click());
        awaitTitle(title);
    }

    private static void section(int title) throws InterruptedException {
        panelButton(title);
        awaitTitle(title);
    }

    private static void panelButton(int title) {
        onView(allOf(withText(title), isAssignableFrom(com.google.android.material.button.MaterialButton.class)))
                .perform(androidx.test.espresso.action.ViewActions.scrollTo(), click());
    }

    private static void awaitTitle(int title) throws InterruptedException {
        awaitVisible(allOf(withParent(withId(R.id.toolbar)), withText(title)));
    }

    private static void signIn(FirebaseAuth auth, String email) throws Exception {
        Tasks.await(auth.signInWithEmailAndPassword(email, FIXTURE_PASSWORD), 15, TimeUnit.SECONDS);
    }

    private static void openChat() throws InterruptedException {
        awaitVisible(withId(R.id.nav_chat));
        onView(withId(R.id.nav_chat)).perform(click());
    }

    private static void awaitVisible(Matcher<View> expected) throws InterruptedException {
        awaitVisible(expected, 20);
    }

    private static void awaitVisible(Matcher<View> expected, long timeoutSeconds) throws InterruptedException {
        long deadline = android.os.SystemClock.elapsedRealtime() + TimeUnit.SECONDS.toMillis(timeoutSeconds);
        Throwable lastFailure = null;
        while (android.os.SystemClock.elapsedRealtime() < deadline) {
            try {
                onView(allOf(expected, isDisplayed())).check(matches(isDisplayed()));
                return;
            } catch (AssertionError | NoMatchingViewException failure) {
                lastFailure = failure;
                Thread.sleep(150);
            }
        }
        throw new AssertionError("No apareció el elemento esperado: " + expected, lastFailure);
    }
}
