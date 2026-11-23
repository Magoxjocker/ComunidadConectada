package com.example.comunidadconectada.ui;

import android.os.Bundle;
import android.widget.LinearLayout;

import com.example.comunidadconectada.R;
import com.example.comunidadconectada.data.CommunityRecord;
import com.example.comunidadconectada.data.CommunityRepository;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.Query;

import java.util.Comparator;

public final class MembersFragment extends FeedFragment {
    @Override protected String collection() { return "members"; }
    @Override protected int emptyMessage() { return R.string.no_members; }
    @Override protected boolean paginable() { return true; }
    @Override protected Query listQuery(DatabaseReference collection) {
        return collection.orderByKey().limitToLast(CommunityRepository.PAGE_SIZE);
    }
    @Override protected Query historyQuery(DatabaseReference collection, CommunityRecord boundary) {
        return collection.orderByKey().endBefore(boundary.id()).limitToLast(CommunityRepository.PAGE_SIZE);
    }
    @Override protected Comparator<CommunityRecord> order() { return Comparator.comparing(record -> record.text("name")); }

    @Override protected void addControls(LinearLayout controls) {
        Ui.paragraph(controls, getText(R.string.invitations_help));
        Ui.button(controls, R.string.add_neighbor, R.drawable.ic_add, () -> showForm(R.string.add_neighbor,
                R.string.invitations_help, R.string.enable, R.string.saved, "addNeighbor", null, Field.text("uid", R.string.membership_uid_label, "", 128)));
    }

    @Override protected RecordAdapter.Row row(CommunityRecord record) {
        boolean active = record.flag("active");
        int access = active ? R.string.access_active : R.string.access_suspended;
        String caption = authorText(record);
        return new RecordAdapter.Row(record.id(), record.text("name"), caption, getText(access),
                () -> detail(record.text("name"), caption + "\n" + getString(access), actions -> {
                    if (!"neighbor".equals(record.text("role"))) return;
                    int action = active ? R.string.suspend : R.string.enable;
                    Ui.button(actions, action, R.drawable.ic_profile, () -> changeAccess(record.id(), active, action));
                }));
    }

    private String authorText(CommunityRecord record) {
        return getString(R.string.member_details, record.text("unit"), record.text("email"),
                getString("admin".equals(record.text("role")) ? R.string.member_admin : R.string.member_neighbor));
    }

    private void changeAccess(String uid, boolean active, int action) {
        Bundle target = FormDialog.target("uid", uid);
        target.putBoolean("active", !active);
        showForm(action, active ? R.string.suspend_confirm : R.string.enable_confirm,
                action, R.string.saved, "setNeighborAccess", target);
    }
}
