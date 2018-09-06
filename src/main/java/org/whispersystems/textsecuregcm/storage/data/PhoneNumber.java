package org.whispersystems.textsecuregcm.storage.data;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class PhoneNumber {
    @JsonProperty
    private String number;
    @JsonProperty
    private String nationalNumber;
    @JsonProperty
    private String city;
    @JsonProperty
    private String rateCenter;
    @JsonProperty
    private String state;
    @JsonProperty
    private String price;
}
