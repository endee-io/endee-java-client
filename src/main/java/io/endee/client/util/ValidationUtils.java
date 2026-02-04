package io.endee.client.util;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Validation utilities.
 */
public final class ValidationUtils {

    private static final Pattern INDEX_NAME_PATTERN = Pattern.compile("^[a-zA-Z0-9_]+$");
    private static final int MAX_INDEX_NAME_LENGTH = 48;

    private ValidationUtils() {}

    /**
     * Validates an index name.
     * Must be alphanumeric with underscores, less than 48 characters.
     */
    public static boolean isValidIndexName(String name) {
        if (name == null || name.isEmpty()) {
            return false;
        }
        if (name.length() >= MAX_INDEX_NAME_LENGTH) {
            return false;
        }
        return INDEX_NAME_PATTERN.matcher(name).matches();
    }

    /**
     * Validates that all vector IDs are non-empty and unique.
     */
    public static void validateVectorIds(List<String> ids) {
        Set<String> seenIds = new HashSet<>();
        Set<String> duplicateIds = new HashSet<>();

        for (String id : ids) {
            if (id == null || id.isEmpty()) {
                throw new IllegalArgumentException("All vectors must have a non-empty ID");
            }
            if (seenIds.contains(id)) {
                duplicateIds.add(id);
            } else {
                seenIds.add(id);
            }
        }

        if (!duplicateIds.isEmpty()) {
            throw new IllegalArgumentException("Duplicate IDs found: " + String.join(", ", duplicateIds));
        }
    }
}
