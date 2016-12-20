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

/*
 * Dropwizard
 * Copyright 2011-2012 Coda Hale and Yammer,Inc.
 * This product includes software developed by Coda Hale and Yammer,Inc.
 */
package com.vmware.photon.controller.common.logging;

import static com.vmware.photon.controller.common.logging.LoggingConfiguration.ConsoleConfiguration;
import static com.vmware.photon.controller.common.logging.LoggingConfiguration.FileConfiguration;
import static com.vmware.photon.controller.common.logging.LoggingConfiguration.SyslogConfiguration;

import ch.qos.logback.classic.AsyncAppender;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.jmx.JMXConfigurator;
import ch.qos.logback.classic.jul.LevelChangePropagator;
import ch.qos.logback.classic.net.SyslogAppender;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.Appender;
import ch.qos.logback.core.ConsoleAppender;
import ch.qos.logback.core.FileAppender;
import com.google.common.base.Optional;
import org.slf4j.LoggerFactory;
import org.slf4j.bridge.SLF4JBridgeHandler;

import javax.management.MBeanServer;
import javax.management.ObjectName;

import java.io.UnsupportedEncodingException;
import java.lang.management.ManagementFactory;
import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Map;
import java.util.TimeZone;
import java.util.UUID;

/**
 * LoggingFactory.
 *
 * NOTICE: Copied from DropWizard with modification to use Logback's AsyncAppender.
 */
public class LoggingFactory {
  public static void bootstrap() {
    // initially configure for WARN+ console logging
    final ConsoleConfiguration console = new ConsoleConfiguration();
    console.setEnabled(true);
    console.setTimeZone(TimeZone.getDefault());
    console.setThreshold(Level.WARN);

    final Logger root = getCleanRoot();
    root.addAppender(LogbackFactory.buildConsoleAppender(console,
        root.getLoggerContext(),
        Optional.<String>absent()));
  }

  public static void detachAndStop() {
    Logger root = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
    root.detachAndStopAllAppenders();
  }

  private final LoggingConfiguration config;
  private final String name;

  public LoggingFactory(LoggingConfiguration config, String name) {
    this.config = config;
    this.name = name;
  }

  private String generateInstanceId() {
    String uuid = UUID.randomUUID().toString();
    try {
      MessageDigest md = MessageDigest.getInstance("SHA1");
      md.reset();
      md.update(uuid.getBytes("UTF-8"));
      String result = new BigInteger(md.digest()).toString(16);
      return result.substring(0, 7);
    } catch (NoSuchAlgorithmException e) {
      throw new RuntimeException(e);
    } catch (UnsupportedEncodingException e) {
      throw new RuntimeException(e);
    }
  }

  public void configure() {
    hijackJDKLogging();

    final Logger root = configureLevels();

    root.getLoggerContext().putProperty("instance", generateInstanceId());

    final ConsoleConfiguration console = config.getConsoleConfiguration();
    if (console.isEnabled()) {
      ConsoleAppender<ILoggingEvent> appender = LogbackFactory.buildConsoleAppender(
          console, root.getLoggerContext(), console.getLogFormat());
      root.addAppender(wrapAsyncAppender(appender));
    }

    final FileConfiguration file = config.getFileConfiguration();
    if (file.isEnabled()) {
      FileAppender<ILoggingEvent> appender = LogbackFactory.buildFileAppender(
          file, root.getLoggerContext(), file.getLogFormat());
      root.addAppender(wrapAsyncAppender(appender));
    }

    final SyslogConfiguration syslog = config.getSyslogConfiguration();
    if (syslog.isEnabled()) {
      SyslogAppender appender = LogbackFactory.buildSyslogAppender(
          syslog, root.getLoggerContext(), name, syslog.getLogFormat());
      root.addAppender(wrapAsyncAppender(appender));
    }

    final MBeanServer server = ManagementFactory.getPlatformMBeanServer();
    try {
      final ObjectName objectName = new ObjectName("com.yammer:type=Logging");
      if (!server.isRegistered(objectName)) {
        server.registerMBean(new JMXConfigurator(root.getLoggerContext(),
            server,
            objectName),
            objectName);
      }
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  private void hijackJDKLogging() {
    SLF4JBridgeHandler.removeHandlersForRootLogger();
    SLF4JBridgeHandler.install();
  }

  private Logger configureLevels() {
    final Logger root = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
    root.getLoggerContext().reset();

    final LevelChangePropagator propagator = new LevelChangePropagator();
    propagator.setContext(root.getLoggerContext());
    propagator.setResetJUL(true);

    root.getLoggerContext().addListener(propagator);

    root.setLevel(config.getLevel());

    for (Map.Entry<String, Level> entry : config.getLoggers().entrySet()) {
      ((Logger) LoggerFactory.getLogger(entry.getKey())).setLevel(entry.getValue());
    }

    return root;
  }

  private static Logger getCleanRoot() {
    final Logger root = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
    root.detachAndStopAllAppenders();
    return root;
  }

  private static Appender<ILoggingEvent> wrapAsyncAppender(Appender<ILoggingEvent> appender) {
    AsyncAppender asyncAppender = new AsyncAppender();
    asyncAppender.setContext(appender.getContext());
    asyncAppender.addAppender(appender);
    asyncAppender.start();
    return asyncAppender;
  }
}
