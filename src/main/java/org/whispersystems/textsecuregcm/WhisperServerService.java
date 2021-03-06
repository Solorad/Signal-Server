/*
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

import com.bandwidth.sdk.BandwidthClient;
import com.codahale.metrics.SharedMetricRegistries;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.google.common.base.Optional;
import io.dropwizard.Application;
import io.dropwizard.db.DataSourceFactory;
import io.dropwizard.jdbi.DBIFactory;
import io.dropwizard.setup.Bootstrap;
import io.dropwizard.setup.Environment;
import io.paradoxical.dropwizard.swagger.AppSwaggerConfiguration;
import io.paradoxical.dropwizard.swagger.bundles.SwaggerUIBundle;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.eclipse.jetty.servlets.CrossOriginFilter;
import org.skife.jdbi.v2.DBI;
import org.whispersystems.dispatch.DispatchManager;
import org.whispersystems.dropwizard.simpleauth.AuthDynamicFeature;
import org.whispersystems.dropwizard.simpleauth.AuthValueFactoryProvider;
import org.whispersystems.dropwizard.simpleauth.BasicCredentialAuthFilter;
import org.whispersystems.textsecuregcm.auth.AccountAuthenticator;
import org.whispersystems.textsecuregcm.auth.FederatedPeerAuthenticator;
import org.whispersystems.textsecuregcm.auth.TurnTokenGenerator;
import org.whispersystems.textsecuregcm.configuration.BandwidthConfiguration;
import org.whispersystems.textsecuregcm.controllers.*;
import org.whispersystems.textsecuregcm.federation.FederatedClientManager;
import org.whispersystems.textsecuregcm.federation.FederatedPeer;
import org.whispersystems.textsecuregcm.limits.RateLimiters;
import org.whispersystems.textsecuregcm.liquibase.NameableMigrationsBundle;
import org.whispersystems.textsecuregcm.mappers.*;
import org.whispersystems.textsecuregcm.metrics.*;
import org.whispersystems.textsecuregcm.providers.RedisClientFactory;
import org.whispersystems.textsecuregcm.providers.RedisHealthCheck;
import org.whispersystems.textsecuregcm.push.*;
import org.whispersystems.textsecuregcm.redis.ReplicatedJedisPool;
import org.whispersystems.textsecuregcm.s3.UrlSigner;
import org.whispersystems.textsecuregcm.sms.SmsSender;
import org.whispersystems.textsecuregcm.sms.TwilioSmsSender;
import org.whispersystems.textsecuregcm.storage.*;
import org.whispersystems.textsecuregcm.util.Constants;
import org.whispersystems.textsecuregcm.websocket.*;
import org.whispersystems.textsecuregcm.workers.*;
import org.whispersystems.websocket.WebSocketResourceProviderFactory;
import org.whispersystems.websocket.setup.WebSocketEnvironment;

import javax.servlet.DispatcherType;
import javax.servlet.FilterRegistration;
import javax.servlet.ServletRegistration;
import java.security.Security;
import java.util.EnumSet;

import static com.codahale.metrics.MetricRegistry.name;

public class WhisperServerService extends Application<WhisperServerConfiguration> {

    static {
        Security.addProvider(new BouncyCastleProvider());
    }

    public static void main(String[] args) throws Exception {
        new WhisperServerService().run(args);
    }

    @Override
    public void initialize(Bootstrap<WhisperServerConfiguration> bootstrap) {
        bootstrap.addCommand(new DirectoryCommand());
        bootstrap.addCommand(new VacuumCommand());
        bootstrap.addCommand(new TrimMessagesCommand());
        bootstrap.addCommand(new PeriodicStatsCommand());
        bootstrap.addCommand(new DeleteUserCommand());
        bootstrap.addBundle(new NameableMigrationsBundle<WhisperServerConfiguration>("accountdb", "accountsdb.xml") {
            @Override
            public DataSourceFactory getDataSourceFactory(WhisperServerConfiguration configuration) {
                return configuration.getDatabase();
            }
        });

        bootstrap.addBundle(new NameableMigrationsBundle<WhisperServerConfiguration>("messagedb", "messagedb.xml") {
            @Override
            public DataSourceFactory getDataSourceFactory(WhisperServerConfiguration configuration) {
                return configuration.getMessageStore();
            }
        });
        bootstrap.addBundle(
                new SwaggerUIBundle(env -> new AppSwaggerConfiguration(env) {
                    {
                        setTitle("Whisper Server App");
                        setDescription("Mobdev Server App");

                        // The package name to look for swagger resources under
                        setResourcePackage("org.whispersystems.textsecuregcm.controllers");

                        setLicense("Apache 2.0");
                        setLicenseUrl("http://www.apache.org/licenses/LICENSE-2.0.html");
                        setContact("admin@mobdev.io");
                        setVersion("1.0");
                    }
                }));

    }

    @Override
    public String getName() {
        return "whisper-server";
    }

    @Override
    public void run(WhisperServerConfiguration config, Environment environment)
            throws Exception {
        SharedMetricRegistries.add(Constants.METRICS_NAME, environment.metrics());
        environment.getObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        environment.getObjectMapper().setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.NONE);
        environment.getObjectMapper().setVisibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY);

        DBIFactory dbiFactory = new DBIFactory();
        DBI database = dbiFactory.build(environment, config.getDatabase(), "accountdb");
        DBI messagedb = dbiFactory.build(environment, config.getMessageStore(), "messagedb");

        Accounts accounts = database.onDemand(Accounts.class);
        AccountNumbers accountNumbers = database.onDemand(AccountNumbers.class);
        PendingAccounts pendingAccounts = database.onDemand(PendingAccounts.class);
        PendingDevices pendingDevices = database.onDemand(PendingDevices.class);
        Keys keys = database.onDemand(Keys.class);
        Messages messages = messagedb.onDemand(Messages.class);

        RedisClientFactory cacheClientFactory = new RedisClientFactory(config.getCache().getUrl(),
                                                                       config.getCache().getReplicaUrls());
        RedisClientFactory directoryClientFactory = new RedisClientFactory(config.getDirectory().getUrl(),
                                                                           config.getDirectory().getReplicaUrls());
        RedisClientFactory messagesClientFactory = new RedisClientFactory(
                config.getMessageCache().getRedisConfiguration().getUrl(),
                config.getMessageCache().getRedisConfiguration().getReplicaUrls());
        RedisClientFactory pushSchedulerClientFactory = new RedisClientFactory(config.getPushScheduler().getUrl(),
                                                                               config.getPushScheduler().getReplicaUrls());

        ReplicatedJedisPool cacheClient = cacheClientFactory.getRedisClientPool();
        ReplicatedJedisPool directoryClient = directoryClientFactory.getRedisClientPool();
        ReplicatedJedisPool messagesClient = messagesClientFactory.getRedisClientPool();
        ReplicatedJedisPool pushSchedulerClient = pushSchedulerClientFactory.getRedisClientPool();

        DirectoryManager directory = new DirectoryManager(directoryClient);
        PendingAccountsManager pendingAccountsManager = new PendingAccountsManager(pendingAccounts, cacheClient);
        PendingDevicesManager pendingDevicesManager = new PendingDevicesManager(pendingDevices, cacheClient);
        AccountsManager accountsManager = new AccountsManager(accounts, directory, cacheClient);
        FederatedClientManager federatedClientManager = new FederatedClientManager(environment,
                                                                                   config.getHttpClient(),
                                                                                   config.getFederation());
        MessagesCache messagesCache = new MessagesCache(messagesClient, messages, accountsManager,
                                                        config.getMessageCache().getPersistDelayMinutes());
        MessagesManager messagesManager = new MessagesManager(messages, messagesCache,
                                                              config.getMessageCache().getCacheRate());
        DeadLetterHandler deadLetterHandler = new DeadLetterHandler(messagesManager);
        DispatchManager dispatchManager = new DispatchManager(cacheClientFactory, Optional.of(deadLetterHandler));
        PubSubManager pubSubManager = new PubSubManager(cacheClient, dispatchManager);
//    APNSender                  apnSender                  = new APNSender(accountsManager, config.getApnConfiguration());
        APNSender apnSender = null;
        GCMSender gcmSender = new GCMSender(accountsManager, config.getGcm().getApiKey());
        WebsocketSender websocketSender = new WebsocketSender(messagesManager, pubSubManager);
        AccountAuthenticator deviceAuthenticator = new AccountAuthenticator(accountsManager);
        FederatedPeerAuthenticator federatedPeerAuthenticator = new FederatedPeerAuthenticator(
                config.getFederation());
        RateLimiters rateLimiters = new RateLimiters(config.getLimits(), cacheClient);

        ApnFallbackManager apnFallbackManager = new ApnFallbackManager(pushSchedulerClient, accountsManager);
        TwilioSmsSender twilioSmsSender = new TwilioSmsSender(config.getTwilio());
        SmsSender smsSender = new SmsSender(twilioSmsSender);
        UrlSigner urlSigner = new UrlSigner(config.getAttachments());
        PushSender pushSender = new PushSender(gcmSender, websocketSender,
                                               config.getPush().getQueueSize());
        ReceiptSender receiptSender = new ReceiptSender(accountsManager, pushSender, federatedClientManager);
        TurnTokenGenerator turnTokenGenerator = new TurnTokenGenerator(config.getTurn());

        messagesCache.setPubSubManager(pubSubManager, pushSender);

//    apnSender.setApnFallbackManager(apnFallbackManager);
        environment.lifecycle().manage(apnFallbackManager);
        environment.lifecycle().manage(pubSubManager);
        environment.lifecycle().manage(pushSender);
        environment.lifecycle().manage(messagesCache);

        AttachmentController attachmentController = new AttachmentController(rateLimiters, federatedClientManager,
                                                                             urlSigner);
        KeysController keysController = new KeysController(rateLimiters, keys, accountsManager, federatedClientManager);
        MessageController messageController = new MessageController(rateLimiters, pushSender, receiptSender,
                                                                    accountsManager, messagesManager,
                                                                    federatedClientManager, apnFallbackManager);
        ProfileController profileController = new ProfileController(rateLimiters, accountsManager,
                                                                    config.getProfiles());

        environment.jersey().register(new AuthDynamicFeature(new BasicCredentialAuthFilter.Builder<Account>()
                                                                     .setAuthenticator(deviceAuthenticator)
                                                                     .setPrincipal(Account.class)
                                                                     .buildAuthFilter(),
                                                             new BasicCredentialAuthFilter.Builder<FederatedPeer>()
                                                                     .setAuthenticator(federatedPeerAuthenticator)
                                                                     .setPrincipal(FederatedPeer.class)
                                                                     .buildAuthFilter()));
        environment.jersey().register(new AuthValueFactoryProvider.Binder());

        environment.jersey().register(
                new AccountController(pendingAccountsManager, accountsManager, rateLimiters, smsSender, messagesManager,
                                      turnTokenGenerator, config.getTestDevicesMap()));
        BandwidthClient bandwidthClient = BandwidthClient.getInstance();
        BandwidthConfiguration bandwidth = config.getBandwidth();
        bandwidthClient.setCredentials(bandwidth.getUserID(), bandwidth.getApiToken(),
                                            bandwidth.getApiSecret());
        BandwidthManager bandwidthManager = new BandwidthManager(bandwidthClient, accountsManager, accountNumbers,
                                                                 cacheClient);
        environment.jersey().register(new BandwidthController(bandwidthManager, rateLimiters));
        environment.jersey().register(
                new DeviceController(pendingDevicesManager, accountsManager, messagesManager, rateLimiters,
                                     config.getMaxDevicesMap()));
        environment.jersey().register(new DirectoryController(rateLimiters, directory));
        environment.jersey().register(
                new FederationControllerV1(accountsManager, attachmentController, messageController));
        environment.jersey().register(
                new FederationControllerV2(accountsManager, attachmentController, messageController, keysController));
        environment.jersey().register(new ProvisioningController(rateLimiters, pushSender));
        environment.jersey().register(attachmentController);
        environment.jersey().register(keysController);
        environment.jersey().register(messageController);
        environment.jersey().register(profileController);

        WebSocketEnvironment webSocketEnvironment = new WebSocketEnvironment(environment,
                                                                             config.getWebSocket(), 90000);
        webSocketEnvironment.setAuthenticator(new WebSocketAccountAuthenticator(deviceAuthenticator));
        webSocketEnvironment.setConnectListener(
                new AuthenticatedConnectListener(pushSender, receiptSender, messagesManager, pubSubManager,
                                                 apnFallbackManager));
        webSocketEnvironment.jersey().register(new KeepAliveController(pubSubManager));
        webSocketEnvironment.jersey().register(messageController);
        webSocketEnvironment.jersey().register(profileController);

        WebSocketEnvironment provisioningEnvironment = new WebSocketEnvironment(environment,
                                                                                webSocketEnvironment.getRequestLog(),
                                                                                60000);
        provisioningEnvironment.setConnectListener(new ProvisioningConnectListener(pubSubManager));
        provisioningEnvironment.jersey().register(new KeepAliveController(pubSubManager));

        WebSocketResourceProviderFactory webSocketServlet = new WebSocketResourceProviderFactory(webSocketEnvironment);
        WebSocketResourceProviderFactory provisioningServlet = new WebSocketResourceProviderFactory(
                provisioningEnvironment);

        ServletRegistration.Dynamic websocket = environment.servlets().addServlet("WebSocket", webSocketServlet);
        ServletRegistration.Dynamic provisioning = environment.servlets().addServlet("Provisioning",
                                                                                     provisioningServlet);

        websocket.addMapping("/v1/websocket/");
        websocket.setAsyncSupported(true);

        provisioning.addMapping("/v1/websocket/provisioning/");
        provisioning.setAsyncSupported(true);

        webSocketServlet.start();
        provisioningServlet.start();

        FilterRegistration.Dynamic filter = environment.servlets().addFilter("CORS", CrossOriginFilter.class);
        filter.addMappingForUrlPatterns(EnumSet.allOf(DispatcherType.class), true, "/*");
        filter.setInitParameter("allowedOrigins", "*");
        filter.setInitParameter("allowedHeaders",
                                "Content-Type,Authorization,X-Requested-With,Content-Length,Accept,Origin,X-Signal-Agent");
        filter.setInitParameter("allowedMethods", "GET,PUT,POST,DELETE,OPTIONS");
        filter.setInitParameter("preflightMaxAge", "5184000");
        filter.setInitParameter("allowCredentials", "true");
///

        environment.healthChecks().register("directory", new RedisHealthCheck(directoryClient));
        environment.healthChecks().register("cache", new RedisHealthCheck(cacheClient));

        registerEceptionMappers(environment);

        environment.metrics().register(name(CpuUsageGauge.class, "cpu"), new CpuUsageGauge());
        environment.metrics().register(name(FreeMemoryGauge.class, "free_memory"), new FreeMemoryGauge());
        environment.metrics().register(name(NetworkSentGauge.class, "bytes_sent"), new NetworkSentGauge());
        environment.metrics().register(name(NetworkReceivedGauge.class, "bytes_received"), new NetworkReceivedGauge());
        environment.metrics().register(name(FileDescriptorGauge.class, "fd_count"), new FileDescriptorGauge());
    }

    private void registerEceptionMappers(Environment environment) {
        environment.jersey().register(new IOExceptionMapper());
        environment.jersey().register(new RateLimitExceededExceptionMapper());
        environment.jersey().register(new InvalidWebsocketAddressExceptionMapper());
        environment.jersey().register(new DeviceLimitExceededExceptionMapper());
    }
}
