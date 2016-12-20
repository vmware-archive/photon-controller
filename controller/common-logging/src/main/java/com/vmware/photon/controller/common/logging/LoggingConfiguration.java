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
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonValue;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableMap;

import javax.validation.Valid;
import javax.validation.constraints.AssertTrue;
import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;

import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

/**
 * Configuration parameters for logging.
 */
@SuppressWarnings("UnusedDeclaration")
public class LoggingConfiguration {
  static final TimeZone UTC = TimeZone.getTimeZone("UTC");

  /**
   * Configuration params for logging outputs that go to console out.
   */
  public static class ConsoleConfiguration {
    @JsonProperty
    private boolean enabled = true;

    @NotNull
    @JsonProperty
    private Level threshold = Level.ALL;

    @NotNull
    @JsonProperty
    private TimeZone timeZone = UTC;

    @JsonProperty
    private String logFormat;

    public boolean isEnabled() {
      return enabled;
    }

    public void setEnabled(boolean enabled) {
      this.enabled = enabled;
    }

    public Level getThreshold() {
      return threshold;
    }

    public void setThreshold(Level threshold) {
      this.threshold = threshold;
    }

    public TimeZone getTimeZone() {
      return timeZone;
    }

    public void setTimeZone(TimeZone timeZone) {
      this.timeZone = timeZone;
    }

    public Optional<String> getLogFormat() {
      return Optional.fromNullable(logFormat);
    }

    public void setLogFormat(String logFormat) {
      this.logFormat = logFormat;
    }
  }

  /**
   * Configuration params for logging outputs that go to a local file.
   */
  public static class FileConfiguration {
    @JsonProperty
    private boolean enabled = false;

    @NotNull
    @JsonProperty
    private Level threshold = Level.ALL;

    @JsonProperty
    private String currentLogFilename;

    @JsonProperty
    private boolean archive = true;

    @JsonProperty
    private String archivedLogFilenamePattern;

    @Min(1)
    @Max(50)
    @JsonProperty
    private int archivedFileCount = 5;

    @NotNull
    @JsonProperty
    private TimeZone timeZone = UTC;

    @JsonProperty
    private String logFormat;

    @AssertTrue(message = "must have logging.file.archivedLogFilenamePattern if logging.file.archive is true")
    public boolean isValidArchiveConfiguration() {
      return !enabled || !archive || (archivedLogFilenamePattern != null);
    }

    @AssertTrue(message = "must have logging.file.currentLogFilename if logging.file.enabled is true")
    public boolean isConfigured() {
      return !enabled || (currentLogFilename != null);
    }

    public boolean isEnabled() {
      return enabled;
    }

    public void setEnabled(boolean enabled) {
      this.enabled = enabled;
    }

    public Level getThreshold() {
      return threshold;
    }

    public void setThreshold(Level level) {
      this.threshold = level;
    }

    public String getCurrentLogFilename() {
      return currentLogFilename;
    }

    public void setCurrentLogFilename(String filename) {
      this.currentLogFilename = filename;
    }

    public boolean isArchive() {
      return archive;
    }

    public void setArchive(boolean archive) {
      this.archive = archive;
    }

    public int getArchivedFileCount() {
      return archivedFileCount;
    }

    public void setArchivedFileCount(int count) {
      this.archivedFileCount = count;
    }

    public String getArchivedLogFilenamePattern() {
      return archivedLogFilenamePattern;
    }

    public void setArchivedLogFilenamePattern(String pattern) {
      this.archivedLogFilenamePattern = pattern;
    }

    public TimeZone getTimeZone() {
      return timeZone;
    }

    public void setTimeZone(TimeZone timeZone) {
      this.timeZone = timeZone;
    }

    public Optional<String> getLogFormat() {
      return Optional.fromNullable(logFormat);
    }

    public void setLogFormat(String logFormat) {
      this.logFormat = logFormat;
    }
  }

  /**
   * Configuration params for logging outputs that go to a Syslog endpoint.
   */
  public static class SyslogConfiguration {
    /**
     * Enum values for the facility field.
     */
    public enum Facility {
      AUTH, AUTHPRIV, DAEMON, CRON, FTP, LPR, KERN, MAIL, NEWS, SYSLOG, USER, UUCP,
      LOCAL0, LOCAL1, LOCAL2, LOCAL3, LOCAL4, LOCAL5, LOCAL6, LOCAL7;

      @Override
      @JsonValue
      public String toString() {
        return super.toString().replace("_", "+").toLowerCase(Locale.ENGLISH);
      }

      @JsonCreator
      public static Facility parse(String facility) {
        return valueOf(facility.toUpperCase(Locale.ENGLISH).replace('+', '_'));
      }
    }

    @JsonProperty
    private boolean enabled = false;

    @NotNull
    @JsonProperty
    private Level threshold = Level.ALL;

    @NotNull
    @JsonProperty
    private String host = "localhost";

    @NotNull
    @JsonProperty
    private Facility facility = Facility.LOCAL0;

    @NotNull
    @JsonProperty
    private TimeZone timeZone = UTC;

    @JsonProperty
    private String logFormat;

    public boolean isEnabled() {
      return enabled;
    }

    public void setEnabled(boolean enabled) {
      this.enabled = enabled;
    }

    public Level getThreshold() {
      return threshold;
    }

    public void setThreshold(Level threshold) {
      this.threshold = threshold;
    }

    public String getHost() {
      return host;
    }

    public void setHost(String host) {
      this.host = host;
    }

    public Facility getFacility() {
      return facility;
    }

    public void setFacility(Facility facility) {
      this.facility = facility;
    }

    public TimeZone getTimeZone() {
      return timeZone;
    }

    public void setTimeZone(TimeZone timeZone) {
      this.timeZone = timeZone;
    }

    public Optional<String> getLogFormat() {
      return Optional.fromNullable(logFormat);
    }

    public void setLogFormat(String logFormat) {
      this.logFormat = logFormat;
    }
  }

  @NotNull
  @JsonProperty
  private Level level = Level.INFO;

  @NotNull
  @JsonProperty
  private ImmutableMap<String, Level> loggers = ImmutableMap.of();

  @Valid
  @NotNull
  @JsonProperty
  private ConsoleConfiguration console = new ConsoleConfiguration();

  @Valid
  @NotNull
  @JsonProperty
  private FileConfiguration file = new FileConfiguration();

  @Valid
  @NotNull
  @JsonProperty
  private SyslogConfiguration syslog = new SyslogConfiguration();

  public Level getLevel() {
    return level;
  }

  public void setLevel(Level level) {
    this.level = level;
  }

  public ImmutableMap<String, Level> getLoggers() {
    return loggers;
  }

  public void setLoggers(Map<String, Level> loggers) {
    this.loggers = ImmutableMap.copyOf(loggers);
  }

  public ConsoleConfiguration getConsoleConfiguration() {
    return console;
  }

  public void setConsoleConfiguration(ConsoleConfiguration config) {
    this.console = config;
  }

  public FileConfiguration getFileConfiguration() {
    return file;
  }

  public void setFileConfiguration(FileConfiguration config) {
    this.file = config;
  }

  public SyslogConfiguration getSyslogConfiguration() {
    return syslog;
  }

  public void setSyslogConfiguration(SyslogConfiguration config) {
    this.syslog = config;
  }
}
