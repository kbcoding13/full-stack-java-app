package com.example.inventory.common;

/** Domain exceptions that map to specific HTTP statuses in {@link GlobalExceptionHandler}. */
public final class ApiExceptions {

    private ApiExceptions() {}

    /** Requested resource does not exist, or is soft-deleted. Maps to 404. */
    public static class NotFoundException extends RuntimeException {
        public NotFoundException(String resource, Object id) {
            super("%s %s was not found".formatted(resource, id));
        }

        public NotFoundException(String message) {
            super(message);
        }
    }

    /** Uniqueness or referential conflict, e.g. duplicate SKU. Maps to 409. */
    public static class ConflictException extends RuntimeException {
        public ConflictException(String message) {
            super(message);
        }
    }

    /** Request is well-formed but violates a business rule, e.g. overselling. Maps to 422. */
    public static class DomainRuleException extends RuntimeException {
        public DomainRuleException(String message) {
            super(message);
        }
    }

    /** Upload or download against object storage failed. Maps to 502. */
    public static class StorageException extends RuntimeException {
        public StorageException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
