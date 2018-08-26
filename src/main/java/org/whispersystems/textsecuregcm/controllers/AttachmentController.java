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
package org.whispersystems.textsecuregcm.controllers;

import com.amazonaws.HttpMethod;
import com.codahale.metrics.annotation.Timed;
import com.google.common.base.Optional;
import io.dropwizard.auth.Auth;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.whispersystems.textsecuregcm.entities.AttachmentDescriptor;
import org.whispersystems.textsecuregcm.entities.AttachmentUri;
import org.whispersystems.textsecuregcm.federation.FederatedClientManager;
import org.whispersystems.textsecuregcm.federation.NoSuchPeerException;
import org.whispersystems.textsecuregcm.limits.RateLimiters;
import org.whispersystems.textsecuregcm.s3.UrlSigner;
import org.whispersystems.textsecuregcm.storage.Account;
import org.whispersystems.textsecuregcm.util.Conversions;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.IOException;
import java.net.URL;
import java.security.SecureRandom;
import java.util.stream.Stream;


@Path("/v1/attachments")
@Api(value = "/v1/attachments", description = "Operations on the attachments")
public class AttachmentController {

    private final Logger logger = LoggerFactory.getLogger(AttachmentController.class);

    private static final String[] UNACCELERATED_REGIONS = {"+20", "+971", "+968", "+974"};

    private final RateLimiters rateLimiters;
    private final FederatedClientManager federatedClientManager;
    private final UrlSigner urlSigner;

    public AttachmentController(RateLimiters rateLimiters,
                                FederatedClientManager federatedClientManager,
                                UrlSigner urlSigner) {
        this.rateLimiters = rateLimiters;
        this.federatedClientManager = federatedClientManager;
        this.urlSigner = urlSigner;
    }

    @Timed
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @ApiOperation(value = "Allocate account attachment", notes = "Allocate attachment for account.")
    public AttachmentDescriptor allocateAttachment(@Auth Account account)
            throws RateLimitExceededException {
        if (account.isRateLimited()) {
            rateLimiters.getAttachmentLimiter().validate(account.getNumber());
        }

        long attachmentId = generateAttachmentId();
        URL url = urlSigner.getPreSignedUrl(attachmentId, HttpMethod.PUT, Stream.of(UNACCELERATED_REGIONS).anyMatch(
                region -> account.getNumber().startsWith(region)));

        return new AttachmentDescriptor(attachmentId, url.toExternalForm());

    }

    @Timed
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @ApiOperation(value = "Redirect to attachment", notes = "Redirect to attachment")
    @Path("/{attachmentId}")
    public AttachmentUri redirectToAttachment(@Auth Account account,
                                              @PathParam("attachmentId") long attachmentId,
                                              @QueryParam("relay") Optional<String> relay)
            throws IOException {
        try {
            if (!relay.isPresent()) {
                return new AttachmentUri(urlSigner.getPreSignedUrl(attachmentId, HttpMethod.GET,
                                                                   Stream.of(UNACCELERATED_REGIONS).anyMatch(
                                                                           region -> account.getNumber().startsWith(
                                                                                   region))));
            } else {
                return new AttachmentUri(
                        federatedClientManager.getClient(relay.get()).getSignedAttachmentUri(attachmentId));
            }
        } catch (NoSuchPeerException e) {
            logger.info("No such peer: " + relay);
            throw new WebApplicationException(Response.status(404).build());
        }
    }

    private long generateAttachmentId() {
        byte[] attachmentBytes = new byte[8];
        new SecureRandom().nextBytes(attachmentBytes);

        attachmentBytes[0] = (byte) (attachmentBytes[0] & 0x7F);
        return Conversions.byteArrayToLong(attachmentBytes);
    }
}
