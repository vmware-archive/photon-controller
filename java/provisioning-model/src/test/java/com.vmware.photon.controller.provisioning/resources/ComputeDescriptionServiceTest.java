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

package com.vmware.photon.controller.provisioning.resources;

import com.vmware.dcp.common.Service;
import com.vmware.photon.controller.provisioning.helpers.TestEnvironment;

import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.testng.Assert.assertNotNull;

import java.net.URI;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.UUID;

/**
 * This class implements tests for the {@link ComputeDescriptionService} class.
 */
public class ComputeDescriptionServiceTest {

  private ComputeDescriptionService computeDescriptionService;
  private TestEnvironment machine;

  private ComputeDescriptionService.ComputeDescription buildValidStartState() throws Throwable {
    ComputeDescriptionService.ComputeDescription cd = new ComputeDescriptionService.ComputeDescription();

    cd.bootAdapterReference = new URI("http://bootAdapterReference");
    cd.powerAdapterReference = new URI("http://powerAdapterReference");
    cd.instanceAdapterReference = new URI("http://instanceAdapterReference");
    cd.healthAdapterReference = new URI("http://healthAdapterReference");
    cd.enumerationAdapterReference = new URI("http://enumerationAdapterReference");

    cd.dataCenterId = null;
    cd.networkId = null;
    cd.dataStoreId = null;

    ArrayList<String> children = new ArrayList<>();
    for (ComputeDescriptionService.ComputeDescription.ComputeType type :
        ComputeDescriptionService.ComputeDescription.ComputeType.values()) {
      children.add(type.name());
    }

    cd.supportedChildren = children;
    cd.environmentName = ComputeDescriptionService.ComputeDescription.ENVIRONMENT_NAME_ON_PREMISE;
    cd.costPerMinute = 1;
    cd.cpuMhzPerCore = 1000;
    cd.cpuCount = 2;
    cd.gpuCount = 1;
    cd.totalMemoryBytes = Integer.MAX_VALUE;
    cd.id = UUID.randomUUID().toString();
    cd.name = "friendly-name";
    cd.regionId = "provider-specific-regions";
    cd.zoneId = "provider-specific-zone";
    return cd;
  }

  @Test
  private void dummy() {
  }

  /**
   * This class implements tests for the constructor.
   */
  public class ConstructorTest {
    @BeforeMethod
    public void setUpTest() {
      computeDescriptionService = new ComputeDescriptionService();
    }

    @Test
    public void testServiceOptions() {

      EnumSet<Service.ServiceOption> expected = EnumSet.of(
          Service.ServiceOption.OWNER_SELECTION,
          Service.ServiceOption.PERSISTENCE,
          Service.ServiceOption.REPLICATION);

      assertThat(computeDescriptionService.getOptions(), is(expected));
    }
  }

  /**
   * This class implements tests for the handleStart method.
   */
  public class HandleStartTest {
    private static final int HOST_COUNT = 1;

    @BeforeClass
    public void setUpClass() throws Throwable {
      machine = new TestEnvironment(HOST_COUNT);
      machine.start();
    }

    @BeforeMethod
    public void setUpTest() {
      computeDescriptionService = new ComputeDescriptionService();
    }

    @AfterMethod
    public void tearDownTest() throws Throwable {
    }

    @AfterClass
    public void tearDownClass() throws Throwable {
      if (machine != null) {
        machine.stop();
        machine = null;
      }
    }

    @Test
    public void testValidStartState() throws Throwable {
      ComputeDescriptionService.ComputeDescription startState = buildValidStartState();
      ComputeDescriptionService.ComputeDescription returnState = machine.callServiceSynchronously(
          ComputeDescriptionFactoryService.SELF_LINK, startState, ComputeDescriptionService.ComputeDescription.class);

      assertNotNull(returnState);
      assertThat(returnState.id, is(startState.id));
      assertThat(returnState.name, is(startState.name));
      assertThat(returnState.regionId, is(startState.regionId));
      assertThat(returnState.environmentName, is(startState.environmentName));
    }

    @Test
    public void testMissingId() throws Throwable {
      ComputeDescriptionService.ComputeDescription startState = buildValidStartState();
      startState.id = null;

      ComputeDescriptionService.ComputeDescription returnState = machine.callServiceSynchronously(
          ComputeDescriptionFactoryService.SELF_LINK, startState, ComputeDescriptionService.ComputeDescription.class);

      assertNotNull(returnState);
      assertNotNull(returnState.id);
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testMissingBootAdapterReference() throws Throwable {
      ComputeDescriptionService.ComputeDescription startState = buildValidStartState();
      startState.bootAdapterReference = null;

      machine.callServiceSynchronously(
          ComputeDescriptionFactoryService.SELF_LINK, startState, ComputeDescriptionService.ComputeDescription.class);
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testMissingPowerAdapterReference() throws Throwable {
      ComputeDescriptionService.ComputeDescription startState = buildValidStartState();
      startState.powerAdapterReference = null;

      machine.callServiceSynchronously(
          ComputeDescriptionFactoryService.SELF_LINK, startState, ComputeDescriptionService.ComputeDescription.class);
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testMissingInstanceAdapterReference() throws Throwable {
      ComputeDescriptionService.ComputeDescription startState = buildValidStartState();
      startState.instanceAdapterReference = null;

      machine.callServiceSynchronously(
          ComputeDescriptionFactoryService.SELF_LINK, startState, ComputeDescriptionService.ComputeDescription.class);
    }
  }
}

