package org.whispersystems.textsecuregcm.controllers;

import com.codahale.metrics.annotation.Timed;
import io.dropwizard.auth.Auth;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.whispersystems.textsecuregcm.limits.RateLimiters;
import org.whispersystems.textsecuregcm.storage.Account;
import org.whispersystems.textsecuregcm.storage.BandwidthManager;
import org.whispersystems.textsecuregcm.storage.bandwidth.ErrorResponse;
import org.whispersystems.textsecuregcm.storage.bandwidth.PhoneNumberRequest;
import org.whispersystems.textsecuregcm.storage.bandwidth.PhoneNumbersResponse;

import javax.validation.Valid;
import javax.ws.rs.*;
import javax.ws.rs.core.*;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Path("/v1/bandwidth")
@Produces(MediaType.APPLICATION_JSON)
@Api(value = "/v1/bandwidth", description = "Operations for work with bandwidth")
public class BandwidthController {
    private final Logger logger = LoggerFactory.getLogger(BandwidthController.class);

    private final BandwidthManager bandwidthManager;
    private final RateLimiters rateLimiters;

    public BandwidthController(BandwidthManager bandwidthManager,
                               RateLimiters rateLimiters) {
        this.bandwidthManager = bandwidthManager;
        this.rateLimiters = rateLimiters;
    }


    @Timed
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/availableNumbers/local")
    @ApiOperation(value = "Get Bandwidth available local numbers", notes = "Get Bandwidth available local numbers. For more info - https://dev.bandwidth.com/ap-docs/methods/availableNumbers/getAvailableNumbersLocal.html")
    public Response getAvailableLocalNumbers(@Auth Account account, @Context UriInfo uriInfo)
            throws IOException, RateLimitExceededException {
        logger.info("getAvailableLocalNumbers started");
        Map<String, Object> params = getParameterMap(account, uriInfo);
        PhoneNumbersResponse availableLocalNumbers = bandwidthManager.getAvailableLocalNumbers(params);
        if (StringUtils.isNotEmpty(availableLocalNumbers.getErrorMessage())) {
            return Response
                    .status(Response.Status.BAD_REQUEST)
                    .entity(new ErrorResponse(availableLocalNumbers.getErrorMessage()))
                    .build();
        }
        return Response.accepted(availableLocalNumbers.getPhoneNumbers()).build();
    }

    @Timed
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/availableNumbers/tollFree")
    @ApiOperation(value = "Get Bandwidth available toll free numbers", notes = "Get Bandwidth available local numbers. For more info - https://dev.bandwidth.com/ap-docs/methods/availableNumbers/getAvailableNumbersTollFree.html")
    public Response getAvailableTollFreeNumbers(@Auth Account account, @Context UriInfo uriInfo)
            throws RateLimitExceededException {
        logger.info("getAvailableTollFreeNumbers started");
        Map<String, Object> params = getParameterMap(account, uriInfo);
        PhoneNumbersResponse phoneNumbersResponse = bandwidthManager.getAvailableTollFreeNumbers(params);
        if (StringUtils.isNotEmpty(phoneNumbersResponse.getErrorMessage())) {
            return Response
                    .status(Response.Status.BAD_REQUEST)
                    .entity(new ErrorResponse(phoneNumbersResponse.getErrorMessage()))
                    .build();
        }
        return Response.accepted(phoneNumbersResponse.getPhoneNumbers()).build();
    }

    @Timed
    @POST
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/phoneNumbers")
    @ApiOperation(value = "Order available local number", notes = "Query param 'localNumber' is required. For more info - https://dev.bandwidth.com/ap-docs/methods/availableNumbers/postAvailableNumbersLocal.html")
    public Response orderLocalNumber(@Auth Account account, @Valid PhoneNumberRequest phoneNumberRequest)
            throws RateLimitExceededException {
        logger.info("getAvailableTollFreeNumbers started");
        PhoneNumbersResponse phoneNumbersResponse = bandwidthManager.orderPhoneNumber(account, phoneNumberRequest);
        if (StringUtils.isNotEmpty(phoneNumbersResponse.getErrorMessage())) {
            return Response
                    .status(Response.Status.BAD_REQUEST)
                    .entity(new ErrorResponse(phoneNumbersResponse.getErrorMessage()))
                    .build();
        }
        return Response.accepted(phoneNumbersResponse.getPhoneNumbers()).build();
    }

    private Map<String, Object> getParameterMap(@Auth Account account, @Context UriInfo uriInfo)
            throws RateLimitExceededException {
        rateLimiters.getBandwidthLimiter().validate(account.getNumber());
        MultivaluedMap<String, String> queryParams = uriInfo.getQueryParameters();
        Map<String, Object> params = new HashMap<>();
        for (Map.Entry<String, List<String>> entry : queryParams.entrySet()) {
            if (entry.getValue().size() > 0) {
                params.put(entry.getKey(), entry.getValue().get(0));
            }
        }
        return params;
    }
}
