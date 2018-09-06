/**
 * Copyright (C) 2013 Open WhisperSystems
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.whispersystems.textsecuregcm;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.dropwizard.Configuration;
import io.dropwizard.client.JerseyClientConfiguration;
import io.dropwizard.db.DataSourceFactory;
import lombok.Getter;
import org.whispersystems.textsecuregcm.configuration.*;
import org.whispersystems.websocket.configuration.WebSocketConfiguration;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import java.util.*;

@Getter
public class WhisperServerConfiguration extends Configuration {

  @NotNull
  @Valid
  @JsonProperty
  private TwilioConfiguration twilioConfiguration;

  @NotNull
  @Valid
  @JsonProperty
  private PushConfiguration pushConfiguration;

  @NotNull
  @Valid
  @JsonProperty
  private AttachmentsConfiguration attachmentsConfiguration;

  @NotNull
  @Valid
  @JsonProperty
  private ProfilesConfiguration profilesConfiguration;

  @NotNull
  @Valid
  @JsonProperty
  private RedisConfiguration cacheConfiguration;

  @NotNull
  @Valid
  @JsonProperty
  private RedisConfiguration directoryConfiguration;

  @NotNull
  @Valid
  @JsonProperty
  private RedisConfiguration pushScheduler;

  @NotNull
  @Valid
  @JsonProperty
  private MessageCacheConfiguration messageCacheConfiguration;

  @Valid
  @NotNull
  @JsonProperty
  private DataSourceFactory messageStoreConfiguration;

  @Valid
  @NotNull
  @JsonProperty
  private List<TestDeviceConfiguration> testDevicesConfiguration = new LinkedList<>();

  @Valid
  @NotNull
  @JsonProperty
  private List<MaxDeviceConfiguration> maxDevicesConfiguration = new LinkedList<>();

  @Valid
  @JsonProperty
  private FederationConfiguration federationConfiguration = new FederationConfiguration();

  @Valid
  @NotNull
  @JsonProperty
  private DataSourceFactory dataSourceFactory = new DataSourceFactory();

  @JsonProperty
  private DataSourceFactory readDataSourceFactory;

  @Valid
  @NotNull
  @JsonProperty
  private RateLimitsConfiguration limitsConfiguration = new RateLimitsConfiguration();

  @Valid
  @NotNull
  @JsonProperty
  private JerseyClientConfiguration jerseyClientConfiguration = new JerseyClientConfiguration();

  @Valid
  @NotNull
  @JsonProperty
  private WebSocketConfiguration webSocketConfiguration = new WebSocketConfiguration();

  @Valid
  @NotNull
  @JsonProperty
  private TurnConfiguration turnConfiguration;

  @Valid
  @NotNull
  @JsonProperty
  private GcmConfiguration gcmConfiguration;

  @Valid
  @NotNull
  @JsonProperty
  private ApnConfiguration apnConfiguration;

  @Valid
  @NotNull
  @JsonProperty
  private BandwidthConfiguration bandwidthConfiguration;


  public Map<String, Integer> getTestDevices() {
    Map<String, Integer> results = new HashMap<>();

    for (TestDeviceConfiguration testDeviceConfiguration : testDevicesConfiguration) {
      results.put(testDeviceConfiguration.getNumber(),
                  testDeviceConfiguration.getCode());
    }

    return results;
  }

  public Map<String, Integer> getMaxDevices() {
    Map<String, Integer> results = new HashMap<>();

    for (MaxDeviceConfiguration maxDeviceConfiguration : maxDevicesConfiguration) {
      results.put(maxDeviceConfiguration.getNumber(),
                  maxDeviceConfiguration.getCount());
    }

    return results;
  }

}
