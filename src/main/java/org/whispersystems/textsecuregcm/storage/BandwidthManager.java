/**
 * Copyright (C) 2013 Open WhisperSystems
 * <p>
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * <p>
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 * <p>
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.whispersystems.textsecuregcm.storage;


import com.bandwidth.sdk.BandwidthClient;
import com.bandwidth.sdk.RestResponse;
import com.google.common.reflect.TypeToken;
import com.google.gson.Gson;
import org.apache.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.whispersystems.textsecuregcm.configuration.BandwidthConfiguration;
import org.whispersystems.textsecuregcm.storage.data.BandwidthRequestResponse;
import org.whispersystems.textsecuregcm.storage.data.PhoneNumber;
import org.whispersystems.textsecuregcm.storage.data.PhoneNumbersResponse;

import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;

public class BandwidthManager {
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
            RestResponse restResponse = bandwidthClient.get("availableNumbers/local", parameters);
            if (restResponse.getStatus() == HttpStatus.SC_OK) {
                Type numberListType = new TypeToken<List<PhoneNumber>>() {}.getType();
                List<PhoneNumber> phoneNumbers = new Gson().fromJson(restResponse.getResponseText(), numberListType);
                return new PhoneNumbersResponse(phoneNumbers);
            } else {
                BandwidthRequestResponse errorResponse = new Gson().fromJson(restResponse.getResponseText(),
                                                                             BandwidthRequestResponse.class);
                return new PhoneNumbersResponse(errorResponse.getMessage());
            }
        } catch (Exception e) {
            logger.error("Exception occurred: {}", e);
            return new PhoneNumbersResponse(e.getMessage());
        }
    }
}
