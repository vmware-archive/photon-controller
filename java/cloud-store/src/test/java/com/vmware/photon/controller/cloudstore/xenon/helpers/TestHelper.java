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

package com.vmware.photon.controller.cloudstore.xenon.helpers;

import com.vmware.photon.controller.api.HostState;
import com.vmware.photon.controller.api.UsageTag;
import com.vmware.photon.controller.cloudstore.xenon.entity.HostService;
import com.vmware.photon.controller.cloudstore.xenon.entity.HostServiceFactory;
import com.vmware.photon.controller.common.xenon.BasicServiceHost;
import com.vmware.photon.controller.common.xenon.MultiHostEnvironment;
import com.vmware.photon.controller.common.xenon.ServiceUtils;
import com.vmware.photon.controller.common.xenon.XenonHostInfoProvider;
import com.vmware.photon.controller.common.xenon.XenonRestClient;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.ServiceHost;

import com.google.common.collect.ImmutableSet;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.closeTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * This class implements helper routines for tests.
 */
public class TestHelper {

  public static HostService.State getHostServiceStartState(Set<String> usageTags) {
    HostService.State startState = new HostService.State();
    startState.state = HostState.CREATING;
    startState.hostAddress = "hostAddress";
    startState.userName = "userName";
    startState.password = "password";
    startState.availabilityZoneId = "availabilityZone";
    startState.esxVersion = "6.0";
    startState.usageTags = new HashSet<>(usageTags);
    startState.reportedImageDatastores = new HashSet<>(Arrays.asList("datastore1"));

    if (usageTags.contains(UsageTag.MGMT.name())) {
      startState.metadata = new HashMap<>();
      startState.metadata.put(HostService.State.METADATA_KEY_NAME_MANAGEMENT_DATASTORE, "datastore1");
      startState.metadata.put(HostService.State.METADATA_KEY_NAME_MANAGEMENT_NETWORK_DNS_SERVER, "8.8.8.8");
      startState.metadata.put(HostService.State.METADATA_KEY_NAME_MANAGEMENT_NETWORK_GATEWAY, "8.8.8.143");
      startState.metadata.put(HostService.State.METADATA_KEY_NAME_MANAGEMENT_NETWORK_IP, "8.8.8.27");
      startState.metadata.put(HostService.State.METADATA_KEY_NAME_MANAGEMENT_NETWORK_NETMASK, "255.255.255.0");
      startState.metadata.put(HostService.State.METADATA_KEY_NAME_MANAGEMENT_PORTGROUP, "VM Network");
    }

    return startState;
  }

  public static <H extends ServiceHost & XenonHostInfoProvider> HostService.State createHostService(
      MultiHostEnvironment<H> testEnvironment,
      Set<String> usageTags) throws Throwable {
    return createHostService(testEnvironment, getHostServiceStartState(usageTags));
  }

  public static <H extends ServiceHost & XenonHostInfoProvider> HostService.State createHostService(
      MultiHostEnvironment<H> testEnvironment,
      HostService.State startState)
      throws Throwable {
    return testEnvironment.callServiceSynchronously(
        HostServiceFactory.SELF_LINK,
        startState,
        HostService.State.class);
  }

  public static HostService.State getHostServiceStartState() {
    return getHostServiceStartState(ImmutableSet.of(UsageTag.MGMT.name(), UsageTag.CLOUD.name()));
  }

  public static <T extends ServiceDocument> void testExpirationOnDelete(
      XenonRestClient xenonRestClient,
      BasicServiceHost host,
      String factoryLink,
      T testState,
      Class<T> stateType,
      Long currentStateExpiration,
      Long deleteStateExpiration,
      Long expectedExpiration) throws Throwable {

    testState.documentExpirationTimeMicros = currentStateExpiration;
    Operation result = xenonRestClient.post(factoryLink, testState);
    assertThat(result.getStatusCode(), is(200));

    T createdState = result.getBody(stateType);
    assertThat(createdState.documentExpirationTimeMicros, is(currentStateExpiration));

    T savedState = host.getServiceState(stateType, createdState.documentSelfLink);
    assertThat(savedState.documentExpirationTimeMicros, is(currentStateExpiration));

    T deleteState = stateType.newInstance();
    deleteState.documentExpirationTimeMicros = deleteStateExpiration;
    result = xenonRestClient.delete(createdState.documentSelfLink, deleteState);
    assertThat(result.getStatusCode(), is(200));

    T deletedState = result.getBody(stateType);
    if (currentStateExpiration > 0) {
      assertThat(new BigDecimal(deletedState.documentExpirationTimeMicros),
          not(closeTo(
              new BigDecimal(
                  ServiceUtils.computeExpirationTime(ServiceUtils.DEFAULT_ON_DELETE_DOC_EXPIRATION_TIME_MICROS)),
              new BigDecimal(TimeUnit.MINUTES.toMicros(1)))));

    }
    if (deleteStateExpiration > 0) {
      assertThat(deletedState.documentExpirationTimeMicros, not(currentStateExpiration));
      assertThat(new BigDecimal(deletedState.documentExpirationTimeMicros),
          not(closeTo(
              new BigDecimal(
                  ServiceUtils.computeExpirationTime(ServiceUtils.DEFAULT_ON_DELETE_DOC_EXPIRATION_TIME_MICROS)),
              new BigDecimal(TimeUnit.MINUTES.toMicros(1)))));

    }
    assertThat(new BigDecimal(deletedState.documentExpirationTimeMicros),
        is(closeTo(
            new BigDecimal(
                expectedExpiration),
            new BigDecimal(TimeUnit.MINUTES.toMicros(1)))));
  }
}
