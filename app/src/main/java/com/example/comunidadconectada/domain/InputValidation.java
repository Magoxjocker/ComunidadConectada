package com.example.comunidadconectada.domain;

import java.text.Normalizer;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

public final class InputValidation {
    public static final Locale CHILE = Locale.forLanguageTag("es-CL");
    private static final ZoneId SANTIAGO = ZoneId.of("America/Santiago");
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd/MM/uuuu HH:mm", CHILE)
            .withResolverStyle(ResolverStyle.STRICT);
    private static final long MIN_POLL_LEAD_MS = 60_000;
    private static final long MAX_POLL_DURATION_MS = 90L * 24 * 60 * 60 * 1000;
    private static final Pattern EMAIL = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");

    private InputValidation() {}

    public static String clean(CharSequence value) {
        if (value == null) return "";
        return Normalizer.normalize(value, Normalizer.Form.NFC)
                .replaceAll("^[\\s\\p{Z}]+|[\\s\\p{Z}]+$", "");
    }

    public static String searchKey(CharSequence value) {
        return Normalizer.normalize(clean(value), Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "").toLowerCase(Locale.ROOT);
    }

    public static String email(CharSequence value) {
        return clean(value).toLowerCase(Locale.ROOT);
    }

    public static boolean validEmail(String email) {
        return email.length() <= 254 && EMAIL.matcher(email).matches();
    }

    public static boolean strongPassword(String password) {
        return password.length() >= 8 && password.length() <= 128 && password.equals(clean(password))
                && password.codePoints().anyMatch(Character::isLetter)
                && password.codePoints().anyMatch(Character::isDigit);
    }

    public static boolean validReportResolution(String status, String note) {
        return !"resolved".equals(status) || !clean(note).isEmpty();
    }

    public static List<String> pollOptions(String input) {
        List<String> options = new ArrayList<>();
        Set<String> distinct = new HashSet<>();
        for (String line : input.split("\\r?\\n")) {
            String option = clean(line);
            if (option.isEmpty()) continue;
            if (option.length() > 100 || !distinct.add(searchKey(option))) return List.of();
            options.add(option);
        }
        return options.size() >= 2 && options.size() <= 6 ? options : List.of();
    }

    public static Long closeDate(String input, long now) {
        try {
            LocalDateTime date = LocalDateTime.parse(input, DATE_TIME);
            List<ZoneOffset> offsets = SANTIAGO.getRules().getValidOffsets(date);
            if (offsets.size() != 1) return null;
            long timestamp = date.toInstant(offsets.get(0)).toEpochMilli();
            return timestamp >= now + MIN_POLL_LEAD_MS && timestamp <= now + MAX_POLL_DURATION_MS ? timestamp : null;
        } catch (DateTimeParseException ignored) {
            return null;
        }
    }
}
