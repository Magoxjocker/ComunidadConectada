package com.example.comunidadconectada.data;

import com.google.firebase.database.DataSnapshot;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public record CommunityRecord(String id, Map<?, ?> fields) {
    public static CommunityRecord from(DataSnapshot snapshot) {
        return new CommunityRecord(snapshot.getKey(), snapshot.getValue() instanceof Map<?, ?> values ? values : Map.of());
    }

    public String text(String field) {
        return fields.get(field) instanceof String value ? value : "";
    }

    public long number(String field) {
        return fields.get(field) instanceof Number value ? value.longValue() : 0;
    }

    public boolean flag(String field) {
        return Boolean.TRUE.equals(fields.get(field));
    }

    public List<?> list(String field) {
        if (fields.get(field) instanceof List<?> values) return values;
        if (fields.get(field) instanceof Map<?, ?> values) {
            List<Object> ordered = new ArrayList<>();
            for (int position = 0; position < values.size(); position++) {
                ordered.add(values.get(String.valueOf(position)));
            }
            return ordered;
        }
        return List.of();
    }

    public List<CommunityRecord> children(String field) {
        if (!(fields.get(field) instanceof Map<?, ?> values)) return List.of();
        List<CommunityRecord> records = new ArrayList<>();
        values.forEach((key, value) -> {
            if (key instanceof String childId && value instanceof Map<?, ?> child) {
                records.add(new CommunityRecord(childId, child));
            }
        });
        return records;
    }
}
