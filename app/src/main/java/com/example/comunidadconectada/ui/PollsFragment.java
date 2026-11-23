package com.example.comunidadconectada.ui;

import android.widget.LinearLayout;

import com.example.comunidadconectada.R;
import com.example.comunidadconectada.data.CommunityRecord;
import com.example.comunidadconectada.domain.InputValidation;
import com.example.comunidadconectada.domain.PollMath;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class PollsFragment extends FeedFragment {
    @Override protected String collection() { return "polls"; }
    @Override protected int emptyMessage() { return R.string.no_polls; }
    @Override protected boolean paginable() { return true; }

    @Override protected void addControls(LinearLayout controls) {
        if (session().administrator()) Ui.button(controls, R.string.poll_new, R.drawable.ic_add, this::create);
    }

    private void create() {
        showForm(R.string.poll_new, 0, R.string.publish, R.string.saved, "createPoll", null,
                Field.text("title", R.string.poll_question, "", 150),
                Field.options("options", R.string.poll_options), Field.date("closesAt", R.string.poll_close_date),
                Field.check("showResults", R.string.poll_show_results, true));
    }

    @Override protected RecordAdapter.Row row(CommunityRecord poll) {
        return new RecordAdapter.Row(poll.id(), poll.text("title"), electionState(poll),
                Ui.date(poll.number("createdAt")), () -> openPoll(poll));
    }

    private String electionState(CommunityRecord poll) {
        return open(poll) ? getString(R.string.poll_active, Ui.date(poll.number("closesAt"))) : getString(R.string.poll_closed);
    }

    private static boolean open(CommunityRecord poll) {
        return !poll.flag("closed") && poll.number("closesAt") > System.currentTimeMillis();
    }

    private void openPoll(CommunityRecord poll) {
        String uid = session().uid();
        host().repository().community(session().community()).child("pollLedger").child(poll.id())
                .child("votes").child(uid).get().addOnCompleteListener(result -> {
                    if (binding == null || !isResumed()) return;
                    if (!result.isSuccessful()) {
                        feedback(R.string.generic_error);
                        return;
                    }
                    Object vote = result.getResult().getValue();
                    showPoll(poll, vote instanceof Number choice ? choice.intValue() : -1);
                });
    }

    private void showPoll(CommunityRecord poll, int vote) {
        List<String> options = new ArrayList<>();
        for (Object option : poll.list("options")) {
            if (option instanceof String label) options.add(label);
        }
        detail(poll.text("title"), electionState(poll), actions -> {
            for (String option : options) Ui.paragraph(actions, option);
            if (vote >= 0 && vote < options.size()) Ui.paragraph(actions, getString(R.string.poll_voted, options.get(vote)));
            else if (open(poll) && poll.number("sparkVersion") == 1 && options.size() >= 2) {
                Ui.button(actions, R.string.poll_vote, R.drawable.ic_poll, () -> vote(poll, options));
            }
            addResults(actions, poll, options);
            if (session().administrator() && open(poll)) {
                Ui.button(actions, R.string.poll_close, R.drawable.ic_close, () -> showForm(R.string.poll_close,
                        R.string.poll_close_confirm, R.string.close, R.string.saved,
                        "closePoll", FormDialog.target("id", poll.id())));
            }
        });
    }

    private void addResults(LinearLayout panel, CommunityRecord poll, List<String> options) {
        if (poll.number("sparkVersion") != 1) {
            if (open(poll) || (session().administrator() && !poll.flag("showResults"))) {
                Ui.paragraph(panel, getText(R.string.poll_legacy));
            }
            if (poll.flag("showResults")) renderResults(panel, options, poll.list("counts"));
            return;
        }
        if (!poll.flag("showResults") && !session().administrator()) {
            Ui.paragraph(panel, getText(R.string.poll_results_private));
            return;
        }
        Ui.button(panel, R.string.poll_results, R.drawable.ic_poll, () ->
                host().repository().action("pollResults", Map.of("id", poll.id())).addOnCompleteListener(result -> {
                    if (binding == null || !isResumed() || !panel.isAttachedToWindow()) return;
                    if (result.isSuccessful() && result.getResult().get("counts") instanceof List<?> counts) {
                        detail(getText(R.string.poll_results), poll.text("title"),
                                results -> renderResults(results, options, counts));
                    } else feedback(R.string.generic_error);
                }));
    }

    private void renderResults(LinearLayout panel, List<String> options, List<?> counts) {
        Ui.heading(panel, R.string.poll_results);
        List<Double> shares = PollMath.shares(counts);
        NumberFormat percent = NumberFormat.getPercentInstance(InputValidation.CHILE);
        percent.setMaximumFractionDigits(1);
        for (int index = 0; index < options.size(); index++) {
            long count = index < counts.size() ? PollMath.asCount(counts.get(index)) : 0L;
            double share = index < shares.size() ? shares.get(index) : 0;
            Ui.paragraph(panel, getResources().getQuantityString(R.plurals.poll_result_row,
                    count == 1 ? 1 : 2, options.get(index), count, percent.format(share)));
        }
    }

    private void vote(CommunityRecord poll, List<String> options) {
        List<String> choices = new ArrayList<>();
        for (int index = 0; index < options.size(); index++) choices.add(Integer.toString(index));
        showForm(R.string.poll_vote, 0, R.string.confirm, R.string.poll_voted_feedback, "vote",
                FormDialog.target("id", poll.id()), Field.choice("choice", R.string.poll_choice, "", choices, options));
    }
}
