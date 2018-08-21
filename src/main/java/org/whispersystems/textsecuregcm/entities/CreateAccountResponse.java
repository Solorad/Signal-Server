package org.whispersystems.textsecuregcm.entities;

public class CreateAccountResponse {
    public final int code;
    public final String message;

    public CreateAccountResponse(int code, String message) {
        this.code = code;
        this.message = message;
    }
}
