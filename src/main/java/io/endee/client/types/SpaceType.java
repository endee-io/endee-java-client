package io.endee.client.types;

/**
 * Space types for distance calculation.
 */
public enum SpaceType {
    COSINE("cosine"),
    L2("l2"),
    IP("ip");

    private final String value;

    SpaceType(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public static SpaceType fromValue(String value) {
        for (SpaceType t : values()) {
            if (t.value.equalsIgnoreCase(value)) {
                return t;
            }
        }
        throw new IllegalArgumentException("Unknown space type: " + value);
    }

    @Override
    public String toString() {
        return value;
    }
}
