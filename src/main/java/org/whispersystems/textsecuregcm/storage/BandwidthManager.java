package org.whispersystems.textsecuregcm.storage;


import com.bandwidth.sdk.AppPlatformException;
import com.bandwidth.sdk.BandwidthClient;
import com.bandwidth.sdk.RestResponse;
import com.google.common.reflect.TypeToken;
import com.google.gson.Gson;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.whispersystems.textsecuregcm.configuration.BandwidthConfiguration;
import org.whispersystems.textsecuregcm.storage.data.BandwidthRequestResponse;
import org.whispersystems.textsecuregcm.storage.data.PhoneNumber;
import org.whispersystems.textsecuregcm.storage.data.PhoneNumbersResponse;

import java.io.IOException;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class BandwidthManager {
    public static final String QUANTITY = "quantity";
    private final Logger logger = LoggerFactory.getLogger(BandwidthManager.class);

    private final BandwidthClient bandwidthClient;

    public BandwidthManager(BandwidthConfiguration bandwidth) {
        bandwidthClient = BandwidthClient.getInstance();
        bandwidthClient.setCredentials(bandwidth.getUserID(), bandwidth.getApiToken(),
                                                     bandwidth.getApiSecret());
    }


    public PhoneNumbersResponse getAvailableTollFreeNumbers(Map<String, Object> parameters) {
        return getNumbers("availableNumbers/tollFree", parameters);
    }

    public PhoneNumbersResponse getAvailableLocalNumbers(Map<String, Object> parameters) {
        return getNumbers("availableNumbers/local", parameters);
    }

    private PhoneNumbersResponse getNumbers(String address, Map<String, Object> parameters) {
        try {
            RestResponse restResponse = bandwidthClient.get(address, parameters);
            return parsePhoneNumberResponse(restResponse);
        } catch (Exception e) {
            logger.error("Exception occurred: {}", e);
            return new PhoneNumbersResponse(e.getMessage());
        }
    }

    private PhoneNumbersResponse parsePhoneNumberResponse(RestResponse restResponse) {
        if (restResponse.getStatus() < 400) {
            Type numberListType = new TypeToken<List<PhoneNumber>>() {}.getType();
            List<PhoneNumber> phoneNumbers = new Gson().fromJson(restResponse.getResponseText(), numberListType);
            return new PhoneNumbersResponse(phoneNumbers);
        } else {
            BandwidthRequestResponse errorResponse = new Gson().fromJson(restResponse.getResponseText(),
                                                                         BandwidthRequestResponse.class);
            return new PhoneNumbersResponse(errorResponse.getMessage());
        }
    }

    /**
     * Order available local numbers
     * @param parameters
     * @return
     */

    public PhoneNumbersResponse orderAvailableLocalNumbers(Map<String, Object> parameters) {
        return orderNumbers("availableNumbers/local", parameters);
    }

    /**
     * Order available toll free numbers
     * @param parameters
     * @return
     */
    public PhoneNumbersResponse orderAvailableTollFreeNumbers(Map<String, Object> parameters) {
        return orderNumbers("availableNumbers/tollFree", parameters);
    }

    private PhoneNumbersResponse orderNumbers(String address, Map<String, Object> parameters) {
        try {
            parameters.put(QUANTITY, 1);
            final StringBuilder sb = new StringBuilder(address);
            sb.append("?");
            for (Map.Entry<String, Object> entry : parameters.entrySet()) {
                sb.append(entry.getKey()).append("=").append(entry.getValue().toString()).append("&");
            }
            RestResponse restResponse = bandwidthClient.post(sb.toString(), new HashMap<>());
            return parsePhoneNumberResponse(restResponse);
        } catch (IOException | AppPlatformException e) {
            logger.error("Exception occurred: {}", e);
            return new PhoneNumbersResponse(e.getMessage());
        }
    }
}
