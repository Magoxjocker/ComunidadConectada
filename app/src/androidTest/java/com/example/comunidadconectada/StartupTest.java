package com.example.comunidadconectada;

import android.content.Intent;
import android.content.res.Configuration;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.google.firebase.FirebaseApp;
import com.example.comunidadconectada.data.CommunityRepository;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.messaging.FirebaseMessaging;

import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.Espresso.pressBack;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.replaceText;
import static androidx.test.espresso.Espresso.closeSoftKeyboard;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.action.ViewActions.scrollTo;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.isAssignableFrom;
import static org.hamcrest.Matchers.allOf;
import static androidx.test.espresso.matcher.ViewMatchers.isEnabled;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertNotNull;
import static androidx.test.espresso.matcher.ViewMatchers.withText;

@RunWith(AndroidJUnit4.class)
public final class StartupTest {
    static Intent launchIntent() {
        return new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                .setClassName(BuildConfig.APPLICATION_ID, MainActivity.class.getName())
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    }

    @Test public void bundledFirebaseConfigurationInitializesSdksAndExplainsMissingDatabase() {
        Assume.assumeFalse(BuildConfig.USE_FIREBASE_EMULATORS);
        var context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        Assume.assumeTrue(CommunityRepository.hasDefaultFirebaseApp(context));

        FirebaseApp app = FirebaseApp.getInstance();
        assertNotNull(FirebaseAuth.getInstance(app));
        assertNotNull(FirebaseMessaging.getInstance());

        String databaseUrl = app.getOptions().getDatabaseUrl();
        if (databaseUrl == null || databaseUrl.trim().isEmpty()) {
            try (ActivityScenario<MainActivity> activity = ActivityScenario.launch(launchIntent())) {
                onView(withText(R.string.config_database_message)).check(matches(isDisplayed()));
            }
        } else {
            assertNotNull(FirebaseDatabase.getInstance(app));
            assertNotNull(CommunityRepository.configured(context));
        }
    }

    @Test public void preferencesKeepAccessUsableWithLargerTextAndDarkMode() {
        Assume.assumeFalse(BuildConfig.USE_FIREBASE_EMULATORS);
        Assume.assumeTrue(FirebaseApp.getApps(InstrumentationRegistry.getInstrumentation().getTargetContext()).isEmpty());
        try (ActivityScenario<MainActivity> activity = ActivityScenario.launch(launchIntent())) {
            onView(allOf(withText(R.string.accessibility), isAssignableFrom(com.google.android.material.button.MaterialButton.class)))
                    .perform(scrollTo(), click());
            pickPreference(R.string.text_size, R.string.text_large);
            pickPreference(R.string.text_size, R.string.text_xlarge);
            pickPreference(R.string.dark_mode, R.string.dark_enabled);
            activity.onActivity(screen -> assertEquals(Configuration.UI_MODE_NIGHT_YES,
                    screen.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK));
            pressBack();
            onView(withId(R.id.auth_email)).perform(scrollTo(), replaceText("vecino@example.test"));
            onView(withId(R.id.auth_password)).perform(scrollTo(), replaceText("presente"));
            closeSoftKeyboard();
            onView(withText(R.string.login)).perform(scrollTo()).check((view, missingView) -> {
                if (missingView != null) throw missingView;
                assertTrue(view.isEnabled() && view.isFocusable());
                assertTrue(view.getHeight() >= Math.round(48 * view.getResources().getDisplayMetrics().density));
            });
            onView(allOf(withText(R.string.accessibility), isAssignableFrom(com.google.android.material.button.MaterialButton.class)))
                    .perform(scrollTo(), click());
            pickPreference(R.string.dark_mode, R.string.follow_system);
            pickPreference(R.string.text_size, R.string.text_normal);
        }
    }

    private static void pickPreference(int title, int choice) {
        String label = InstrumentationRegistry.getInstrumentation().getTargetContext().getString(title);
        onView(withText(containsString(label))).perform(scrollTo(), click());
        onView(withText(choice)).inRoot(androidx.test.espresso.matcher.RootMatchers.isDialog()).perform(click());
    }

    @Test public void unconfiguredBuildShowsSetupAndAccessiblePreferencesAfterRecreation() {
        Assume.assumeFalse(BuildConfig.USE_FIREBASE_EMULATORS);
        Assume.assumeTrue(FirebaseApp.getApps(InstrumentationRegistry.getInstrumentation().getTargetContext()).isEmpty());
        try (ActivityScenario<MainActivity> activity = ActivityScenario.launch(launchIntent())) {
            onView(withText(R.string.config_title)).check(matches(isDisplayed()));
            onView(withText(R.string.login)).perform(scrollTo()).check(matches(not(isEnabled())));
            onView(withText(R.string.forgot_password)).perform(scrollTo()).check(matches(not(isEnabled())));
            onView(withId(R.id.auth_email)).perform(scrollTo(), replaceText("vecino@example.test"));
            onView(withId(R.id.auth_password)).perform(scrollTo(), replaceText("presente"));
            closeSoftKeyboard();
            onView(withText(R.string.login)).perform(scrollTo(), click());
            onView(withId(R.id.status)).check(matches(withText(R.string.config_message)));
            onView(allOf(withText(R.string.accessibility), isAssignableFrom(com.google.android.material.button.MaterialButton.class)))
                    .perform(scrollTo(), click());
            onView(withText(R.string.high_contrast)).check(matches(isDisplayed()));
            activity.recreate();
            onView(withText(R.string.high_contrast)).check(matches(isDisplayed()));
            onView(withText(R.string.high_contrast)).perform(scrollTo(), click());
            onView(withText(R.string.high_contrast)).check(matches(isDisplayed()));
            onView(withText(R.string.high_contrast)).perform(scrollTo(), click());
            pressBack();
            onView(withText(R.string.login)).perform(scrollTo()).check(matches(isDisplayed()));
        }
    }
}
