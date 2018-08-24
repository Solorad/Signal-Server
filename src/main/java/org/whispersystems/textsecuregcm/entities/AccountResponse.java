package org.whispersystems.textsecuregcm.entities;

public class AccountResponse {
    public final int code;
    public final String message;

    public AccountResponse(int code, String message) {
        this.code = code;
        this.message = message;
    }
}
