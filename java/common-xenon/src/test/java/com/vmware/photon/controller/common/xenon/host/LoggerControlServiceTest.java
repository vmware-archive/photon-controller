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

package com.vmware.photon.controller.common.xenon.host;

import com.vmware.xenon.common.Operation;

import org.mockito.Mockito;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

import java.util.HashMap;
import java.util.Map;

/**
 * This class implements tests for {@link LoggerControlService}.
 */
public class LoggerControlServiceTest {

  private LoggerControlService service;

  @BeforeMethod
  public void setUp() {
    service = Mockito.spy(new LoggerControlService());
  }

  /**
   * Test to make sure updating the ROOT logger with a valid
   * log level is successful based on both return code and
   * the updated logging info map.
   *
   * @throws Throwable
   */
  @Test
  public void successUpdateRootLogger() throws Throwable {
    Map<String, String> loggerUpdate = new HashMap<>();
    loggerUpdate.put("ROOT", "info");
    Operation put = new Operation();
    put.setBody(loggerUpdate);

    service.handlePut(put);
    Map<String, String> updatedValues = put.getBody(Map.class);
    assertThat(updatedValues.get("ROOT"), is("INFO"));
    assertThat(put.getStatusCode(), is(put.STATUS_CODE_OK));
  }

  /**
   * Test to make sure updating a non-ROOT logger with a valid
   * log level is successful based on both return code and
   * the updated logging info map.
   *
   * @throws Throwable
   */
  @Test
  public void successUpdateNonRootLogger() throws Throwable {
    Map<String, String> loggerUpdate = new HashMap<>();
    String className = "com.vmware.photon.controller.common.xenon.host.LoggerControlServiceTest";
    loggerUpdate.put(className, "info");
    Operation put = new Operation();
    put.setBody(loggerUpdate);

    service.handlePut(put);
    Map<String, String> updatedValues = put.getBody(Map.class);
    assertThat(updatedValues.get(className), is("INFO"));
    assertThat(put.getStatusCode(), is(put.STATUS_CODE_OK));
  }

  /**
   * Test to make sure that updating a logger (in this case
   * the ROOT logger but it really covers updating any valid logger)
   * with an invalid log level fails appropriately.
   *
   * @throws Throwable
   */
  @Test
  public void failUpdateRootLoggerInvalidLevel() throws Throwable {
    Map<String, String> loggerUpdate = new HashMap<>();
    loggerUpdate.put("ROOT", "invalid");
    Operation put = new Operation();
    put.setBody(loggerUpdate);

    service.handlePut(put);
    assertThat(put.getStatusCode(), is(put.STATUS_CODE_INTERNAL_ERROR));
  }

  /**
   * Test to make sure that updating an invalid logger with
   * a valid log level fails appropriately.
   *
   * @throws Throwable
   */
  @Test
  public void failUpdateNonExistingLogger() throws Throwable {
    Map<String, String> loggerUpdate = new HashMap<>();
    loggerUpdate.put("invalid", "debug");
    Operation put = new Operation();
    put.setBody(loggerUpdate);

    service.handlePut(put);
    assertThat(put.getStatusCode(), is(put.STATUS_CODE_INTERNAL_ERROR));
  }

  /**
   * Test to make sure the fetching of logger status information
   * is working correctly.
   *
   * @throws Throwable
   */
  @Test
  public void successGetLoggerStatus() throws Throwable {
    Operation get = new Operation();
    service.handleGet(get);
    Map<String, String> loggerStatus = get.getBody(Map.class);

    assertThat(loggerStatus.get("ROOT"), is("DEBUG"));
    assertThat(get.getStatusCode(), is (get.STATUS_CODE_OK));
  }
}
