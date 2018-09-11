package org.whispersystems.textsecuregcm.storage;


import com.bandwidth.sdk.BandwidthClient;
import com.bandwidth.sdk.RestResponse;
import com.google.common.reflect.TypeToken;
import com.google.gson.Gson;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.whispersystems.textsecuregcm.configuration.BandwidthConfiguration;
import org.whispersystems.textsecuregcm.redis.ReplicatedJedisPool;
import org.whispersystems.textsecuregcm.storage.bandwidth.*;
import redis.clients.jedis.Jedis;

import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.util.*;

public class BandwidthManager {
    public static final String LOCAL_ADDRESS = "availableNumbers/local";
    public static final String TOLL_FREE_ADDRESS = "availableNumbers/tollFree";

    public static final String BANDWIDTH_NUMBER_PREFIX = "bandwidthNumber_";
    private final Logger logger = LoggerFactory.getLogger(BandwidthManager.class);

    private final BandwidthClient bandwidthClient;
    private final AccountNumbers accountNumbers;
    private final AccountsManager accountsManager;
    private final ReplicatedJedisPool cacheClient;

    public BandwidthManager(BandwidthConfiguration bandwidth,
                            AccountsManager accountsManager,
                            AccountNumbers accountNumbers,
                            ReplicatedJedisPool cacheClient) {
        this.cacheClient = cacheClient;
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
            // 1. parse results from bandwidth
            Type numberListType = new TypeToken<List<PhoneNumber>>() {}.getType();
            List<PhoneNumber> phoneNumbers = new Gson().fromJson(restResponse.getResponseText(), numberListType);

            // 2. persist all phones into local storage
            for (PhoneNumber phoneNumber : phoneNumbers) {
                setInCache(phoneNumber.getNumber(), phoneNumber.getPrice());
            }
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
     * @param phoneNumberRequest
     * @return
     */
    public PhoneNumbersResponse orderPhoneNumber(Account account,
                                                 PhoneNumberRequest phoneNumberRequest) {
        try {
            String request = new Gson().toJson(phoneNumberRequest);
            // 1. validate account balance is enough to buy phone
            String error = validateUserBalanceForBuy(account, phoneNumberRequest);
            if (StringUtils.isNotEmpty(error)) {
                return new PhoneNumbersResponse(error);
            }
            // 2. buy phone
            RestResponse restResponse = bandwidthClient.postJson("phoneNumbers ", request);
            PhoneNumbersResponse phoneNumbersResponse = parsePhoneNumberResponse(restResponse);
            // 3. Analyze buy results and add results into user's history.
            if (phoneNumbersResponse.getErrorMessage() == null && phoneNumbersResponse.getPhoneNumbers().size() > 0) {
                return updateAccountAndAddInHistory(account, phoneNumbersResponse);
            }
            return phoneNumbersResponse;
        } catch (Exception  e) {
            logger.error("Exception occurred: {}", e);
            return new PhoneNumbersResponse(e.getMessage());
        }
    }

    /**
     *
     * @param account
     * @param phoneNumberRequest
     * @return error message
     */
    private String validateUserBalanceForBuy(Account account, PhoneNumberRequest phoneNumberRequest) {
        String phonePrice = getFromCache(phoneNumberRequest.getNumber());
        if (phonePrice == null) {
            PhoneNumberInfo phoneNumberInfo = getPhoneInfo(phoneNumberRequest.getNumber());
            if (phoneNumberInfo == null) {
                logger.error("No info about this number in bandwidth");
                return "No info about this number in bandwidth";
            }
            if (!phoneNumberInfo.getNumberState().equals("enabled")) {
                logger.error("Phone is already in number state '{}'", phoneNumberInfo.getNumberState());
                return "Phone is already in number state '" + phoneNumberInfo.getNumberState() + "'.";
            }
        }
        if (phonePrice == null) {
            logger.error("Phone price for number '{}' is still unknown.", phoneNumberRequest.getNumber());
            return "Phone price is unknown";
        }
        if (account.getBalance() == null) {
            logger.error("Account '{}' balance is null.", account.getNumber());
            return "Account balance is null";
        }
        boolean isEnoughMoney = account.getBalance().compareTo(new BigDecimal(phonePrice)) > 0;
        return isEnoughMoney ? null : "Not enough money to buy this phone.";
    }

    private PhoneNumberInfo getPhoneInfo(String number) {
        try {
            String encodedNumber = number.replaceAll("\\+", "%20");
            RestResponse restResponse = bandwidthClient.get("phoneNumbers/" + encodedNumber, new HashMap<>());
            if (restResponse.getStatus() < 400) {
                return new Gson().fromJson(restResponse.getResponseText(), PhoneNumberInfo.class);
            } else {
              logger.error("Status is invalid: {}", restResponse.getStatus());
              return null;
            }
        } catch (Exception e) {
            logger.error("Exception occurred", e);
        }
        return null;
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


    private String getKey(String number) {
        return BANDWIDTH_NUMBER_PREFIX + number;
    }

    private void setInCache(String number, String price) {
        try (Jedis jedis = cacheClient.getWriteResource()) {
            // store prices for 1 hour after search
            jedis.set(getKey(number), price, null, "ex", 3600);
        }
    }

    private String getFromCache(String number) {
        try (Jedis jedis = cacheClient.getReadResource()) {
            return jedis.get(getKey(number));
        }
    }
}
