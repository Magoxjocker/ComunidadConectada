package com.example.comunidadconectada.data;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.TaskCompletionSource;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.ServerValue;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class SparkCommands {
    private final DatabaseReference root;
    private final FirebaseAuth authentication;

    SparkCommands(DatabaseReference root, FirebaseAuth authentication) {
        this.root = root;
        this.authentication = authentication;
    }

    Task<Map<?, ?>> execute(String command, Map<String, Object> fields) {
        FirebaseUser user = authentication.getCurrentUser();
        if (user == null) return denied();
        String uid = user.getUid();
        try {
            if ("registerProfile".equals(command)) {
                return write(Map.of("accounts/" + uid, Map.of(
                        "name", string(fields, "name"), "unit", string(fields, "unit"),
                        "role", "neighbor", "community", "")), Map.of());
            }
            if ("requestMembership".equals(command)) return requestMembership(user, fields);
            return root.child("accounts").child(uid).get().onSuccessTask(account -> {
                String community = value(account, "community");
                if (community.isEmpty()) return denied();
                DatabaseReference reference = root.child("communityData").child(community);
                return reference.child("members").child(uid).get().onSuccessTask(profile -> {
                    if (!Boolean.TRUE.equals(profile.child("active").getValue(Boolean.class))) return denied();
                    return dispatch(command, fields, new Membership(uid, community, profile, reference));
                });
            });
        } catch (IllegalArgumentException exception) {
            return Tasks.forException(exception);
        }
    }

    private Task<Map<?, ?>> requestMembership(FirebaseUser user, Map<String, Object> fields) {
        return root.child("accounts").child(user.getUid()).get().onSuccessTask(account ->
                write(Map.of("joinRequests/" + user.getUid(), Map.of(
                        "community", key(fields, "community"), "name", value(account, "name"),
                        "unit", value(account, "unit"), "email", user.getEmail())), Map.of()));
    }

    private Task<Map<?, ?>> dispatch(String command, Map<String, Object> fields, Membership actor) {
        return switch (command) {
            case "renameProfile" -> write(Map.of("accounts/" + actor.uid() + "/name", string(fields, "name"),
                    actor.path("members/" + actor.uid() + "/name"), string(fields, "name")), Map.of());
            case "addNeighbor" -> approve(actor, key(fields, "uid"));
            case "setNeighborAccess" -> write(actor, "members/" + key(fields, "uid") + "/active",
                    bool(fields, "active"));
            case "sendMessage" -> create(actor, "messages", fields, Map.of(
                    "text", string(fields, "text"), "community", actor.community()));
            case "saveSale" -> save(actor, "marketplace", fields, Map.of(
                    "title", string(fields, "title"), "description", string(fields, "description"),
                    "price", number(fields, "price"), "category", string(fields, "category"),
                    "status", string(fields, "status")));
            case "deleteSale" -> remove(actor, "marketplace", key(fields, "id"));
            case "saveAnnouncement" -> save(actor, "announcements", fields, Map.of(
                    "title", string(fields, "title"), "description", string(fields, "description"),
                    "priority", string(fields, "priority"), "status", "published"));
            case "deleteAnnouncement" -> remove(actor, "announcements", key(fields, "id"));
            case "createAlert" -> create(actor, "alerts", fields, Map.of(
                    "category", string(fields, "category"), "description", string(fields, "description"),
                    "community", actor.community(), "status", "active", "delivery", "pending"));
            case "setAlertStatus" -> edit(actor, "alerts", key(fields, "id"), Map.of(
                    "status", string(fields, "status"), "statusChangedAt", ServerValue.TIMESTAMP, "statusChangedBy", actor.uid()));
            case "createReport" -> create(actor, "reports", fields, Map.of(
                    "title", string(fields, "title"), "description", string(fields, "description"),
                    "location", string(fields, "location"), "category", string(fields, "category"), "status", "pending"));
            case "updateReport" -> updateReport(actor, fields);
            case "createPoll" -> create(actor, "polls", fields, Map.of(
                    "title", string(fields, "title"), "options", options(fields), "closesAt", number(fields, "closesAt"),
                    "showResults", bool(fields, "showResults"), "closed", false, "sparkVersion", 1));
            case "closePoll" -> write(actor, "polls/" + key(fields, "id") + "/closed", true);
            case "vote" -> vote(actor, key(fields, "id"), number(fields, "choice"));
            case "pollResults" -> results(actor, key(fields, "id"));
            case "notificationDevice" -> saveDevice(actor, fields);
            default -> Tasks.forException(new IllegalArgumentException("La operación no está disponible."));
        };
    }

    private Task<Map<?, ?>> approve(Membership actor, String uid) {
        return root.child("joinRequests").child(uid).get().onSuccessTask(request -> {
            if (!actor.community().equals(value(request, "community"))) {
                return Tasks.forException(new IllegalArgumentException("La solicitud no corresponde a tu comunidad."));
            }
            Map<String, Object> membership = Map.of("name", value(request, "name"),
                    "unit", value(request, "unit"), "email", value(request, "email"),
                    "role", "neighbor", "active", true);
            return write(Map.of("accounts/" + uid + "/community", actor.community(),
                    actor.path("members/" + uid), membership), Map.of());
        });
    }

    private Task<Map<?, ?>> create(Membership actor, String collection, Map<String, Object> input,
                                   Map<String, Object> fields) {
        String id = actor.uid() + "_" + key(input, "requestId");
        Map<String, Object> record = new LinkedHashMap<>(fields);
        record.put("owner", actor.uid());
        record.put("author", value(actor.profile(), "name"));
        record.put("unit", value(actor.profile(), "unit"));
        record.put("createdAt", ServerValue.TIMESTAMP);
        return write(Map.of(actor.path(collection + "/" + id), record), Map.of("id", id));
    }

    private Task<Map<?, ?>> save(Membership actor, String collection, Map<String, Object> input,
                                 Map<String, Object> fields) {
        return string(input, "id").isEmpty() ? create(actor, collection, input, fields)
                : edit(actor, collection, key(input, "id"), fields);
    }

    private Task<Map<?, ?>> edit(Membership actor, String collection, String id, Map<String, Object> fields) {
        Map<String, Object> changes = new LinkedHashMap<>();
        fields.forEach((property, fieldValue) -> changes.put(actor.path(collection + "/" + id + "/" + property), fieldValue));
        return write(changes, Map.of("id", id));
    }

    private Task<Map<?, ?>> remove(Membership actor, String collection, String id) {
        Map<String, Object> changes = new LinkedHashMap<>();
        changes.put(actor.path(collection + "/" + id), null);
        return write(changes, Map.of("id", id));
    }

    private Task<Map<?, ?>> updateReport(Membership actor, Map<String, Object> fields) {
        String id = key(fields, "id");
        String changeId = key(fields, "requestId");
        String status = string(fields, "status");
        Map<String, Object> event = Map.of("status", status, "note", string(fields, "note"),
                "author", value(actor.profile(), "name"), "createdAt", ServerValue.TIMESTAMP);
        return edit(actor, "reports", id, Map.of("status", status, "lastChangeId", changeId,
                "history/" + changeId, event));
    }

    private Task<Map<?, ?>> vote(Membership actor, String id, long choice) {
        return write(Map.of(actor.path("pollLedger/" + id + "/votes/" + actor.uid()), choice,
                actor.path("pollTotals/" + id + "/" + choice), ServerValue.increment(1L)), Map.of("id", id));
    }

    private Task<Map<?, ?>> results(Membership actor, String id) {
        return actor.reference().child("polls").child(id).get().onSuccessTask(poll -> {
            List<Long> counts = new ArrayList<>();
            if (!Long.valueOf(1).equals(poll.child("sparkVersion").getValue(Long.class))) {
                if (!Boolean.TRUE.equals(poll.child("showResults").getValue(Boolean.class))) {
                    return Tasks.forException(new IllegalArgumentException("Los resultados anteriores requieren migración."));
                }
                for (DataSnapshot option : poll.child("options").getChildren()) {
                    Long count = poll.child("counts").child(option.getKey()).getValue(Long.class);
                    counts.add(count == null ? 0 : count);
                }
                return Tasks.forResult(Map.of("counts", counts));
            }
            return actor.reference().child("pollTotals").child(id).get().onSuccessTask(totals -> {
                for (DataSnapshot option : poll.child("options").getChildren()) {
                    Long count = totals.child(option.getKey()).getValue(Long.class);
                    counts.add(count == null ? 0 : count);
                }
                return Tasks.forResult(Map.of("counts", counts));
            });
        });
    }

    private Task<Map<?, ?>> saveDevice(Membership actor, Map<String, Object> fields) {
        String token = string(fields, "token");
        String path = "devices/" + actor.uid() + "/" + tokenHash(token);
        if (!bool(fields, "enabled")) {
            Map<String, Object> changes = new LinkedHashMap<>();
            changes.put(actor.path(path), null);
            return write(changes, Map.of());
        }
        return write(actor, path, Map.of("token", token, "updatedAt", ServerValue.TIMESTAMP));
    }

    private Task<Map<?, ?>> write(Membership actor, String path, Object value) {
        return write(Map.of(actor.path(path), value), Map.of());
    }

    private Task<Map<?, ?>> write(Map<String, Object> changes, Map<?, ?> result) {
        TaskCompletionSource<Map<?, ?>> pending = new TaskCompletionSource<>();
        root.updateChildren(changes, (error, reference) -> {
            if (error == null) pending.setResult(result);
            else pending.setException(new CommunityRepository.OperationFailure(error));
        });
        return pending.getTask();
    }

    private static Task<Map<?, ?>> denied() {
        return Tasks.forException(new CommunityRepository.OperationFailure(
                DatabaseError.PERMISSION_DENIED, null));
    }

    private static String value(DataSnapshot snapshot, String field) {
        String value = snapshot.child(field).getValue(String.class);
        return value == null ? "" : value;
    }

    private static String string(Map<String, Object> fields, String field) {
        Object input = fields.get(field);
        return input instanceof String text ? text.trim() : "";
    }

    private static String key(Map<String, Object> fields, String field) {
        String key = string(fields, field);
        if (!key.matches("[A-Za-z0-9_-]{1,180}")) {
            throw new IllegalArgumentException("El identificador indicado no es válido.");
        }
        return key;
    }

    private static boolean bool(Map<String, Object> fields, String field) {
        if (fields.get(field) instanceof Boolean enabled) return enabled;
        throw new IllegalArgumentException("El campo debe indicar sí o no.");
    }

    private static long number(Map<String, Object> fields, String field) {
        Object input = fields.get(field);
        if (input instanceof Integer || input instanceof Long) return ((Number) input).longValue();
        try {
            return Long.parseLong(string(fields, field));
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("El campo debe contener un número entero.");
        }
    }

    private static List<String> options(Map<String, Object> fields) {
        if (!(fields.get("options") instanceof List<?> choices)) {
            throw new IllegalArgumentException("Ingresa las alternativas de la encuesta.");
        }
        List<String> labels = new ArrayList<>();
        for (Object choice : choices) {
            if (!(choice instanceof String label)) throw new IllegalArgumentException("La alternativa no es válida.");
            labels.add(label.trim());
        }
        return labels;
    }

    private static String tokenHash(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte octet : digest) {
                hex.append(Character.forDigit((octet & 0xff) >>> 4, 16));
                hex.append(Character.forDigit(octet & 0x0f, 16));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 no está disponible.", exception);
        }
    }

    private record Membership(String uid, String community, DataSnapshot profile, DatabaseReference reference) {
        String path(String relative) {
            return "communityData/" + community + "/" + relative;
        }
    }
}
