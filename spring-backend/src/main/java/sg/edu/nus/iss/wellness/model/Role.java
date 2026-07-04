package sg.edu.nus.iss.wellness.model;

public enum Role {
    USER,
    PREMIUM_USER;

    /** Spring Security expects role authorities to be prefixed with {@code ROLE_}. */
    public static final String AUTHORITY_PREFIX = "ROLE_";

    /**
     * @return the Spring Security authority string for this role, e.g. {@code ROLE_USER}.
     */
    public String authority() {
        return AUTHORITY_PREFIX + name();
    }

    /**
     * Parses a stored/claim value into a {@link Role}, defaulting to {@link #USER}
     * when the value is missing or unrecognised so legacy rows never break login.
     *
     * @param value the persisted or claimed role string (case-insensitive)
     * @return the matching role, or {@link #USER} as a safe fallback
     */
    public static Role fromValue(String value) {
        if (value == null || value.isBlank()) {
            return USER;
        }
        try {
            return Role.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return USER;
        }
    }
}
