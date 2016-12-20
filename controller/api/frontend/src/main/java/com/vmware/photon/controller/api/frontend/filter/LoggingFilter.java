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

package com.vmware.photon.controller.api.frontend.filter;

import com.vmware.photon.controller.api.frontend.RequestId;
import com.vmware.photon.controller.common.logging.LoggingUtils;

import com.google.inject.Inject;
import org.apache.commons.lang3.time.StopWatch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Provider;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.UUID;

/**
 * Logs every request and response and sets the request id context.
 */
public class LoggingFilter implements Filter {
  private static final Logger logger = LoggerFactory.getLogger(LoggingFilter.class);

  private final Provider<UUID> requestIdProvider;

  @Inject
  public LoggingFilter(@RequestId Provider<UUID> requestIdProvider) {
    this.requestIdProvider = requestIdProvider;
  }

  @Override
  public void init(FilterConfig filterConfig) throws ServletException {
  }

  @Override
  public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException,
      ServletException {

    if (request instanceof HttpServletRequest) {
      HttpServletRequest httpRequest = (HttpServletRequest) request;
      HttpServletResponse httpResponse = (HttpServletResponse) response;
      String requestId = requestIdProvider.get().toString();

      LoggingUtils.setRequestId(requestId);
      logger.debug("Request: {} {}", httpRequest.getMethod(), httpRequest.getPathInfo());

      StopWatch stopwatch = new StopWatch();
      stopwatch.start();
      try {
        chain.doFilter(request, response);
      } finally {
        stopwatch.stop();
        String msg = String.format("Response: %s [%s] in %sms", httpRequest.getPathInfo(), httpResponse.getStatus(),
            stopwatch.getTime());
        if (httpResponse.getStatus() == HttpServletResponse.SC_OK) {
          logger.debug(msg);
        } else {
          logger.info(msg);
        }

        LoggingUtils.clearRequestId();
      }
    } else {
      chain.doFilter(request, response);
    }
  }

  @Override
  public void destroy() {
  }
}
