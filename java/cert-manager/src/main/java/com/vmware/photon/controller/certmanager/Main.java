/*
 * Copyright (c) 2015 VMware, Inc. All Rights Reserved.
 */
package com.vmware.photon.controller.certmanager;

import com.vmware.photon.controller.certmanager.dcp.CertManagerServiceHost;
import com.vmware.photon.controller.common.config.BadConfigException;
import com.vmware.photon.controller.common.config.ConfigBuilder;
import com.vmware.photon.controller.common.logging.LoggingFactory;

import com.google.inject.Guice;
import com.google.inject.Injector;
import net.sourceforge.argparse4j.ArgumentParsers;
import net.sourceforge.argparse4j.inf.ArgumentParser;
import net.sourceforge.argparse4j.inf.Namespace;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * Cert Manager entry point
 */
public class Main {
  private static final Logger logger = LoggerFactory.getLogger(Main.class);

  public static void main(String[] args) throws Throwable {
    ArgumentParser parser = ArgumentParsers.newArgumentParser("CertManager")
        .defaultHelp(true)
        .description("ESX Cloud Cert Manager Service");
    parser.addArgument("file").help("configuration file");

    Namespace namespace = parser.parseArgsOrFail(args);

    Config config = getConfig(namespace);

    new LoggingFactory(config.getLogging(), "certmanager").configure();

    Injector injector = Guice.createInjector(
        new CertManagerModule(config)
    );

    final CertManagerServiceHost server = injector.getInstance(CertManagerServiceHost.class);

    Runtime.getRuntime().addShutdownHook(new Thread() {
      @Override
      public void run() {
        logger.info("Shutting down");
        server.stop();
        logger.info("Done");
        LoggingFactory.detachAndStop();
      }
    });

    server.start();
  }

  private static Config getConfig(Namespace namespace) {
    Config config = null;
    try {
      config = ConfigBuilder.build(Config.class, namespace.getString("file"));
    } catch (BadConfigException e) {
      logger.error(e.getMessage());
      System.exit(1);
    }
    return config;
  }

}
