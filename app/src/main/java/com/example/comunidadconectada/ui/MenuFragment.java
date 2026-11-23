package com.example.comunidadconectada.ui;

import android.os.Bundle;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;

import com.example.comunidadconectada.MainActivity.Destination;
import com.example.comunidadconectada.R;
import com.example.comunidadconectada.services.CommunityMessagingService;
import com.example.comunidadconectada.viewmodel.SessionViewModel;
import com.example.comunidadconectada.viewmodel.SessionViewModel.Session;
import com.example.comunidadconectada.viewmodel.SessionViewModel.Stage;

public final class MenuFragment extends PanelFragment {
    private static final String DESTINATION = "destination";

    public static MenuFragment forDestination(Destination destination) {
        MenuFragment fragment = new MenuFragment();
        fragment.setArguments(FormDialog.target(DESTINATION, destination.name()));
        return fragment;
    }

    @Override public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        if (!host().active()) return;
        switch (Destination.valueOf(requireArguments().getString(DESTINATION))) {
            case PROFILE -> new ViewModelProvider(requireActivity()).get(SessionViewModel.class)
                    .session().observe(getViewLifecycleOwner(), this::profile);
            case ADMIN -> administration();
            default -> more();
        }
    }

    private void more() {
        binding.heading.setText(R.string.more);
        if (host().currentSession().administrator()) link(R.string.admin, R.drawable.ic_tools, Destination.ADMIN);
        link(R.string.announcements, R.drawable.ic_announce, Destination.ANNOUNCEMENTS);
        link(R.string.polls, R.drawable.ic_poll, Destination.POLLS);
        link(R.string.maintenance, R.drawable.ic_tools, Destination.MAINTENANCE);
        link(R.string.profile, R.drawable.ic_profile, Destination.PROFILE);
        link(R.string.accessibility, R.drawable.ic_access, Destination.ACCESSIBILITY);
        signOut();
    }

    private void profile(Session session) {
        if (session.stage() != Stage.ACTIVE) return;
        binding.actions.removeAllViews();
        binding.heading.setText(R.string.profile);
        Ui.status(binding.summary, getString(R.string.profile_details, session.name(), session.unit(),
                session.community(), getString(session.administrator() ? R.string.member_admin : R.string.member_neighbor)));
        Ui.paragraph(binding.actions, getString(R.string.profile_email, session.email()));
        Ui.button(binding.actions, R.string.edit_name, R.drawable.ic_profile, () -> FormDialog.show(
                getParentFragmentManager(), R.string.edit_name, 0, R.string.save, R.string.saved,
                "renameProfile", null, Field.text("name", R.string.name, session.name(), 100)));
        Ui.button(binding.actions,
                CommunityMessagingService.allowed(requireContext()) ? R.string.notifications_settings : R.string.notifications_enable,
                R.drawable.ic_announce, host()::notificationSettings);
        signOut();
    }

    private void administration() {
        binding.heading.setText(R.string.admin);
        showCommunityName(host().currentSession(), communityName ->
                Ui.status(binding.summary, getString(R.string.admin_caption, communityName)));
        link(R.string.neighbors, R.drawable.ic_profile, Destination.MEMBERS);
        link(R.string.announcements, R.drawable.ic_announce, Destination.ANNOUNCEMENTS);
        link(R.string.polls, R.drawable.ic_poll, Destination.POLLS);
        link(R.string.alerts, R.drawable.ic_alert, Destination.ALERTS);
        link(R.string.maintenance, R.drawable.ic_tools, Destination.MAINTENANCE);
        link(R.string.chat, R.drawable.ic_chat, Destination.CHAT);
        link(R.string.sales, R.drawable.ic_sales, Destination.SALES);
        link(R.string.profile, R.drawable.ic_profile, Destination.PROFILE);
        link(R.string.accessibility, R.drawable.ic_access, Destination.ACCESSIBILITY);
    }

    private void link(int title, int icon, Destination destination) {
        Ui.button(binding.actions, title, icon, () -> host().open(destination));
    }

    private void signOut() {
        Ui.button(binding.actions, R.string.sign_out, R.drawable.ic_logout, host()::signOut);
    }
}
