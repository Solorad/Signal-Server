package org.whispersystems.textsecuregcm.storage.bandwidth;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

@Data
public class BandwidthRequestResponse {
    @JsonProperty
    private String category;
    @JsonProperty
    private String code;
    @JsonProperty
    private String message;
    @JsonProperty
    private List<Detail> details;


    @Data
    public static class Detail {
        private String name;
        private String value;
    }
}
