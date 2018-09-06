package org.whispersystems.textsecuregcm.storage.data;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

@Data
public class PhoneNumbersResponse {
    @JsonProperty
    private List<PhoneNumber> phoneNumbers;
    @JsonProperty
    private String errorMessage;

    public PhoneNumbersResponse(List<PhoneNumber> phoneNumbers) {
        this.phoneNumbers = phoneNumbers;
    }

    public PhoneNumbersResponse(String errorMessage) {
        this.errorMessage = errorMessage;
    }
}
