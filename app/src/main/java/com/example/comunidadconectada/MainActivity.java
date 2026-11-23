package com.example.comunidadconectada;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;

import androidx.activity.EdgeToEdge;
import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.example.comunidadconectada.data.CommunityRepository;
import com.example.comunidadconectada.databinding.ActivityMainBinding;
import com.example.comunidadconectada.services.CommunityMessagingService;
import com.example.comunidadconectada.ui.AccessibilityFragment;
import com.example.comunidadconectada.ui.AlertsFragment;
import com.example.comunidadconectada.ui.AnnouncementsFragment;
import com.example.comunidadconectada.ui.AuthFragment;
import com.example.comunidadconectada.ui.ChatFragment;
import com.example.comunidadconectada.ui.FormDialog;
import com.example.comunidadconectada.ui.HomeFragment;
import com.example.comunidadconectada.ui.MembersFragment;
import com.example.comunidadconectada.ui.MenuFragment;
import com.example.comunidadconectada.ui.PollsFragment;
import com.example.comunidadconectada.ui.ReportsFragment;
import com.example.comunidadconectada.ui.SalesFragment;
import com.example.comunidadconectada.ui.PreferenceStore;
import com.example.comunidadconectada.viewmodel.AlertChannelViewModel;
import com.example.comunidadconectada.viewmodel.SessionViewModel;
import com.example.comunidadconectada.viewmodel.OperationViewModel;
import com.example.comunidadconectada.ui.Errors;
import com.example.comunidadconectada.viewmodel.SessionViewModel.Session;
import com.example.comunidadconectada.viewmodel.SessionViewModel.Stage;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.Objects;
import java.util.List;
import java.util.stream.Collectors;

public final class MainActivity extends AppCompatActivity {
    public enum Destination {
        AUTH(0, 0), HOME(R.string.home, R.id.nav_home), CHAT(R.string.chat, R.id.nav_chat),
        SALES(R.string.sales, R.id.nav_sales), ALERTS(R.string.alerts, R.id.nav_alerts),
        MORE(R.string.more, R.id.nav_more), ANNOUNCEMENTS(R.string.announcements, 0),
        POLLS(R.string.polls, 0), MAINTENANCE(R.string.maintenance, 0), PROFILE(R.string.profile, 0),
        ADMIN(R.string.admin, 0), MEMBERS(R.string.neighbors, 0), ACCESSIBILITY(R.string.accessibility, 0);

        final int title;
        final int tab;

        Destination(int title, int tab) {
            this.title = title;
            this.tab = tab;
        }
    }

    private static final String DESTINATION = "destination";
    private static final String HISTORY = "history";
    private static final String SCOPE = "scope";
    private static final String PUBLIC_SETTINGS = "publicSettings";
    private static final String LINK_ID = "linkId";
    private static final String LINK_COMMUNITY = "linkCommunity";
    private static final float EXPANDED_NAVIGATION_FONT_SCALE = 1.4f;
    private ActivityMainBinding binding;
    private SessionViewModel model;
    private AlertChannelViewModel alertChannel;
    private Destination displayed;
    private final Deque<Destination> history = new ArrayDeque<>();
    private Destination restoredDestination;
    private String navigationScope;
    private boolean publicSettings;
    private Session session;
    private String linkId;
    private String linkCommunity;
    private boolean selecting;
    private boolean visible;
    private boolean checkingNotifications;

    private final ActivityResultLauncher<String> notificationRequest = registerForActivityResult(
            new ActivityResultContracts.RequestPermission(), granted -> {
                if (granted && active()) {
                    CommunityMessagingService.synchronize(this);
                    Snackbar.make(binding.getRoot(), R.string.notifications_enabled, Snackbar.LENGTH_SHORT).show();
                }
            });

    @Override protected void attachBaseContext(Context base) {
        super.attachBaseContext(new PreferenceStore(base).applyFont(base));
    }

    @Override protected void onCreate(Bundle savedInstanceState) {
        PreferenceStore preferences = new PreferenceStore(this);
        preferences.applyNightMode();
        if (preferences.highContrast()) setTheme(R.style.Theme_Comunidad_HighContrast);
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        binding.screen.setVisibility(View.INVISIBLE);
        setContentView(binding.getRoot());
        setupInsets();
        setupNavigation();
        if (savedInstanceState != null) restore(savedInstanceState);
        else acceptNotification(getIntent());
        ViewModelProvider provider = new ViewModelProvider(this);
        model = provider.get(SessionViewModel.class);
        alertChannel = provider.get(AlertChannelViewModel.class);
        model.session().observe(this, this::renderSession);
        configureFeedback(provider.get(OperationViewModel.class));
        CommunityMessagingService.createChannel(this);
    }

