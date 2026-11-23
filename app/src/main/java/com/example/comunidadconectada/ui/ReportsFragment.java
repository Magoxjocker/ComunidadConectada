package com.example.comunidadconectada.ui;

import android.widget.LinearLayout;

import com.example.comunidadconectada.R;
import com.example.comunidadconectada.data.CommunityRecord;

import java.util.Comparator;
import java.util.List;

public final class ReportsFragment extends FeedFragment {
    @Override protected String collection() { return "reports"; }
    @Override protected int emptyMessage() { return R.string.no_reports; }
    @Override protected boolean paginable() { return true; }

    @Override protected boolean usesOwnReportPages() { return !session().administrator(); }

    @Override protected Comparator<CommunityRecord> order() {
        return Comparator.comparingLong((CommunityRecord report) -> report.number("createdAt"))
                .thenComparing(CommunityRecord::id).reversed();
    }

    @Override protected void addControls(LinearLayout controls) {
        Ui.button(controls, R.string.report_new, R.drawable.ic_add, () -> showForm(R.string.report_new, 0,
                R.string.save, R.string.saved, "createReport", null,
                Field.text("title", R.string.title, "", 100),
                Field.paragraph("description", R.string.description, "", 1, 2000),
                Field.text("location", R.string.report_location, "", 120),
                Labels.REPORT_CATEGORIES.field(requireContext(), "category", R.string.category, "")));
    }

    @Override protected RecordAdapter.Row row(CommunityRecord report) {
        return new RecordAdapter.Row(report.id(), report.text("title"), report.text("location"),
                getString(R.string.date_meta, state(report), Ui.date(report.number("createdAt"))),
                () -> showReport(report));
    }

    private String state(CommunityRecord record) {
        return Labels.REPORT_STATES.of(requireContext(), record.text("status"));
    }

    private void showReport(CommunityRecord report) {
        String information = report.text("description") + "\n\n" + state(report) + "\n"
                + Labels.REPORT_CATEGORIES.of(requireContext(), report.text("category")) + "\n"
                + report.text("location") + "\n" + author(report) + "\n" + Ui.date(report.number("createdAt"));
        detail(report.text("title"), information, actions -> {
            List<CommunityRecord> changes = report.children("history");
            if (!changes.isEmpty()) showHistory(actions, changes);
            if (session().administrator()) Ui.button(actions, R.string.report_update, R.drawable.ic_tools, () -> update(report));
        });
    }

    private void showHistory(LinearLayout actions, List<CommunityRecord> changes) {
        Ui.heading(actions, R.string.report_history);
        changes.sort(Comparator.comparingLong(change -> change.number("createdAt")));
        for (CommunityRecord change : changes) {
            String metadata = getString(R.string.date_meta, change.text("author"), Ui.date(change.number("createdAt")));
            Ui.paragraph(actions, getString(R.string.history_entry, state(change), metadata, change.text("note")));
        }
    }

    private void update(CommunityRecord report) {
        showForm(R.string.report_update, 0, R.string.save, R.string.saved, "updateReport", FormDialog.target("id", report.id()),
                Labels.REPORT_STATES.field(requireContext(), "status", R.string.status, report.text("status")),
                Field.paragraph("note", R.string.report_note, "", 0, 1500));
    }
}
