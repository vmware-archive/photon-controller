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

package com.vmware.photon.controller.housekeeper.dcp;

import com.vmware.photon.controller.common.clients.HostClient;
import com.vmware.photon.controller.common.dcp.ServiceUtils;
import com.vmware.photon.controller.common.dcp.exceptions.BadRequestException;
import com.vmware.photon.controller.common.dcp.exceptions.DcpRuntimeException;
import com.vmware.photon.controller.housekeeper.helpers.dcp.TestHost;
import com.vmware.photon.controller.resource.gen.ImageReplication;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.UriUtils;

import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.closeTo;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;

import java.math.BigDecimal;
import java.util.EnumSet;
import java.util.concurrent.TimeUnit;

/**
 * Tests {@link ImageService}.
 */
public class ImageServiceTest {

  private TestHost host;
  private ImageService service;

  private ImageService.State buildValidStartupState() {
    ImageService.State state = new ImageService.State();
    state.id = "image-id";
    state.parentLink = "parent-service";
    state.replication = ImageReplication.EAGER;
    state.isTombstoned = false;
    return state;
  }

  /**
   * Dummy test case to make Intellij recognize this as a test class.
   */
  @Test
  private void dummy() {
  }

  /**
   * Tests for the constructors.
   */
  public class InitializationTest {
    @BeforeMethod
    public void setUp() {
      service = new ImageService();
    }

    /**
     * Test that the service starts with the expected options.
     */
    @Test
    public void testServiceOptions() {
      EnumSet<Service.ServiceOption> expected = EnumSet.of(
          Service.ServiceOption.PERSISTENCE,
          Service.ServiceOption.REPLICATION,
          Service.ServiceOption.INSTRUMENTATION);
      assertThat(service.getOptions(), is(expected));
    }
  }

  /**
   * Tests for the handleStart method.
   */
  public class HandleStartTest {
    @BeforeMethod
    public void setUp() throws Throwable {
      service = spy(new ImageService());
      host = TestHost.create(mock(HostClient.class));
    }

    @AfterMethod
    public void tearDown() throws Throwable {
      if (host != null) {
        TestHost.destroy(host);
      }

      service = null;
    }

    /**
     * Test start of service with valid start state.
     *
     * @throws Throwable
     */
    @Test
    public void testStartState() throws Throwable {
      Operation startOp = host.startServiceSynchronously(service, buildValidStartupState());
      assertThat(startOp.getStatusCode(), is(200));

      ImageService.State savedState = host.getServiceState(ImageService.State.class);
      assertThat(savedState.id, is("image-id"));
      assertThat(savedState.parentLink, is("parent-service"));
      assertThat(savedState.replication, is(ImageReplication.EAGER));
      assertThat(new BigDecimal(savedState.documentExpirationTimeMicros),
          is(closeTo(new BigDecimal(ServiceUtils.computeExpirationTime(ServiceUtils.DEFAULT_DOC_EXPIRATION_TIME)),
              new BigDecimal(TimeUnit.MINUTES.toMicros(10)))));
    }

    /**
     * Test service start with missing image identifier in start state.
     *
     * @param id
     * @throws Throwable
     */
    @Test(dataProvider = "illegalIdValues",
        expectedExceptions = DcpRuntimeException.class,
        expectedExceptionsMessageRegExp = "id cannot be (null|blank)")
    public void testMissingImageId(String id) throws Throwable {
      ImageService.State startState = buildValidStartupState();
      startState.id = id;

      host.startServiceSynchronously(service, startState);
    }

    @DataProvider(name = "illegalIdValues")
    public Object[][] getIllegalIdValuesData() {
      return new Object[][]{
          {""},
          {null}
      };
    }

    /**
     * Test service start with missing parentLink in start state.
     *
     * @throws Throwable
     */
    @Test(dataProvider = "illegalParentLinkValues",
        expectedExceptions = DcpRuntimeException.class,
        expectedExceptionsMessageRegExp = "parentLink cannot be (null|blank)")
    public void testMissingParentLink(String link) throws Throwable {
      ImageService.State startState = buildValidStartupState();
      startState.parentLink = link;

      host.startServiceSynchronously(service, startState);
    }

    @DataProvider(name = "illegalParentLinkValues")
    public Object[][] getIllegalParentLinkValuesData() {
      return new Object[][]{
          {""},
          {null}
      };
    }

    /**
     * @param time
     * @param expectedTime
     * @param delta
     * @throws Throwable
     */
    @Test(dataProvider = "ExpirationTime")
    public void testExpirationTimeInitialization(long time,
                                                 BigDecimal expectedTime,
                                                 BigDecimal delta) throws Throwable {
      ImageService.State startState = buildValidStartupState();
      startState.documentExpirationTimeMicros = time;

      Operation startOp = host.startServiceSynchronously(service, startState);
      assertThat(startOp.getStatusCode(), is(200));

      ImageService.State savedState = host.getServiceState(ImageService.State.class);
      assertThat(new BigDecimal(savedState.documentExpirationTimeMicros), is(closeTo(expectedTime, delta)));
    }

    @DataProvider(name = "ExpirationTime")
    public Object[][] getExpirationTime() {
      long expTime = ServiceUtils.computeExpirationTime(TimeUnit.HOURS.toMillis(1));

      return new Object[][]{
          {
              -10L,
              new BigDecimal(ServiceUtils.computeExpirationTime(ServiceUtils.DEFAULT_DOC_EXPIRATION_TIME)),
              new BigDecimal(TimeUnit.MINUTES.toMicros(10))
          },
          {
              0L,
              new BigDecimal(ServiceUtils.computeExpirationTime(ServiceUtils.DEFAULT_DOC_EXPIRATION_TIME)),
              new BigDecimal(TimeUnit.MINUTES.toMicros(10))
          },
          {
              expTime,
              new BigDecimal(expTime),
              new BigDecimal(0)
          }
      };
    }
  }

  /**
   * Tests for the handlePatch method.
   */
  public class HandlePatchTest {
    @BeforeMethod
    public void setUp() throws Throwable {
      service = spy(new ImageService());
      host = TestHost.create(mock(HostClient.class));
    }

    @AfterMethod
    public void tearDown() throws Throwable {
      if (host != null) {
        TestHost.destroy(host);
      }

      service = null;
    }

    /**
     * Test patch operation which should always fail.
     *
     * @throws Throwable
     */
    @Test(expectedExceptions = BadRequestException.class,
        expectedExceptionsMessageRegExp = "Action not supported: PATCH")
    public void testPatch() throws Throwable {
      host.startServiceSynchronously(service, buildValidStartupState());

      ImageService.State patchState = new ImageService.State();
      patchState.id = "new-image-id";

      Operation patch = Operation
          .createPatch(UriUtils.buildUri(host, TestHost.SERVICE_URI, null))
          .setBody(patchState);
      host.sendRequestAndWait(patch);
    }
  }
}
