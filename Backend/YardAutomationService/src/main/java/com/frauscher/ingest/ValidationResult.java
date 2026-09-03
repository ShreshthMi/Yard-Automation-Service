package com.frauscher.ingest;

/** Outcome of validating a packet: whether it passed, and if not, why. */
public record ValidationResult(boolean valid, String reason) {
    public static ValidationResult ok() {
        return new ValidationResult(true, null);
    }
    public static ValidationResult invalid(String reason) {
        return new ValidationResult(false, reason);
    }
}