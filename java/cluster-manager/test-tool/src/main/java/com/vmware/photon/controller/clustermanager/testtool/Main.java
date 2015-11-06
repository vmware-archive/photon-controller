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

package com.vmware.photon.controller.clustermanager.testtool;

import com.vmware.photon.controller.common.logging.LogbackFactory;
import com.vmware.photon.controller.common.logging.LoggingConfiguration;
import com.vmware.photon.controller.common.logging.LoggingFactory;

import com.google.common.base.Optional;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Main class for the test-tool.
 */
public class Main {

  /**
   * Bootstrap the executor.
   */
  public static void main(String[] args) throws Exception {
    initializeLogging();

    Arguments arguments = Arguments.parseArguments(args);
    TestRunner runner = new TestRunner(arguments);

    List<TestStats> testStats = runner.run();
    printTestStats(testStats);

    LoggingFactory.detachAndStop();
  }

  private static void initializeLogging() throws Exception {
    LoggingConfiguration.FileConfiguration fileConfiguration =
        new LoggingConfiguration.FileConfiguration();
    fileConfiguration.setEnabled(true);
    fileConfiguration.setArchive(false);
    fileConfiguration.setCurrentLogFilename("clusterManagerTestTool.log");

    final ch.qos.logback.classic.Logger root =
        (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(ch.qos.logback.classic.Logger.ROOT_LOGGER_NAME);

    root.detachAndStopAllAppenders();
    root.addAppender(LogbackFactory.buildFileAppender(fileConfiguration,
        root.getLoggerContext(),
        Optional.<String>absent()));
  }

  private static void printTestStats(List<TestStats> testStats) {
    for (TestStats ts : testStats) {
      for (TestStats.TestStep step : ts.testStepList) {
        System.out.println(
            "StepName: " + step.name + ", " +
            "Passed: " + step.passed + ", " +
            "TimeTaken: " + getTime(step.timeTakenMilliSeconds));
      }

      System.out.println();
    }
  }

  private static String getTime(long milliseconds) {
    return String.format("%d min, %d sec",
        TimeUnit.MILLISECONDS.toMinutes(milliseconds),
        TimeUnit.MILLISECONDS.toSeconds(milliseconds) -
            TimeUnit.MINUTES.toSeconds(TimeUnit.MILLISECONDS.toMinutes(milliseconds))
    );
  }
}
