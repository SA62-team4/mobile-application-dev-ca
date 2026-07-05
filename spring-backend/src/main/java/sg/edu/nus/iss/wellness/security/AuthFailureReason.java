package sg.edu.nus.iss.wellness.security;

/**
 * Why authentication did not succeed for a request, set by {@link JwtAuthenticationFilter}
 * and read by {@link LoginRedirectAuthenticationEntryPoint} to decide between a 302 login
 * redirect and a plain 401.
 *
 * @author JustinChua97
 */
public enum AuthFailureReason {
    /** No bearer token was supplied. */
    MISSING,

    /** A bearer token was supplied but has expired. */
    EXPIRED,

    /** A bearer token was supplied but could not be parsed/verified (bad signature, garbage, etc.). */
    MALFORMED,

    /** The token parsed correctly but the referenced account is unknown or disabled. */
    USER_INVALID;

    /** Request attribute key under which the reason is stored for the current request. */
    public static final String ATTRIBUTE = "sg.edu.nus.iss.wellness.AUTH_FAILURE_REASON";
}
