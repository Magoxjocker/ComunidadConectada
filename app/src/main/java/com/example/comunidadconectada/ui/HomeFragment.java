package com.example.comunidadconectada.ui;

import android.os.Bundle;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.comunidadconectada.MainActivity.Destination;
import com.example.comunidadconectada.R;
import com.example.comunidadconectada.data.CommunityRecord;
import com.example.comunidadconectada.data.CommunityRepository;
import com.example.comunidadconectada.viewmodel.SessionViewModel.Session;

import java.util.ArrayList;
import java.util.List;

public final class HomeFragment extends PanelFragment {
    private CommunityRepository.Subscription announcements;
    private List<CommunityRecord> recent = List.of();

    @Override public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        if (!host().active()) return;
        Session session = host().currentSession();
        binding.heading.setText(getString(R.string.hello, session.name()));
        showCommunityName(session, communityName ->
                Ui.status(binding.summary, getString(R.string.home_profile, session.unit(), communityName)));
        renderActions();
        binding.progress.setVisibility(View.VISIBLE);
        announcements = CommunityRepository.watch(CommunityRepository.latest(host().repository()
                            .community(session.community()).child("announcements")), snapshot -> {
                        recent = CommunityRepository.records(snapshot);
                        if (binding != null) {
                            binding.progress.setVisibility(View.GONE);
                            Ui.status(binding.status, null);
                            renderActions();
                        }
                    }, error -> {
                        if (binding != null) {
                            binding.progress.setVisibility(View.GONE);
                            Ui.status(binding.status, getText(R.string.generic_error));
                        }
                    });
    }

    private void renderActions() {
        binding.actions.removeAllViews();
        Ui.emergency(Ui.button(binding.actions, R.string.alert_new, R.drawable.ic_alert,
                () -> AlertsFragment.openSos(getParentFragmentManager(), requireContext())));
        Ui.heading(binding.actions, R.string.featured);
        List<CommunityRecord> priority = new ArrayList<>(recent);
        priority.sort(AnnouncementsFragment.priorityOrder());
        recentNews(priority.stream().filter(record -> Labels.priority(record.text("priority")) > 0).limit(3)
                .collect(java.util.stream.Collectors.toList()), R.string.no_featured);
        Ui.heading(binding.actions, R.string.quick_actions);
        Ui.button(binding.actions, R.string.announcements, R.drawable.ic_announce, () -> host().open(Destination.ANNOUNCEMENTS));
        Ui.button(binding.actions, R.string.maintenance, R.drawable.ic_tools, () -> host().open(Destination.MAINTENANCE));
        Ui.button(binding.actions, R.string.polls, R.drawable.ic_poll, () -> host().open(Destination.POLLS));
        Ui.heading(binding.actions, R.string.latest);
        List<CommunityRecord> latest = new ArrayList<>(recent);
        java.util.Collections.reverse(latest);
        recentNews(latest.subList(0, Math.min(3, latest.size())), R.string.no_announcements);
    }

    private void recentNews(List<CommunityRecord> records, int emptyMessage) {
        if (records.isEmpty()) Ui.paragraph(binding.actions, getText(emptyMessage));
        for (CommunityRecord record : records) {
            int priority = Labels.priority(record.text("priority"));
            Ui.button(binding.actions, getString(R.string.date_meta, record.text("title"),
                            Labels.PRIORITIES.of(requireContext(), record.text("priority"))),
                    priority >= 2 ? R.drawable.ic_alert : R.drawable.ic_announce,
                    () -> host().openAnnouncement(record.id()));
        }
    }

    @Override public void onDestroyView() {
        if (announcements != null) announcements.close();
        super.onDestroyView();
    }
}
