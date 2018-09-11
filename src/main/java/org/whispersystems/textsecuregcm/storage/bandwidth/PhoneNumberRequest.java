package org.whispersystems.textsecuregcm.storage.bandwidth;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import org.hibernate.validator.constraints.NotEmpty;

@Data
public class PhoneNumberRequest {
    @JsonProperty
    @NotEmpty
    private String number;
    @JsonProperty
    private String name;
    @JsonProperty
    private String applicationId;
    @JsonProperty
    private String fallbackNumber;
}
