// File: src/main/java/com/fitclash/service/ApiException.java
package com.fitclash.service;

import org.springframework.http.HttpStatus;

/** A business rule said no, with the status the client should see. */
public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    public ApiException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public static ApiException notFound(String message) {
        return new ApiException(HttpStatus.NOT_FOUND, "not_found", message);
    }

    public static ApiException conflict(String message) {
        return new ApiException(HttpStatus.CONFLICT, "conflict", message);
    }

    public static ApiException badRequest(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, "bad_request", message);
    }

    public static ApiException forbidden(String message) {
        return new ApiException(HttpStatus.FORBIDDEN, "forbidden", message);
    }

    public static ApiException unauthorized(String message) {
        return new ApiException(HttpStatus.UNAUTHORIZED, "unauthorized", message);
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }
}
