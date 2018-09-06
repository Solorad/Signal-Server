package org.whispersystems.textsecuregcm.controllers;

import com.codahale.metrics.annotation.Timed;
import io.dropwizard.auth.Auth;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.whispersystems.textsecuregcm.limits.RateLimiters;
import org.whispersystems.textsecuregcm.storage.Account;
import org.whispersystems.textsecuregcm.storage.BandwidthManager;
import org.whispersystems.textsecuregcm.storage.data.PhoneNumbersResponse;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
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
    public PhoneNumbersResponse getAvailableLocalNumbers(@Auth Account account, @Context UriInfo uriInfo)
            throws IOException, RateLimitExceededException {
        logger.info("getAvailableLocalNumbers started");
        Map<String, Object> params = getParameterMap(account, uriInfo);
        return bandwidthManager.getAvailableLocalNumbers(params);
    }

    @Timed
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/availableNumbers/tollFree")
    @ApiOperation(value = "Get Bandwidth available toll free numbers", notes = "Get Bandwidth available local numbers. For more info - https://dev.bandwidth.com/ap-docs/methods/availableNumbers/getAvailableNumbersTollFree.html")
    public PhoneNumbersResponse getAvailableTollFreeNumbers(@Auth Account account, @Context UriInfo uriInfo)
            throws RateLimitExceededException {
        logger.info("getAvailableTollFreeNumbers started");
        Map<String, Object> params = getParameterMap(account, uriInfo);
        return bandwidthManager.getAvailableTollFreeNumbers(params);
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
