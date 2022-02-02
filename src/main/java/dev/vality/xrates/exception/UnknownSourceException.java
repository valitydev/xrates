package dev.vality.xrates.exception;

import dev.vality.woody.api.flow.error.WUndefinedResultException;

public class UnknownSourceException extends WUndefinedResultException {

    public UnknownSourceException(String message) {
        super(message);
    }

    public UnknownSourceException(String message, Throwable cause) {
        super(message, cause);
    }

    public UnknownSourceException(Throwable cause) {
        super(cause);
    }

}
