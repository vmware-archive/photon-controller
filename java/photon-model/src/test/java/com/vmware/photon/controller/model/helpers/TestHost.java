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

package com.vmware.photon.controller.model.helpers;

import com.vmware.dcp.common.Operation;
import com.vmware.dcp.common.ServiceDocument;
import com.vmware.dcp.common.ServiceHost;
import com.vmware.dcp.common.UriUtils;
import com.vmware.dcp.common.test.VerificationHost;

import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.concurrent.TimeoutException;
import java.util.function.Predicate;
import java.util.logging.LogManager;

/**
 * This class implements helper routines used to test service hosts in isolation.
 */
public class TestHost extends VerificationHost {

  private static final int WAIT_ITERATION_SLEEP = 500;
  private static final int WAIT_ITERATION_COUNT = 30000 / WAIT_ITERATION_SLEEP; // 30 seconds.

  private ServiceDocument responseBody;
  private Class[] factoryServices;

  /**
   * Overloaded Constructor.
   */
  public TestHost(int port, Path storageSandbox, Class... factoryServices) throws Throwable {
    super();
    ServiceHost.Arguments args = new ServiceHost.Arguments();
    args.id = "host-" + VerificationHost.hostNumber.incrementAndGet();
    args.port = port;
    args.sandbox = storageSandbox;
    args.bindAddress = ServiceHost.LOOPBACK_ADDRESS;
    this.initialize(args);

    this.factoryServices = factoryServices;
  }

  @Override
  public ServiceHost start() throws Throwable {
    super.start();
    for (Class service : this.factoryServices) {
      Field f = service.getField(UriUtils.FIELD_NAME_SELF_LINK);
      String path = (String) f.get(null);

      this.startServiceAndWait(service, path);
    }
    return this;
  }

  @Override
  public void tearDown() {
    super.tearDown();
    LogManager.getLogManager().reset();
  }

  public <T extends ServiceDocument> T postServiceSynchronously(
      String serviceUri, T body, Class<T> type) throws Throwable {
    return postServiceSynchronously(serviceUri, body, type, null);
  }

  public <T extends ServiceDocument> T postServiceSynchronously(
      String serviceUri, T body, Class<T> type, Class expectedException) throws Throwable {

    this.testStart(1);
    Operation postOperation = Operation
        .createPost(UriUtils.buildUri(this, serviceUri))
        .setBody(body)
        .setReferer(this.getUri())
        .setCompletion((operation, throwable) -> {

          boolean failureExpected = (expectedException != null);
          boolean failureReturned = (throwable != null);

          if (failureExpected ^ failureReturned) {
            Throwable t = throwable == null
                ? new IllegalArgumentException("Call did not fail as expected")
                : throwable;

            this.failIteration(t);
            return;
          }

          if (failureExpected && expectedException != throwable.getClass()) {
            this.failIteration(throwable);
            return;
          }

          if (!failureExpected) {
            responseBody = operation.getBody(type);
          }
          this.completeIteration();
        });

    this.sendRequest(postOperation);
    this.testWait();

    return (T) responseBody;
  }

  public <T extends ServiceDocument> void patchServiceSynchronously(
      String serviceUri, T patchBody) throws Throwable {

    this.testStart(1);
    Operation patchOperation = Operation
        .createPatch(UriUtils.buildUri(this, serviceUri))
        .setBody(patchBody)
        .setReferer(this.getUri())
        .setCompletion(getCompletion());

    this.sendRequest(patchOperation);
    this.testWait();
  }

  public <T extends ServiceDocument> T getServiceSynchronously(
      String serviceUri, Class<T> type) throws Throwable {

    this.testStart(1);
    Operation patchOperation = Operation
        .createGet(UriUtils.buildUri(this, serviceUri))
        .setReferer(this.getUri())
        .setCompletion((operation, throwable) -> {
          if (throwable != null) {
            this.failIteration(throwable);
          }

          responseBody = operation.getBody(type);
          this.completeIteration();
        });

    this.sendRequest(patchOperation);
    this.testWait();

    return (T) responseBody;
  }

  public <T extends ServiceDocument> T waitForServiceState(
      Class<T> type, String serviceUri, Predicate<T> test)
      throws Throwable {
    return waitForServiceState(type, serviceUri, test, WAIT_ITERATION_SLEEP, WAIT_ITERATION_COUNT);
  }

  public <T extends ServiceDocument> T waitForServiceState(
      Class<T> type, String serviceUri, Predicate<T> test, int waitIterationSleep, int waitIterationCount)
      throws Throwable {
    for (int i = 0; i < waitIterationCount; i++) {
      T t = getServiceSynchronously(serviceUri, type);
      if (test.test(t)) {
        return t;
      }
      Thread.sleep(waitIterationSleep);
    }

    throw new TimeoutException("timeout waiting for state transition.");
  }

}
