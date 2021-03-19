package com.me.healthplan.utility;

public class AuthorizationErrorResponse {

    private final String error;
    private final String message;

    public AuthorizationErrorResponse(String message, String error) {
        this.message = message;
        this.error = error;
    }

    public String getMessage() {
        return message;
    }

    public String getError() {
        return error;
    }
}