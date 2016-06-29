/*
 * Copyright 2016 VMware, Inc. All Rights Reserved.
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
package com.vmware.photon.controller.common.xenon.host;

import com.vmware.photon.controller.common.xenon.ServiceUriPaths;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.StatelessService;
import com.vmware.xenon.common.Utils;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Class implementing service to change runtime log levels.
 */
public class LoggerControlService extends StatelessService {

  public static final String SELF_LINK = ServiceUriPaths.LOGGER_CONTROL_SERVICE;
  public static final String ROOT = "ROOT";

  @Override
  public void handleGet(Operation getOperation) {
    try {
      Map<String, String> loggerInfo = getCurrentLoggerInfo();
      getOperation.setBody(loggerInfo).complete();
    } catch (Throwable e) {
      getOperation.fail(e);
    }
  }

  @Override
  public void handlePut(Operation postOperation) {
    try {
      Map<?, ?> documents = postOperation.getBody(Map.class);

      if (!documents.isEmpty()) {
        for (Map.Entry<?, ?> entry : documents.entrySet()) {
          String logClassString = Utils.fromJson(entry.getKey(), String.class);
          String logLevelString = Utils.fromJson(entry.getValue(), String.class);

          Level logLevel = Level.toLevel(logLevelString);

          // Change global log level
          Logger logger = null;
          if (ROOT.equals(logClassString)) {
            logger = (Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
          } else {
            Class logClass = Class.forName(logClassString);
            if (logClass != null) {
              logger = (Logger) LoggerFactory.getLogger(logClass);
            }
          }

          if (logger != null && logLevel != null) {
            logger.info("Updating log level for logger {} to {}", logger, logLevel);
            logger.setLevel(logLevel);
          }
        }
      }

      postOperation.setBody(getCurrentLoggerInfo()).complete();
    } catch (Throwable e) {
      postOperation.fail(e);
    }
  }

  private Map<String, String> getCurrentLoggerInfo() throws Throwable {
    Map<String, String> loggerInfo = new HashMap<>();

    LoggerContext loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();
    List<Logger> loggerList = loggerContext.getLoggerList();
    for (Logger logger : loggerList) {
      Level logLevel = logger.getLevel();

      // Only include entries for loggers that aren't using the default log level
      if (logLevel != null) {
        loggerInfo.put(logger.getName(), logLevel.toString());
      }
    }

    return loggerInfo;
  }
}
