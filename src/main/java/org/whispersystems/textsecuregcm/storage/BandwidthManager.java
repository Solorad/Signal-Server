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
import java.math.BigDecimal;
import java.util.*;

public class BandwidthManager {
    public static final String QUANTITY = "quantity";
    public static final String LOCAL_ADDRESS = "availableNumbers/local";
    public static final String TOLL_FREE_ADDRESS = "availableNumbers/tollFree";
    private final Logger logger = LoggerFactory.getLogger(BandwidthManager.class);

    private final BandwidthClient bandwidthClient;
    private final AccountNumbers accountNumbers;
    private final AccountsManager accountsManager;

    public BandwidthManager(BandwidthConfiguration bandwidth,
                            AccountsManager accountsManager,
                            AccountNumbers accountNumbers) {
        this.bandwidthClient = BandwidthClient.getInstance();
        this.bandwidthClient.setCredentials(bandwidth.getUserID(), bandwidth.getApiToken(),
                                                     bandwidth.getApiSecret());
        this.accountsManager = accountsManager;
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
                return updateAccountAndAddInHistory(account, phoneNumbersResponse);
            }
            return phoneNumbersResponse;
        } catch (Exception  e) {
            logger.error("Exception occurred: {}", e);
            return new PhoneNumbersResponse(e.getMessage());
        }
    }

    private PhoneNumbersResponse updateAccountAndAddInHistory(Account account, PhoneNumbersResponse phoneNumbersResponse) {
        PhoneNumber phoneNumber = phoneNumbersResponse.getPhoneNumbers().get(0);
        String price = phoneNumber.getPrice();
        BigDecimal phonePrice = new BigDecimal(price);
        if (account.getBalance() == null || account.getBalance().compareTo(phonePrice) > 0) {
            return new PhoneNumbersResponse("Balance is less than needed to buy new number.");
        }
        account.setSecondPhoneNumber(phoneNumber.getNumber());
        account.setPhoneBuyDate(new Date());
        account.setBalance(account.getBalance().min(phonePrice));
        account.setPhonePrice(phonePrice);
        accountsManager.update(account);
        accountNumbers.insertStep(account.getNumber(), phoneNumber.getNumber());

        return phoneNumbersResponse;
    }
}
