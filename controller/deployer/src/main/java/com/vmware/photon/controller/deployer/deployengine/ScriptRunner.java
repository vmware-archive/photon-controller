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

package com.vmware.photon.controller.deployer.deployengine;

import com.google.common.annotations.VisibleForTesting;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.Callable;

/**
 * This class implements simple script execution as a Java callable object.
 */
public class ScriptRunner implements Callable<Integer> {

  private static final Logger logger = LoggerFactory.getLogger(ScriptRunner.class);

  private final List<String> command;
  private final String directory;
  private final Map<String, String> environment;
  private final ProcessBuilder.Redirect redirectError;
  private final Boolean redirectErrorStream;
  private final ProcessBuilder.Redirect redirectInput;
  private final ProcessBuilder.Redirect redirectOutput;
  private final int timeoutInSeconds;

  private ProcessBuilder processBuilder;

  private ScriptRunner(Builder builder) {

    if (null == builder.command || builder.command.isEmpty()) {
      throw new IllegalArgumentException("Parameter command cannot be null or zero-length");
    }

    this.command = new ArrayList<>(builder.command);

    if (0 == builder.timeoutInSeconds) {
      throw new IllegalArgumentException("Parameter timeoutInSeconds cannot be zero");
    }

    this.timeoutInSeconds = builder.timeoutInSeconds;

    if (null != builder.environment) {
      this.environment = new HashMap<>(builder.environment);
    } else {
      this.environment = null;
    }

    this.directory = builder.directory;
    this.redirectError = builder.redirectError;
    this.redirectErrorStream = builder.redirectErrorStream;
    this.redirectInput = builder.redirectInput;
    this.redirectOutput = builder.redirectOutput;
  }

  private static ProcessBuilder getProcessBuilder(
      List<String> command,
      @Nullable String directory,
      @Nullable Map<String, String> environment,
      @Nullable ProcessBuilder.Redirect redirectError,
      @Nullable Boolean redirectErrorStream,
      @Nullable ProcessBuilder.Redirect redirectInput,
      @Nullable ProcessBuilder.Redirect redirectOutput)
      throws IOException {

    ProcessBuilder processBuilder = new ProcessBuilder(command);

    if (null != environment) {
      processBuilder.environment().putAll(environment);
    }

    if (null != directory) {
      processBuilder.directory(new File(directory));
    }

    if (null != redirectError) {
      processBuilder.redirectError(redirectError);
    }

    if (null != redirectErrorStream) {
      processBuilder.redirectErrorStream(true);
    }

    if (null != redirectInput) {
      processBuilder.redirectInput(redirectInput);
    }

    if (null != redirectOutput) {
      processBuilder.redirectOutput(redirectOutput);
    }

    return processBuilder;
  }

  public Integer call() {

    Process process = null;

    try {
      if (null == processBuilder) {
        processBuilder = getProcessBuilder(command, directory, environment, redirectError,
            redirectErrorStream, redirectInput, redirectOutput);
      }

      logger.debug("Executing command {}", command);
      process = processBuilder.start();

      Timer timer = new Timer();
      TimeoutWatchdog timeoutWatchdog = new TimeoutWatchdog(Thread.currentThread());
      timer.schedule(timeoutWatchdog, timeoutInSeconds * 1000);

      int exitCode = process.waitFor();
      process = null;

      timer.cancel();

      logger.debug("Command {} returned {}", command, String.valueOf(exitCode));
      return exitCode;

    } catch (RuntimeException e) {
      throw e;
    } catch (Exception e) {
      throw new RuntimeException(e);
    } finally {
      if (null != process) {
        process.destroy();
      }
    }
  }

  @VisibleForTesting
  protected List<String> getCommand() {
    return command;
  }

  @VisibleForTesting
  @Nullable
  protected String getDirectory() {
    return directory;
  }

  @VisibleForTesting
  @Nullable
  protected Map<String, String> getEnvironment() {
    return environment;
  }

  @VisibleForTesting
  @Nullable
  protected ProcessBuilder.Redirect getRedirectError() {
    return redirectError;
  }

  @VisibleForTesting
  @Nullable
  protected Boolean getRedirectErrorStream() {
    return redirectErrorStream;
  }

  @VisibleForTesting
  @Nullable
  protected ProcessBuilder.Redirect getRedirectInput() {
    return redirectInput;
  }

  @VisibleForTesting
  @Nullable
  protected ProcessBuilder.Redirect getRedirectOutput() {
    return redirectOutput;
  }

  @VisibleForTesting
  protected Integer getTimeoutInSeconds() {
    return timeoutInSeconds;
  }

  /**
   * This class implements a builder for the {@link ScriptRunner} object.
   */
  public static class Builder {

    private List<String> command;
    private String directory;
    private Map<String, String> environment;
    private ProcessBuilder.Redirect redirectError;
    private Boolean redirectErrorStream;
    private ProcessBuilder.Redirect redirectInput;
    private ProcessBuilder.Redirect redirectOutput;
    private int timeoutInSeconds;

    public Builder(List<String> command, int timeoutInSeconds) {

      if (null == command || command.isEmpty()) {
        throw new IllegalArgumentException("Parameter command cannot be null or zero-length");
      }

      if (0 == timeoutInSeconds) {
        throw new IllegalArgumentException("Parameter timeoutInSeconds cannot be zero");
      }

      this.command = new ArrayList<>(command);
      this.timeoutInSeconds = timeoutInSeconds;
    }

    public Builder directory(String directory) {
      this.directory = directory;
      return this;
    }

    public Builder environment(Map<String, String> environment) {
      this.environment = new HashMap<>(environment);
      return this;
    }

    public Builder redirectError(ProcessBuilder.Redirect redirectError) {
      this.redirectError = redirectError;
      return this;
    }

    public Builder redirectErrorStream(boolean redirectErrorStream) {
      this.redirectErrorStream = redirectErrorStream;
      return this;
    }

    public Builder redirectInput(ProcessBuilder.Redirect redirectInput) {
      this.redirectInput = redirectInput;
      return this;
    }

    public Builder redirectOutput(ProcessBuilder.Redirect redirectOutput) {
      this.redirectOutput = redirectOutput;
      return this;
    }

    public ScriptRunner build() {
      return new ScriptRunner(this);
    }
  }

  private class TimeoutWatchdog extends TimerTask {

    private final Logger logger = LoggerFactory.getLogger(TimeoutWatchdog.class);

    private Thread monitoredThread = null;

    public TimeoutWatchdog(Thread monitoredThread) {

      if (monitoredThread == null) {
        throw new IllegalArgumentException("Parameter monitoredThread cannot be null");
      }

      this.monitoredThread = monitoredThread;
    }

    public void run() {
      logger.info("TimeoutWatchdog fired");
      if (monitoredThread != null && monitoredThread.isAlive()) {
        monitoredThread.interrupt();
      }
    }
  }
}
