package com.linewell.dataelement.metautil.exception;

/**
 * Preserves the provider exception chain when a connectivity probe fails.
 */
public class DataSourceConnectionException extends RuntimeException {

    public DataSourceConnectionException(String message, Throwable cause) {
        super(message, cause);
    }
}
