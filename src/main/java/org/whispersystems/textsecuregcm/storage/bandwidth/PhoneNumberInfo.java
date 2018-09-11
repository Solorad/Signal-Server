package org.whispersystems.textsecuregcm.storage.bandwidth;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class PhoneNumberInfo {
    @JsonProperty
    private String id;
    @JsonProperty
    private String application;
    @JsonProperty
    private String number;
    @JsonProperty
    private String nationalNumber;
    @JsonProperty
    private String name;
    @JsonProperty
    private String createdTime;
    @JsonProperty
    private String city;
    @JsonProperty
    private String state;
    @JsonProperty
    private String price;
    @JsonProperty
    private String numberState;
}
