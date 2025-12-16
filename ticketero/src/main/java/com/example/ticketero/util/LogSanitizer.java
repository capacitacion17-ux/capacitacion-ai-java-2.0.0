package com.example.ticketero.util;

/**
 * Utility class for sanitizing values before logging.
 * Prevents Log Injection attacks (CWE-117).
 *
 * <p>Log injection occurs when an attacker can inject malicious content
 * into log files by including special characters (newlines, tabs) in
 * user-controlled input.</p>
 *
 * <p>Usage example:</p>
 * <pre>
 * log.info("User {} logged in", LogSanitizer.sanitize(username));
 * </pre>
 */
public final class LogSanitizer {

    private LogSanitizer() {
        // Utility class - prevent instantiation
    }

    /**
     * Sanitizes a value for safe logging by replacing control characters.
     *
     * @param value the value to sanitize (can be null)
     * @return sanitized string safe for logging
     */
    public static String sanitize(Object value) {
        if (value == null) {
            return "null";
        }
        return value.toString()
                .replace("\n", "_")
                .replace("\r", "_")
                .replace("\t", "_");
    }
}
