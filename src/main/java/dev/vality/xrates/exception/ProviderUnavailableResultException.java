package dev.vality.xrates.exception;

import dev.vality.woody.api.flow.error.WUnavailableResultException;

public class ProviderUnavailableResultException extends WUnavailableResultException {

    public ProviderUnavailableResultException(String message) {
        super(message);
    }

    public ProviderUnavailableResultException(String message, Throwable cause) {
        super(message, cause);
    }

    public ProviderUnavailableResultException(Throwable cause) {
        super(cause);
    }

}
