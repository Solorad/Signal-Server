package org.whispersystems.textsecuregcm.controllers;

import com.codahale.metrics.annotation.Timed;
import io.dropwizard.auth.Auth;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.whispersystems.textsecuregcm.entities.ProvisioningMessage;
import org.whispersystems.textsecuregcm.limits.RateLimiters;
import org.whispersystems.textsecuregcm.push.PushSender;
import org.whispersystems.textsecuregcm.push.WebsocketSender;
import org.whispersystems.textsecuregcm.storage.Account;
import org.whispersystems.textsecuregcm.util.Base64;
import org.whispersystems.textsecuregcm.websocket.InvalidWebsocketAddressException;
import org.whispersystems.textsecuregcm.websocket.ProvisioningAddress;

import javax.validation.Valid;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.IOException;

@Path("/v1/provisioning")
@Api(value = "/v1/provisioning", description = "Provisioning controller")
public class ProvisioningController {                     

  private final RateLimiters    rateLimiters;
  private final WebsocketSender websocketSender;

  public ProvisioningController(RateLimiters rateLimiters, PushSender pushSender) {
    this.rateLimiters    = rateLimiters;
    this.websocketSender = pushSender.getWebSocketSender();
  }

  @Timed
  @Path("/{destination}")
  @PUT
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  @ApiOperation(value = "Send provisioning message", notes = "Send provisioning message")
  public void sendProvisioningMessage(@Auth                     Account source,
                                      @PathParam("destination") String destinationName,
                                      @Valid                    ProvisioningMessage message)
      throws RateLimitExceededException, InvalidWebsocketAddressException, IOException
  {
    rateLimiters.getMessagesLimiter().validate(source.getNumber());

    if (!websocketSender.sendProvisioningMessage(new ProvisioningAddress(destinationName, 0),
                                                 Base64.decode(message.getBody())))
    {
      throw new WebApplicationException(Response.Status.NOT_FOUND);
    }
  }
}
