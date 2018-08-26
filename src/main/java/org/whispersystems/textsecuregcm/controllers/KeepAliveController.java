package org.whispersystems.textsecuregcm.controllers;

import com.codahale.metrics.annotation.Timed;
import io.dropwizard.auth.Auth;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.whispersystems.textsecuregcm.storage.Account;
import org.whispersystems.textsecuregcm.storage.PubSubManager;
import org.whispersystems.textsecuregcm.websocket.WebsocketAddress;
import org.whispersystems.websocket.session.WebSocketSession;
import org.whispersystems.websocket.session.WebSocketSessionContext;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.core.Response;


@Path("/v1/keepalive")
@Api(value = "/v1/keepalive", description = "Keepalive endpoint")
public class KeepAliveController {

  private final Logger logger = LoggerFactory.getLogger(KeepAliveController.class);

  private final PubSubManager pubSubManager;

  public KeepAliveController(PubSubManager pubSubManager) {
    this.pubSubManager = pubSubManager;
  }

  @Timed
  @GET
  @ApiOperation(value = "Get keep alive", notes = "Get keep alive")
  public Response getKeepAlive(@Auth             Account account,
                               @WebSocketSession WebSocketSessionContext context)
  {
    if (account != null) {
      WebsocketAddress address = new WebsocketAddress(account.getNumber(),
                                                      account.getAuthenticatedDevice().get().getId());

      if (!pubSubManager.hasLocalSubscription(address)) {
        logger.warn("***** No local subscription found for: " + address);
        context.getClient().close(1000, "OK");
      }
    }

    return Response.ok().build();
  }

  @Timed
  @GET
  @Path("/provisioning")
  @ApiOperation(value = "Get provisioning keep alive", notes = "Get provisioning keep alive")
  public Response getProvisioningKeepAlive() {
    return Response.ok().build();
  }

}
