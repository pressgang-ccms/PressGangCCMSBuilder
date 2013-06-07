package com.redhat.contentspec.builder.exception;

public class BuildProcessingException extends Exception {
    private static final long serialVersionUID = 7574806371186221384L;

    public BuildProcessingException(final String message) {
        super(message);
    }

    public BuildProcessingException(final Throwable cause) {
        super(cause);
    }

    public BuildProcessingException(final String message, final Throwable cause) {
        super(message, cause);
    }
}
