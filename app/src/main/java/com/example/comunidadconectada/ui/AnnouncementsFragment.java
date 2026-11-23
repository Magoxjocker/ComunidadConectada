package com.example.comunidadconectada.ui;

import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.comunidadconectada.R;
import com.example.comunidadconectada.data.CommunityRecord;

import java.util.Comparator;

public final class AnnouncementsFragment extends FeedFragment {
    private static final String OPEN_ID = "openAnnouncement";
    private boolean opened;

    public static AnnouncementsFragment open(String id) {
        AnnouncementsFragment fragment = new AnnouncementsFragment();
        fragment.setArguments(FormDialog.target(OPEN_ID, id));
        return fragment;
    }

    @Override public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        if (savedInstanceState != null) opened = savedInstanceState.getBoolean(OPEN_ID);
    }

    @Override public void onResume() {
        super.onResume();
        if (getArguments() == null || opened || !host().active()) return;
        String id = getArguments().getString(OPEN_ID);
        records().child(id).get().addOnCompleteListener(result -> {
            if (binding == null || !isResumed()) return;
            opened = true;
            if (result.isSuccessful() && result.getResult().exists()) showAnnouncement(CommunityRecord.from(result.getResult()));
            else feedback(R.string.notification_unavailable);
        });
    }

    @Override public void onSaveInstanceState(@NonNull Bundle savedInstanceState) {
        super.onSaveInstanceState(savedInstanceState);
        savedInstanceState.putBoolean(OPEN_ID, opened);
    }

    @Override protected String collection() { return "announcements"; }
    @Override protected int emptyMessage() { return R.string.no_announcements; }
    @Override protected boolean paginable() { return true; }

    @Override protected void addControls(LinearLayout controls) {
        if (session().administrator()) Ui.button(controls, R.string.announcement_new, R.drawable.ic_announce, () -> edit(null));
    }

    @Override protected Comparator<CommunityRecord> order() { return priorityOrder(); }

    static Comparator<CommunityRecord> priorityOrder() {
        return Comparator.comparingInt((CommunityRecord record) -> Labels.priority(record.text("priority")))
                .thenComparingLong(record -> record.number("createdAt")).reversed();
    }

    @Override protected RecordAdapter.Row row(CommunityRecord record) {
        int level = Labels.priority(record.text("priority"));
        int accent = switch (level) {
            case 2 -> R.color.danger;
            case 1 -> R.color.warning;
            default -> R.color.petroleum;
        };
        return new RecordAdapter.Row(record.id(), record.text("title"), Ui.preview(record.text("description")),
                getString(R.string.date_meta, priority(record), Ui.date(record.number("createdAt"))),
                level > 0 ? R.drawable.ic_alert : R.drawable.ic_announce, accent, () -> showAnnouncement(record));
    }

    private String priority(CommunityRecord record) {
        return Labels.PRIORITIES.of(requireContext(), record.text("priority"));
    }

    private void showAnnouncement(CommunityRecord record) {
        String information = priority(record) + "\n\n" + record.text("description") + "\n\n"
                + author(record) + "\n" + Ui.date(record.number("createdAt"));
        detail(record.text("title"), information, actions -> {
            if (!session().administrator()) return;
            Ui.button(actions, R.string.edit, R.drawable.ic_tools, () -> edit(record));
            Ui.button(actions, R.string.delete, R.drawable.ic_close, () -> showForm(R.string.delete, R.string.confirm_delete,
                    R.string.delete, R.string.saved, "deleteAnnouncement", FormDialog.target("id", record.id())));
        });
    }

    private void edit(CommunityRecord record) {
        boolean create = record == null;
        showForm(create ? R.string.announcement_new : R.string.announcement_edit, 0,
                create ? R.string.publish : R.string.save, R.string.saved, "saveAnnouncement",
                create ? null : FormDialog.target("id", record.id()),
                Field.text("title", R.string.title, create ? "" : record.text("title"), 100),
                Field.paragraph("description", R.string.description, create ? "" : record.text("description"), 1, 3000),
                Labels.PRIORITIES.field(requireContext(), "priority", R.string.priority, create ? "" : record.text("priority")));
    }

}
