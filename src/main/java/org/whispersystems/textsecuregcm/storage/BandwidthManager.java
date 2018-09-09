package org.whispersystems.textsecuregcm.storage;


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

import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class BandwidthManager {
    public static final String QUANTITY = "quantity";
    public static final String LOCAL_ADDRESS = "availableNumbers/local";
    public static final String TOLL_FREE_ADDRESS = "availableNumbers/tollFree";
    private final Logger logger = LoggerFactory.getLogger(BandwidthManager.class);

    private final BandwidthClient bandwidthClient;
    private final AccountNumbers accountNumbers;
    private final Accounts accounts;

    public BandwidthManager(BandwidthConfiguration bandwidth,
                            Accounts accounts, AccountNumbers accountNumbers) {
        this.bandwidthClient = BandwidthClient.getInstance();
        this.bandwidthClient.setCredentials(bandwidth.getUserID(), bandwidth.getApiToken(),
                                                     bandwidth.getApiSecret());
        this.accounts = accounts;
        this.accountNumbers = accountNumbers;
    }


    public PhoneNumbersResponse getAvailableTollFreeNumbers(Map<String, Object> parameters) {
        return getNumbers(TOLL_FREE_ADDRESS, parameters);
    }

    public PhoneNumbersResponse getAvailableLocalNumbers(Map<String, Object> parameters) {
        return getNumbers(LOCAL_ADDRESS, parameters);
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
     *
     * @param account
     * @param parameters
     * @return
     */

    public PhoneNumbersResponse orderAvailableLocalNumbers(Account account,
                                                           Map<String, Object> parameters) {
        return orderNumberForAccount(account, LOCAL_ADDRESS, parameters);
    }

    /**
     * Order available toll free numbers
     *
     * @param account
     * @param parameters
     * @return
     */
    public PhoneNumbersResponse orderAvailableTollFreeNumbers(Account account,
                                                              Map<String, Object> parameters) {
        return orderNumberForAccount(account, TOLL_FREE_ADDRESS, parameters);
    }

    private PhoneNumbersResponse orderNumberForAccount(Account account, String address,
                                                       Map<String, Object> parameters) {
        try {
            parameters.put(QUANTITY, 1);
            final StringBuilder sb = new StringBuilder(address);
            sb.append("?");
            for (Map.Entry<String, Object> entry : parameters.entrySet()) {
                sb.append(entry.getKey()).append("=").append(entry.getValue().toString()).append("&");
            }
            RestResponse restResponse = bandwidthClient.post(sb.toString(), new HashMap<>());
            PhoneNumbersResponse phoneNumbersResponse = parsePhoneNumberResponse(restResponse);
            if (phoneNumbersResponse.getErrorMessage() == null && phoneNumbersResponse.getPhoneNumbers().size() > 0) {
                updateAccountAndAddInHistory(account, phoneNumbersResponse);
            }
            return phoneNumbersResponse;
        } catch (Exception  e) {
            logger.error("Exception occurred: {}", e);
            return new PhoneNumbersResponse(e.getMessage());
        }
    }

    private void updateAccountAndAddInHistory(Account account, PhoneNumbersResponse phoneNumbersResponse) {
        PhoneNumber phoneNumber = phoneNumbersResponse.getPhoneNumbers().get(0);
        account.setSecondPhoneNumber(phoneNumber.getNumber());
        accounts.update(account);
        accountNumbers.insertStep(account.getNumber(), phoneNumber.getNumber());
    }
}
