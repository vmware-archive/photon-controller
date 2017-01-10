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

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.filter.ThresholdFilter;
import ch.qos.logback.classic.net.SyslogAppender;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.ConsoleAppender;
import ch.qos.logback.core.FileAppender;
import ch.qos.logback.core.rolling.DefaultTimeBasedFileNamingAndTriggeringPolicy;
import ch.qos.logback.core.rolling.RollingFileAppender;
import ch.qos.logback.core.rolling.TimeBasedRollingPolicy;
import ch.qos.logback.core.spi.FilterAttachable;
import com.google.common.base.Optional;

/**
 * Factory to initialize various Logback appender instances.
 */
public class LogbackFactory {
  private LogbackFactory() { /* singleton */ }

  public static SyslogAppender buildSyslogAppender(LoggingConfiguration.SyslogConfiguration syslog,
                                                   LoggerContext context,
                                                   String name,
                                                   Optional<String> logFormat) {
    final SyslogAppender appender = new SyslogAppender();
    appender.setName(name);
    appender.setContext(context);
    appender.setSyslogHost(syslog.getHost());
    appender.setFacility(syslog.getFacility().toString());
    addThresholdFilter(appender, syslog.getThreshold());

    for (String format : logFormat.asSet()) {
      appender.setSuffixPattern(format);
    }

    appender.start();

    return appender;
  }

  public static FileAppender<ILoggingEvent> buildFileAppender(LoggingConfiguration.FileConfiguration file,
                                                              LoggerContext context,
                                                              Optional<String> logFormat) {
    final LogFormatter formatter = new LogFormatter(context, file.getTimeZone());
    for (String format : logFormat.asSet()) {
      formatter.setPattern(format);
    }
    formatter.start();

    final FileAppender<ILoggingEvent> appender =
        file.isArchive() ? new RollingFileAppender<ILoggingEvent>() :
            new FileAppender<ILoggingEvent>();

    appender.setAppend(true);
    appender.setContext(context);
    appender.setLayout(formatter);
    appender.setFile(file.getCurrentLogFilename());
    appender.setPrudent(false);

    addThresholdFilter(appender, file.getThreshold());

    if (file.isArchive()) {

      final DefaultTimeBasedFileNamingAndTriggeringPolicy<ILoggingEvent> triggeringPolicy =
          new DefaultTimeBasedFileNamingAndTriggeringPolicy<>();
      triggeringPolicy.setContext(context);

      final TimeBasedRollingPolicy<ILoggingEvent> rollingPolicy = new TimeBasedRollingPolicy<>();
      rollingPolicy.setContext(context);
      rollingPolicy.setFileNamePattern(file.getArchivedLogFilenamePattern());
      rollingPolicy.setTimeBasedFileNamingAndTriggeringPolicy(
          triggeringPolicy);
      triggeringPolicy.setTimeBasedRollingPolicy(rollingPolicy);
      rollingPolicy.setMaxHistory(file.getArchivedFileCount());

      ((RollingFileAppender<ILoggingEvent>) appender).setRollingPolicy(rollingPolicy);
      ((RollingFileAppender<ILoggingEvent>) appender).setTriggeringPolicy(triggeringPolicy);

      rollingPolicy.setParent(appender);
      rollingPolicy.start();
    }

    appender.stop();
    appender.start();

    return appender;
  }

  public static ConsoleAppender<ILoggingEvent> buildConsoleAppender(LoggingConfiguration.ConsoleConfiguration console,
                                                                    LoggerContext context,
                                                                    Optional<String> logFormat) {
    final LogFormatter formatter = new LogFormatter(context, console.getTimeZone());
    for (String format : logFormat.asSet()) {
      formatter.setPattern(format);
    }
    formatter.start();

    final ConsoleAppender<ILoggingEvent> appender = new ConsoleAppender<>();
    appender.setContext(context);
    appender.setLayout(formatter);
    addThresholdFilter(appender, console.getThreshold());
    appender.start();

    return appender;
  }

  private static void addThresholdFilter(FilterAttachable<ILoggingEvent> appender, Level threshold) {
    final ThresholdFilter filter = new ThresholdFilter();
    filter.setLevel(threshold.toString());
    filter.start();
    appender.addFilter(filter);
  }
}
