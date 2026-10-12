package com.example.funfridaygame.room;

import java.util.regex.Pattern;

/** Validation and normalisation of display names. */
public final class NameRules {

    public static final int MAX_LENGTH = 20;
    private static final Pattern ALLOWED = Pattern.compile("[\\p{L}\\p{N} _.'\\-]+");

    private NameRules() {}

    /**
     * Trims and collapses whitespace, then validates.
     *
     * @throws RoomException with {@link ErrorCode#INVALID_NAME}
     */
    public static String normalize(String raw) {
        String name = raw == null ? "" : raw.trim().replaceAll("\\s+", " ");
        if (name.isEmpty()) {
            throw new RoomException(ErrorCode.INVALID_NAME, "Please enter a name.");
        }
        if (name.length() > MAX_LENGTH) {
            throw new RoomException(
                    ErrorCode.INVALID_NAME,
                    "Names can have at most " + MAX_LENGTH + " characters.");
        }
        if (!ALLOWED.matcher(name).matches()) {
            throw new RoomException(
                    ErrorCode.INVALID_NAME,
                    "Names may contain letters, digits, spaces and . _ ' - only.");
        }
        return name;
    }

    /** A short emoji/text avatar, or the default. */
    public static String avatar(String raw) {
        if (raw == null || raw.isBlank()) {
            return "🙂";
        }
        String avatar = raw.strip();
        return avatar.codePointCount(0, avatar.length()) > 8 ? "🙂" : avatar;
    }
}