    private void setupInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.getRoot(), (view, windowInsets) -> {
            Insets bars = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars()
                    | WindowInsetsCompat.Type.displayCutout());
            Insets keyboard = windowInsets.getInsets(WindowInsetsCompat.Type.ime());
            view.setPadding(bars.left, bars.top, bars.right, Math.max(bars.bottom, keyboard.bottom));
            return WindowInsetsCompat.CONSUMED;
        });
    }

    private void setupNavigation() {
        binding.navigation.setOnItemSelectedListener(item -> {
            if (selecting) return true;
            for (Destination candidate : Destination.values()) {
                if (candidate.tab != 0 && candidate.tab == item.getItemId()) {
                    return selectTab(candidate);
                }
            }
            return false;
        });
        binding.navigationExpanded.setOnClickListener(view -> selectExpandedDestination());
        binding.toolbar.setNavigationOnClickListener(view -> goBack());
        binding.toolbar.setNavigationContentDescription(R.string.back);
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override public void handleOnBackPressed() { goBack(); }
        });
    }

    private void selectExpandedDestination() {
        List<Destination> destinations = Arrays.stream(Destination.values())
                .filter(destination -> destination.tab != 0).collect(Collectors.toList());
        String[] labels = destinations.stream().map(destination -> getString(destination.title)).toArray(String[]::new);
        Destination selected = displayed.tab == 0 ? Destination.MORE : displayed;
        new MaterialAlertDialogBuilder(this).setTitle(R.string.change_section)
                .setSingleChoiceItems(labels, destinations.indexOf(selected), (dialog, position) -> {
                    dialog.dismiss();
                    selectTab(destinations.get(position));
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void configureFeedback(OperationViewModel operations) {
        operations.notices().observe(this, notices -> {
            if (notices.isEmpty()) return;
            Session current = model.session().getValue();
            operations.acknowledge(notices);
            if (current == null || current.stage() != Stage.ACTIVE) return;
            for (OperationViewModel.Notice notice : notices) {
                if (!current.uid().equals(notice.key().uid()) || !current.community().equals(notice.key().community())) continue;
                int message = notice.failure() == null ? notice.message()
                        : Errors.message(notice.failure(), R.string.operation_failed);
                if (message != 0) Snackbar.make(binding.getRoot(), message, Snackbar.LENGTH_LONG).show();
            }
        });
    }

    private void restore(Bundle savedInstanceState) {
        String destination = savedInstanceState.getString(DESTINATION);
        restoredDestination = destination == null ? null : Destination.valueOf(destination);
        navigationScope = savedInstanceState.getString(SCOPE);
        publicSettings = savedInstanceState.getBoolean(PUBLIC_SETTINGS);
        ArrayList<String> previousScreens = savedInstanceState.getStringArrayList(HISTORY);
        if (previousScreens != null) {
            for (String screen : previousScreens) history.addLast(Destination.valueOf(screen));
        }
        linkId = savedInstanceState.getString(LINK_ID);
        linkCommunity = savedInstanceState.getString(LINK_COMMUNITY);
        Fragment restored = getSupportFragmentManager().findFragmentById(R.id.screen);
        if (restored != null && restored.getTag() != null) displayed = Destination.valueOf(restored.getTag());
    }

    @Override protected void onSaveInstanceState(Bundle activeState) {
        super.onSaveInstanceState(activeState);
        Destination destination = restoredDestination == null ? displayed : restoredDestination;
        if (destination != null) activeState.putString(DESTINATION, destination.name());
        activeState.putString(SCOPE, navigationScope);
        activeState.putBoolean(PUBLIC_SETTINGS, publicSettings);
        ArrayList<String> previousScreens = new ArrayList<>();
        for (Destination screen : history) previousScreens.add(screen.name());
        activeState.putStringArrayList(HISTORY, previousScreens);
        activeState.putString(LINK_ID, linkId);
        activeState.putString(LINK_COMMUNITY, linkCommunity);
    }

    @Override protected void onStart() {
        super.onStart();
        visible = true;
        updateAlertChannel();
    }

    @Override protected void onResume() {
        super.onResume();
        if (active() && !checkingNotifications) CommunityMessagingService.synchronize(this);
        checkingNotifications = false;
    }

    @Override protected void onResumeFragments() {
        super.onResumeFragments();
        Session current = model.session().getValue();
        if (current != null) renderSession(current);
        openPendingNotification();
    }

    @Override protected void onStop() {
        visible = false;
        alertChannel.stop();
        super.onStop();
    }

    private void renderSession(Session next) {
        if (getSupportFragmentManager().isStateSaved() || next.equals(session)) return;
        Session previous = session;
        session = next;
        if (active()) enterCommunity(previous);
        else showPublicScreen(previous);
        updateAlertChannel();
    }

    private void showPublicScreen(Session previous) {
        dismissForm();
        if (previous != null && previous.stage() == Stage.ACTIVE) publicSettings = false;
        if (session.stage() != Stage.LOADING) {
            CommunityMessagingService.clearSession(this);
            navigationScope = null;
            restoredDestination = null;
            history.clear();
        }
        display(publicSettings && session.stage() != Stage.LOADING ? Destination.ACCESSIBILITY : Destination.AUTH, null);
    }

    private void enterCommunity(Session previous) {
        String scope = session.uid() + "/" + session.community() + "/" + session.administrator();
        boolean sameScope = Objects.equals(navigationScope, scope);
        Destination destination = sameScope && restoredDestination != null ? restoredDestination : displayed;
        if (!sameScope) {
            dismissForm();
            binding.screen.setVisibility(View.INVISIBLE);
            history.clear();
            destination = landing();
        }
        if (!allowed(destination) || publicSettings) destination = landing();
        publicSettings = false;
        restoredDestination = null;
        navigationScope = scope;
        CommunityMessagingService.session(this, session.uid(), session.community());
        if (previous == null || previous.stage() != Stage.ACTIVE || !sameScope) {
            CommunityMessagingService.synchronize(this);
        }
        if (!openPendingNotification()) display(destination, null);
    }

    private void dismissForm() {
        for (Fragment fragment : getSupportFragmentManager().getFragments()) {
            if (fragment instanceof FormDialog form) form.dismissAllowingStateLoss();
        }
    }

    private void updateAlertChannel() {
        if (visible && active()) alertChannel.start(repository(), session.uid() + "/" + session.community());
        else alertChannel.stop();
    }

    public CommunityRepository repository() { return model.repository(); }
    public Session currentSession() { return session; }
    public boolean active() { return session != null && session.stage() == Stage.ACTIVE; }

    public void open(Destination destination) {
        if (getSupportFragmentManager().isStateSaved() || !allowed(destination) || destination == displayed) return;
        if (!active()) publicSettings = destination == Destination.ACCESSIBILITY;
        else if (displayed != null) history.addLast(displayed);
        display(destination, null);
    }

    private boolean selectTab(Destination destination) {
        if (!active() || getSupportFragmentManager().isStateSaved()) return false;
        if (destination == displayed) return true;
        history.clear();
        if (destination != landing()) history.addLast(landing());
        display(destination, null);
        return true;
    }

    private boolean allowed(Destination destination) {
        if (destination == null) return false;
        if (!active()) return destination == Destination.AUTH || destination == Destination.ACCESSIBILITY;
        return destination != Destination.AUTH && (!isAdministration(destination) || session.administrator());
    }

    private Destination landing() {
        return session.administrator() ? Destination.ADMIN : Destination.HOME;
    }

    private static boolean isAdministration(Destination destination) {
        return destination == Destination.ADMIN || destination == Destination.MEMBERS;
    }

    private void display(Destination destination, String announcementId) {
        updateNavigation(destination);
        if (destination == displayed && announcementId == null && binding.screen.getVisibility() == View.VISIBLE) return;
        displayed = destination;
        binding.screen.setVisibility(View.INVISIBLE);
        Fragment next = fragment(destination, announcementId);
        getSupportFragmentManager().beginTransaction()
                .setReorderingAllowed(true).replace(R.id.screen, next, destination.name())
                .runOnCommit(() -> {
                    if (getSupportFragmentManager().findFragmentById(R.id.screen) == next && allowed(destination)) {
                        binding.screen.setVisibility(View.VISIBLE);
                    }
                }).commit();
    }

    private static Fragment fragment(Destination destination, String announcementId) {
        return switch (destination) {
            case AUTH -> new AuthFragment();
            case HOME -> new HomeFragment();
            case CHAT -> new ChatFragment();
            case SALES -> new SalesFragment();
            case ALERTS -> new AlertsFragment();
            case ANNOUNCEMENTS -> announcementId == null ? new AnnouncementsFragment() : AnnouncementsFragment.open(announcementId);
            case POLLS -> new PollsFragment();
            case MAINTENANCE -> new ReportsFragment();
            case MEMBERS -> new MembersFragment();
            case ACCESSIBILITY -> new AccessibilityFragment();
            case MORE, PROFILE, ADMIN -> MenuFragment.forDestination(destination);
        };
    }

    private void updateNavigation(Destination destination) {
        binding.toolbar.setVisibility(destination == Destination.AUTH ? View.GONE : View.VISIBLE);
        if (destination.title != 0) binding.toolbar.setTitle(destination.title);
        boolean secondary = destination.tab == 0 && destination != Destination.AUTH
                && (!active() || destination != landing() || !history.isEmpty());
        binding.toolbar.setNavigationIcon(secondary
                ? AppCompatResources.getDrawable(this, R.drawable.ic_arrow_back) : null);
        boolean expanded = active() && getResources().getConfiguration().fontScale > EXPANDED_NAVIGATION_FONT_SCALE;
        binding.navigationExpanded.setVisibility(expanded ? View.VISIBLE : View.GONE);
        binding.navigation.setVisibility(active() && !expanded ? View.VISIBLE : View.GONE);
        if (expanded) {
            Destination label = destination.tab == 0 ? Destination.MORE : destination;
            binding.navigationExpanded.setText(getString(R.string.current_section, getString(label.title)));
        }
        if (active()) {
            selecting = true;
            try {
                binding.navigation.setSelectedItemId(destination.tab == 0 ? R.id.nav_more : destination.tab);
            } finally {
                selecting = false;
            }
        }
    }

    private void goBack() {
        if (getSupportFragmentManager().isStateSaved()) return;
        if (!active() && displayed == Destination.ACCESSIBILITY) {
            open(Destination.AUTH);
            return;
        }
        if (active()) {
            while (!history.isEmpty()) {
                Destination previous = history.removeLast();
                if (previous != displayed && allowed(previous)) {
                    display(previous, null);
                    return;
                }
            }
            if (displayed != landing()) {
                display(landing(), null);
                return;
            }
        }
        finish();
    }

    public void signOut() {
        CommunityMessagingService.clearSession(this);
        model.signOut();
    }

    public void notificationSettings() {
        checkingNotifications = true;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !CommunityMessagingService.allowed(this)) {
            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED
                    && (!getPreferences(MODE_PRIVATE).getBoolean("notification_requested", false)
                    || shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS))) {
                getPreferences(MODE_PRIVATE).edit().putBoolean("notification_requested", true).apply();
                notificationRequest.launch(Manifest.permission.POST_NOTIFICATIONS);
                return;
            }
        }
        startActivity(new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName()));
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        acceptNotification(intent);
        openPendingNotification();
    }

    private void acceptNotification(Intent intent) {
        if (!Intent.ACTION_VIEW.equals(intent.getAction())) return;
        String id = intent.getStringExtra(CommunityMessagingService.RECORD);
        String community = intent.getStringExtra(CommunityMessagingService.COMMUNITY);
        if (id == null || !id.matches("[A-Za-z0-9_-]{1,170}") || community == null) return;
        linkId = id;
        linkCommunity = community;
        intent.removeExtra(CommunityMessagingService.RECORD);
        intent.removeExtra(CommunityMessagingService.COMMUNITY);
    }

    public void openAnnouncement(String id) {
        if (!active() || getSupportFragmentManager().isStateSaved()) return;
        Destination previous = binding.screen.getVisibility() == View.VISIBLE && allowed(displayed) ? displayed : landing();
        if (previous != Destination.ANNOUNCEMENTS) history.addLast(previous);
        display(Destination.ANNOUNCEMENTS, id);
    }

    private boolean openPendingNotification() {
        if (!active() || linkId == null || getSupportFragmentManager().isStateSaved()) return false;
        String id = linkId;
        boolean match = session.community().equals(linkCommunity);
        linkId = null;
        linkCommunity = null;
        if (!match) {
            Snackbar.make(binding.getRoot(), R.string.notification_unavailable, Snackbar.LENGTH_LONG).show();
            return false;
        }
        openAnnouncement(id);
        return true;
    }
}
