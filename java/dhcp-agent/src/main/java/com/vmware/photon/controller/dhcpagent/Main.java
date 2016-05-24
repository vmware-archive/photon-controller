/*
 * Copyright 2015 VMware, Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License.  You may obtain a copy of
 * the License at http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed
 * under the License is distributed on an "AS IS" BASIS, without warranties or
 * conditions of any kind, EITHER EXPRESS OR IMPLIED.  See the License for the
 * specific language governing permissions and limitations under the License.
 */

package com.vmware.photon.controller.dhcpagent;

import com.vmware.photon.controller.common.config.BadConfigException;
import com.vmware.photon.controller.common.config.ConfigBuilder;
import com.vmware.photon.controller.common.logging.LoggingFactory;
import com.vmware.photon.controller.dhcpagent.dhcpdrivers.DnsmasqDriver;
import com.vmware.photon.controller.dhcpagent.xenon.DHCPAgentXenonHost;

import com.google.inject.Guice;
import com.google.inject.Injector;
import net.sourceforge.argparse4j.ArgumentParsers;
import net.sourceforge.argparse4j.inf.ArgumentParser;
import net.sourceforge.argparse4j.inf.Namespace;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * DHCP-agent entry point.
 */
public class Main {

  private static final Logger logger = LoggerFactory.getLogger(Main.class);

  public static void main(String[] args) throws Throwable {
    LoggingFactory.bootstrap();

    ArgumentParser parser = ArgumentParsers.newArgumentParser("DHCPAgent")
        .defaultHelp(true)
        .description("Photon Controller DHCP Agent");
    parser.addArgument("file").help("configuration file");

    Namespace namespace = parser.parseArgsOrFail(args);

    DHCPAgentConfig dhcpAgentConfig = getConfig(namespace);

    new LoggingFactory(dhcpAgentConfig.getLogging(), "dhcpagent").configure();

    DnsmasqDriver dnsmasqDriver = new DnsmasqDriver("/var/lib/misc/dnsmasq.leases",
            "/usr/local/bin/dhcp_release",
            DnsmasqDriver.class.getResource("/scripts/release-ip.sh").getPath(),
            DnsmasqDriver.class.getResource("/scripts/dhcp-status.sh").getPath());

    Injector injector = Guice.createInjector(new DHCPAgentModule(dhcpAgentConfig, dnsmasqDriver));

    final DHCPAgentXenonHost dhcpAgentXenonHost = injector.getInstance(DHCPAgentXenonHost.class);

    Runtime.getRuntime().addShutdownHook(new Thread() {
      @Override
      public void run() {
        logger.info("Shutting down");
        dhcpAgentXenonHost.stop();
        logger.info("Done");
        LoggingFactory.detachAndStop();
      }
    });

    dhcpAgentXenonHost.start();
  }

  private static DHCPAgentConfig getConfig(Namespace namespace) {
    DHCPAgentConfig config = null;
    try {
      config = ConfigBuilder.build(DHCPAgentConfig.class, namespace.getString("file"));
    } catch (BadConfigException e) {
      logger.error(e.getMessage());
      System.exit(1);
    }
    return config;
  }
}
