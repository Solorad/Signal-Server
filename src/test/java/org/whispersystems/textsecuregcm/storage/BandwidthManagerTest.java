package org.whispersystems.textsecuregcm.storage;

import com.bandwidth.sdk.BandwidthClient;
import com.bandwidth.sdk.RestResponse;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.whispersystems.textsecuregcm.redis.ReplicatedJedisPool;
import org.whispersystems.textsecuregcm.storage.bandwidth.PhoneNumberRequest;
import org.whispersystems.textsecuregcm.storage.bandwidth.PhoneNumbersResponse;
import redis.clients.jedis.Jedis;

import java.math.BigDecimal;
import java.util.HashMap;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.whispersystems.textsecuregcm.storage.BandwidthManager.LOCAL_ADDRESS;
import static org.whispersystems.textsecuregcm.storage.BandwidthManager.TOLL_FREE_ADDRESS;


@RunWith(MockitoJUnitRunner.class)
public class BandwidthManagerTest {

    private BandwidthManager bandwidthManager;
    @Mock
    private AccountsManager accountsManager;
    @Mock
    private AccountNumbers accountNumbers;
    @Mock
    private ReplicatedJedisPool cacheClient;
    @Mock
    private BandwidthClient bandwidthClient;
    @Mock
    private Jedis jedis;

    private Account account;
    private PhoneNumberRequest phoneNumberRequest;
    private String localAddrResponseString = "[{\"number\":\"+16072352031\",\"nationalNumber\":\"(607) 235-2031\",\"city\":\"BINGHAMTON\",\"rateCenter\":\"BINGHAMTON\",\"state\":\"NY\",\"price\":\"0.35\"},{\"number\":\"+16072383779\",\"nationalNumber\":\"(607) 238-3779\",\"city\":\"BINGHAMTON\",\"rateCenter\":\"BINGHAMTON\",\"state\":\"NY\",\"price\":\"0.35\"}]";
    private String tollFreeAddrResponseString = "[{\"number\": \"+16073013587\", \"nationalNumber\": \"(607) 301-3587\", \"city\": \"BIG FLATS\", \"rateCenter\": \"BIG FLATS\", \"state\": \"NY\", \"price\": \"0.35\"}]";
    private String getPhonesErrorResponse = "{\"category\": \"bad-request\", \"code\": \"invalid-search-option\", \"message\": \"The state abbreviation asd is not valid.\", \"details\": [{\"name\": \"requestPath\", \"value\": \"availableNumbers/local\"}, {\"name\": \"requestMethod\", \"value\": \"GET\"}, {\"name\": \"remoteAddress\", \"value\": \"61.128.37.124\"}]}";
    



    @Before
    public void setUp() throws Exception {
        bandwidthManager = new BandwidthManager(bandwidthClient, accountsManager, accountNumbers, cacheClient);
        account = new Account();
        account.setNumber("+12354780754");
        account.setBalance(new BigDecimal("400"));
        phoneNumberRequest = new PhoneNumberRequest();
        phoneNumberRequest.setNumber("+23335554488");

        when(cacheClient.getWriteResource()).thenReturn(jedis);
        when(cacheClient.getReadResource()).thenReturn(jedis);
        RestResponse localAddrResponse = new RestResponse();
        localAddrResponse.setStatus(200);
        localAddrResponse.setResponseText(localAddrResponseString);
        when(bandwidthClient.get(eq(LOCAL_ADDRESS), any())).thenReturn(localAddrResponse);
        RestResponse tollFreeResponse = new RestResponse();
        tollFreeResponse.setStatus(200);
        tollFreeResponse.setResponseText(tollFreeAddrResponseString);
        when(bandwidthClient.get(eq(TOLL_FREE_ADDRESS), any())).thenReturn(tollFreeResponse);
    }

    @Test
    public void testGetAvailableLocalNumbers() {
        PhoneNumbersResponse availableTollFreeNumbers = bandwidthManager.getAvailableLocalNumbers(new HashMap<>());
        assertEquals(2, availableTollFreeNumbers.getPhoneNumbers().size());
        assertEquals("+16072352031", availableTollFreeNumbers.getPhoneNumbers().get(0).getNumber());
        assertEquals("(607) 235-2031", availableTollFreeNumbers.getPhoneNumbers().get(0).getNationalNumber());
        assertEquals("0.35", availableTollFreeNumbers.getPhoneNumbers().get(0).getPrice());
    }

    @Test
    public void testGetAvailableTollFreeNumbers() {
        PhoneNumbersResponse availableTollFreeNumbers = bandwidthManager.getAvailableTollFreeNumbers(new HashMap<>());
        assertEquals(1, availableTollFreeNumbers.getPhoneNumbers().size());
        assertEquals("+16073013587", availableTollFreeNumbers.getPhoneNumbers().get(0).getNumber());
        assertEquals("(607) 301-3587", availableTollFreeNumbers.getPhoneNumbers().get(0).getNationalNumber());
        assertEquals("0.35", availableTollFreeNumbers.getPhoneNumbers().get(0).getPrice());
    }

    @Test
    public void testGetAvailableLocalNumbersBadAnswer() throws Exception {
        RestResponse localAddrResponse = new RestResponse();
        localAddrResponse.setStatus(400);
        localAddrResponse.setResponseText(getPhonesErrorResponse);
        when(bandwidthClient.get(eq(LOCAL_ADDRESS), any())).thenReturn(localAddrResponse);
        PhoneNumbersResponse availableTollFreeNumbers = bandwidthManager.getAvailableLocalNumbers(new HashMap<>());
        assertEquals("The state abbreviation asd is not valid.", availableTollFreeNumbers.getErrorMessage());
    }

    @Test
    public void testOrderPhonePhoneNotFound() throws Exception {
        RestResponse tollFreeResponse = new RestResponse();
        tollFreeResponse.setStatus(403);
        tollFreeResponse.setResponseText(getPhonesErrorResponse);
        when(bandwidthClient.get(eq(TOLL_FREE_ADDRESS), any())).thenReturn(tollFreeResponse);
        PhoneNumbersResponse phoneNumbersResponse = bandwidthManager.orderPhoneNumber(account, phoneNumberRequest);
        assertEquals("No info about this number in bandwidth", phoneNumbersResponse.getErrorMessage());
    }
}