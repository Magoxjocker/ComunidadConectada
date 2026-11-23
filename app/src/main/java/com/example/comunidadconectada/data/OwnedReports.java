package com.example.comunidadconectada.data;

import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.Query;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

public final class OwnedReports implements CommunityRepository.Subscription {
    private final DatabaseReference reference;
    private final String owner;
    private final Consumer<List<CommunityRecord>> result;
    private final Runnable failure;
    private final Page first = new Page(null);

    public OwnedReports(DatabaseReference reference, String owner, Consumer<List<CommunityRecord>> result,
                        Runnable failure) {
        this.reference = reference;
        this.owner = owner;
        this.result = result;
        this.failure = failure;
        watch(first);
    }

    private void watch(Page page) {
        Query ownerRange = reference.orderByChild("owner").startAt(owner);
        Query throughBoundary = page.boundary == null ? ownerRange.endAt(owner) : ownerRange.endAt(owner, page.boundary);
        page.subscription = CommunityRepository.watch(throughBoundary.limitToLast(CommunityRepository.PAGE_SIZE),
                snapshot -> replace(page, CommunityRepository.records(snapshot)), error -> {
                    if (!page.closed) failure.run();
                });
    }

    private void replace(Page page, List<CommunityRecord> records) {
        if (page.closed) return;
        page.records = records;
        String boundary = records.size() == CommunityRepository.PAGE_SIZE ? records.get(0).id() : null;
        if (Objects.equals(boundary, page.boundary)) boundary = null;
        if (page.older != null && !page.older.boundary.equals(boundary)) {
            closeFrom(page.older);
            page.older = null;
        }
        if (page.older == null && boundary != null) {
            page.older = new Page(boundary);
            watch(page.older);
        }
        publish();
    }

    private void publish() {
        Map<String, CommunityRecord> records = new LinkedHashMap<>();
        for (Page page = first; page != null; page = page.older) {
            if (page.records == null) return;
            // The rules allow an inclusive owner range. Keep the freshest copy of its shared boundary.
            for (CommunityRecord record : page.records) records.putIfAbsent(record.id(), record);
        }
        result.accept(new ArrayList<>(records.values()));
    }

    @Override public void close() {
        closeFrom(first);
    }

    private static void closeFrom(Page first) {
        for (Page page = first; page != null; page = page.older) {
            page.closed = true;
            if (page.subscription != null) page.subscription.close();
        }
    }

    private static final class Page {
        private final String boundary;
        private List<CommunityRecord> records;
        private Page older;
        private CommunityRepository.Subscription subscription;
        private boolean closed;

        private Page(String boundary) {
            this.boundary = boundary;
        }
    }
}
