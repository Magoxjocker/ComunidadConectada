package com.example.comunidadconectada.ui;

import android.widget.LinearLayout;

import com.example.comunidadconectada.R;
import com.example.comunidadconectada.data.CommunityRecord;

public final class ChatFragment extends FeedFragment {
    @Override protected String collection() { return "messages"; }
    @Override protected int emptyMessage() { return R.string.no_messages; }
    @Override protected boolean paginable() { return true; }
    @Override protected boolean chronological() { return true; }

    @Override protected void addControls(LinearLayout controls) {
        Ui.button(controls, R.string.write_message, R.drawable.ic_chat, () -> showForm(R.string.write_message, 0, R.string.send,
                R.string.saved, "sendMessage", null, Field.paragraph("text", R.string.message, "", 1, 1000)));
    }

    @Override protected RecordAdapter.Row row(CommunityRecord record) {
        String author = session().uid().equals(record.text("owner"))
                ? getString(R.string.own_message, record.text("author")) : author(record);
        String timestamp = Ui.date(record.number("createdAt"));
        return new RecordAdapter.Row(record.id(), author, record.text("text"), timestamp,
                () -> detail(author, record.text("text") + "\n\n" + timestamp, content -> {}));
    }
}
