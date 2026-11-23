package com.example.comunidadconectada.viewmodel;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.comunidadconectada.data.CommunityRecord;
import com.example.comunidadconectada.data.CommunityRepository;
import com.example.comunidadconectada.data.OwnedReports;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.Query;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

public final class FeedViewModel extends ViewModel {
    public record State(boolean loading, boolean paging, boolean failed, boolean older, List<CommunityRecord> records) {}
    private final MutableLiveData<State> state = new MutableLiveData<>(new State(true, false, false, false, List.of()));
    private final Map<String, CommunityRecord> older = new LinkedHashMap<>();
    private CommunityRepository.Subscription subscription;
    private Query query;
    private List<CommunityRecord> latest = List.of();
    private Function<CommunityRecord, Query> historyQuery;
    private CommunityRecord cursor;
    private boolean immutableRecords;
    private boolean hasOlder;
    private boolean paging;
    private int generation;
    private String owner;
    private int ownReportsLimit;

    public LiveData<State> state() { return state; }

    public void load(Query listQuery, Function<CommunityRecord, Query> historyQuery, boolean immutableRecords) {
        if (listQuery.equals(query) && owner == null && !state.getValue().failed()) return;
        int currentGeneration = beginLoad(listQuery, null);
        this.historyQuery = historyQuery;
        this.immutableRecords = immutableRecords;
        cursor = null;
        older.clear();
        latest = List.of();
        hasOlder = false;
        paging = false;
        state.setValue(new State(true, false, false, false, List.of()));
        subscription = CommunityRepository.watch(query, snapshot -> {
            if (currentGeneration != generation) return;
            if (this.immutableRecords && !older.isEmpty()) {
                for (CommunityRecord record : latest) older.put(record.id(), record);
            }
            latest = CommunityRepository.records(snapshot);
            if (older.isEmpty()) {
                hasOlder = this.historyQuery != null && latest.size() == CommunityRepository.PAGE_SIZE;
                cursor = hasOlder ? latest.get(0) : null;
            }
            update(false);
        }, error -> {
            if (currentGeneration == generation) update(true);
        });
    }

    public void loadOwnReports(DatabaseReference reference, String ownerUid) {
        if (reference.equals(query) && Objects.equals(owner, ownerUid) && !state.getValue().failed()) return;
        int currentGeneration = beginLoad(reference, ownerUid);
        ownReportsLimit = CommunityRepository.PAGE_SIZE;
        cursor = null;
        older.clear();
        latest = List.of();
        hasOlder = false;
        paging = false;
        state.setValue(new State(true, false, false, false, List.of()));
        subscription = new OwnedReports(reference, ownerUid, records -> {
            if (currentGeneration != generation) return;
            latest = records;
            update(false);
        }, () -> {
            if (currentGeneration == generation) update(true);
        });
    }

    private int beginLoad(Query source, String ownerUid) {
        if (subscription != null) subscription.close();
        query = source;
        owner = ownerUid;
        return ++generation;
    }

    public void loadOlder() {
        if (owner != null) {
            if (hasOlder) {
                ownReportsLimit += CommunityRepository.PAGE_SIZE;
                update(false);
            }
            return;
        }
        if (!hasOlder || paging || cursor == null) return;
        int currentGeneration = generation;
        if (immutableRecords) {
            for (CommunityRecord record : latest) older.put(record.id(), record);
        }
        CommunityRecord boundary = cursor;
        paging = true;
        update(false);
        historyQuery.apply(boundary).get().addOnCompleteListener(result -> {
                    if (currentGeneration != generation) return;
                    paging = false;
                    if (!result.isSuccessful()) {
                        update(true);
                        return;
                    }
                    List<CommunityRecord> page = CommunityRepository.records(result.getResult());
                    boolean fullPage = page.size() == CommunityRepository.PAGE_SIZE;
                    page.removeIf(record -> record.id().equals(boundary.id()));
                    for (CommunityRecord record : page) older.put(record.id(), record);
                    hasOlder = fullPage && !page.isEmpty();
                    cursor = hasOlder ? page.get(0) : null;
                    update(false);
                });
    }

    private void update(boolean failed) {
        Map<String, CommunityRecord> combined = new LinkedHashMap<>(older);
        for (CommunityRecord record : latest) combined.put(record.id(), record);
        List<CommunityRecord> displayed = new ArrayList<>(combined.values());
        displayed.sort(Comparator.comparingLong((CommunityRecord record) -> record.number("createdAt"))
                .thenComparing(CommunityRecord::id));
        if (owner != null) {
            hasOlder = displayed.size() > ownReportsLimit;
            if (hasOlder) displayed = displayed.subList(displayed.size() - ownReportsLimit, displayed.size());
        }
        state.setValue(new State(false, paging, failed, hasOlder, displayed));
    }

    @Override protected void onCleared() {
        generation++;
        if (subscription != null) subscription.close();
    }
}
