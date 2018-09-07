package org.whispersystems.textsecuregcm.workers;

import io.dropwizard.cli.ConfiguredCommand;
import io.dropwizard.db.DataSourceFactory;
import io.dropwizard.jdbi.ImmutableListContainerFactory;
import io.dropwizard.jdbi.ImmutableSetContainerFactory;
import io.dropwizard.jdbi.OptionalContainerFactory;
import io.dropwizard.jdbi.args.OptionalArgumentFactory;
import io.dropwizard.setup.Bootstrap;
import net.sourceforge.argparse4j.inf.Namespace;
import org.skife.jdbi.v2.DBI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.whispersystems.textsecuregcm.WhisperServerConfiguration;
import org.whispersystems.textsecuregcm.storage.*;


public class VacuumCommand extends ConfiguredCommand<WhisperServerConfiguration> {

  private final Logger logger = LoggerFactory.getLogger(VacuumCommand.class);

  public VacuumCommand() {
    super("vacuum", "Vacuum Postgres Tables");
  }

  @Override
  protected void run(Bootstrap<WhisperServerConfiguration> bootstrap,
                     Namespace namespace,
                     WhisperServerConfiguration config)
      throws Exception
  {
    DataSourceFactory dbConfig        = config.getDatabase();
    DataSourceFactory messageDbConfig = config.getMessageStore();
    DBI               dbi             = new DBI(dbConfig.getUrl(), dbConfig.getUser(), dbConfig.getPassword()                     );
    DBI               messageDbi      = new DBI(messageDbConfig.getUrl(), messageDbConfig.getUser(), messageDbConfig.getPassword());

    dbi.registerArgumentFactory(new OptionalArgumentFactory(dbConfig.getDriverClass()));
    dbi.registerContainerFactory(new ImmutableListContainerFactory());
    dbi.registerContainerFactory(new ImmutableSetContainerFactory());
    dbi.registerContainerFactory(new OptionalContainerFactory());

    messageDbi.registerArgumentFactory(new OptionalArgumentFactory(dbConfig.getDriverClass()));
    messageDbi.registerContainerFactory(new ImmutableListContainerFactory());
    messageDbi.registerContainerFactory(new ImmutableSetContainerFactory());
    messageDbi.registerContainerFactory(new OptionalContainerFactory());

    Accounts        accounts        = dbi.onDemand(Accounts.class       );
    Keys            keys            = dbi.onDemand(Keys.class           );
    PendingAccounts pendingAccounts = dbi.onDemand(PendingAccounts.class);
    Messages        messages        = messageDbi.onDemand(Messages.class       );

    logger.info("Vacuuming accounts...");
    accounts.vacuum();

    logger.info("Vacuuming pending_accounts...");
    pendingAccounts.vacuum();

    logger.info("Vacuuming keys...");
    keys.vacuum();

    logger.info("Vacuuming messages...");
    messages.vacuum();

    Thread.sleep(3000);
    System.exit(0);
  }
}
