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

package com.vmware.photon.controller.provisioner.xenon.entity;

import com.vmware.photon.controller.provisioner.xenon.entity.DhcpSubnetService.DhcpSubnetState;
import com.vmware.photon.controller.provisioner.xenon.helpers.TestUtils;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.test.VerificationHost;

import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsNull.notNullValue;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * This class implements tests for the {@link DhcpConfigurationService} class.
 */
public class TestDhcpConfigurationService {
  public VerificationHost host;
  public String testMethod;

  private URI uri;

  @BeforeMethod
  public void setUp() throws Exception {
    this.host = VerificationHost.create(0);
    try {
      this.host.start();
      TestUtils.startDhcpServices(this.host);
      this.uri = TestUtils.register(this.host, DhcpConfigurationServiceFactory.class);
    } catch (Throwable e) {
      throw new Exception(e);
    }
  }

  @AfterMethod
  public void tearDown() {
    this.host.tearDown();
  }

  @Test
  public void testValidConfigurationForSubnet() throws Throwable {
    DhcpSubnetState inSubnet = new DhcpSubnetState();
    inSubnet.subnetAddress = "192.4.0.0/16";
    inSubnet.documentSelfLink = inSubnet.subnetAddress;

    URI subnetURI = TestUtils.register(this.host, DhcpSubnetServiceFactory.class);

    // set the subnetAddress first
    DhcpSubnetState outSubnet =
        TestUtils.doPost(this.host, inSubnet, DhcpSubnetState.class,
            subnetURI);
    assertThat(outSubnet, is(notNullValue()));

    DhcpConfigurationService.State inState = new DhcpConfigurationService.State();
    inState.isEnabled = true;
    inState.hostBootImageReference = new URI("http://somehost:8000/CargosImage.vhd");
    inState.routerAddresses = new String[]{"8.8.8.8"};
    inState.nameServerAddresses = new String[]{"8.8.8.8"};
    inState.documentSelfLink = "test";

    DhcpConfigurationService.State outState =
        TestUtils.doPost(this.host, inState,
            DhcpConfigurationService.State.class,
            this.uri);

    assertThat(outState, is(notNullValue()));
    assertThat(outState.routerAddresses[0], is(inState.routerAddresses[0]));
    assertThat(outState.nameServerAddresses[0], is(inState.nameServerAddresses[0]));
  }

  @Test
  public void testParallelValidConfiguration() throws Throwable {
    int iterations = 250;

    DhcpSubnetState inSubnet = new DhcpSubnetState();
    inSubnet.subnetAddress = "192.3.0.0/16";
    inSubnet.documentSelfLink = inSubnet.subnetAddress;

    URI subnetURI = TestUtils.register(this.host, DhcpSubnetServiceFactory.class);

    // set the subnetAddress first
    DhcpSubnetState outSubnet =
        TestUtils.doPost(this.host, inSubnet, DhcpSubnetState.class,
            subnetURI);
    assertThat(outSubnet, is(notNullValue()));

    List<DhcpConfigurationService.State> inState = new ArrayList<>();
    for (int i = 0; i < iterations; i++) {
      DhcpConfigurationService.State in = new DhcpConfigurationService.State();
      in.isEnabled = true;
      in.hostBootImageReference = new URI(String.format(
          "http://somehost:8000/CargosImage%d.vhd", i));
      in.routerAddresses = new String[]{String.format("1.1.1.%d", i)};
      in.nameServerAddresses = new String[]{String.format("8.8.8.%d", i)};

      // We're setting the selflink in the test to easily find the config in the returned map.
      // In production
      // selflink will not be set.
      in.documentSelfLink = Integer.toString(i);

      inState.add(in);
    }

    Map<URI, DhcpConfigurationService.State> outState = TestUtils
        .doParallelPost(
            this.host, inState,
            DhcpConfigurationService.State.class, this.uri);

    assertThat(inState.size(), is(iterations));
    assertThat(outState.size(), is(iterations));

    for (int i = 0; i < inState.size(); i++) {
      DhcpConfigurationService.State in = inState.get(i);
      DhcpConfigurationService.State out = outState
          .get(UriUtils.buildUri(this.host,
              UriUtils.buildUriPath(DhcpConfigurationServiceFactory.SELF_LINK,
                  Integer.toString(i))));

      assertThat(out, is(notNullValue()));
      assertThat(out.documentVersion, is(in.documentVersion));
      assertThat(out.routerAddresses[0], is(in.routerAddresses[0]));
      assertThat(out.nameServerAddresses[0], is(in.nameServerAddresses[0]));
    }
  }

  @Test
  public void testInValidNameserver() throws Throwable {
    DhcpSubnetState inSubnet = new DhcpSubnetState();
    inSubnet.subnetAddress = "192.2.0.0/16";
    inSubnet.documentSelfLink = inSubnet.subnetAddress;

    URI subnetURI = TestUtils.register(this.host, DhcpSubnetServiceFactory.class);

    // set the subnetAddress first
    DhcpSubnetState outSubnet =
        TestUtils.doPost(this.host, inSubnet, DhcpSubnetState.class,
            subnetURI);
    assertThat(outSubnet, is(notNullValue()));

    DhcpConfigurationService.State inState = new DhcpConfigurationService.State();
    inState.nameServerAddresses = new String[]{"1.1.1.1", "8.8.8.800"};

    this.host.testStart(1);
    this.host.send(Operation.createPost(this.uri)
        .setBody(inState)
        .setCompletion((o, e) -> {
          // we're expecting a failure
          if (e == null) {
            this.host.failIteration(new AssertionError());
            return;
          }
          this.host.completeIteration();
          ;
        }));
    this.host.testWait();
  }

  @Test
  public void testInValidRouter() throws Throwable {
    DhcpSubnetState inSubnet = new DhcpSubnetState();
    inSubnet.subnetAddress = "192.1.0.0/16";
    inSubnet.documentSelfLink = inSubnet.subnetAddress;

    URI subnetURI = TestUtils.register(this.host, DhcpSubnetServiceFactory.class);

    // set the subnetAddress first
    DhcpSubnetState outSubnet =
        TestUtils.doPost(this.host, inSubnet, DhcpSubnetState.class,
            subnetURI);
    assertThat(outSubnet, is(notNullValue()));

    DhcpConfigurationService.State inState = new DhcpConfigurationService.State();
    inState.routerAddresses = new String[]{"1.1.1.1", "8.8.8.800"};

    DhcpConfigurationService.State outState = null;
    try {
      outState = TestUtils.doPost(this.host, inState,
          DhcpConfigurationService.State.class,
          this.uri);
      Assert.fail("IllegalArgumentException not thrown.");
    } catch (Throwable e) {

    }

    assertThat(outState, is(notNullValue()));
  }

  @Test
  public void testPatch() throws Throwable {
    DhcpSubnetState inSubnet = new DhcpSubnetState();
    inSubnet.subnetAddress = "192.0.0.0/16";
    inSubnet.documentSelfLink = inSubnet.subnetAddress;

    URI subnetURI = TestUtils.register(this.host, DhcpSubnetServiceFactory.class);

    DhcpSubnetState outSubnet = TestUtils.doPost(this.host, inSubnet,
        DhcpSubnetState.class, subnetURI);
    assertThat(outSubnet, is(notNullValue()));

    // POST the initial state.
    DhcpConfigurationService.State inState = new DhcpConfigurationService.State();
    inState.isEnabled = true;
    inState.hostBootImageReference = new URI("http://somehost:8000/CargosImage.vhd");
    inState.routerAddresses = new String[]{"8.8.8.8"};
    inState.nameServerAddresses = new String[]{"8.8.8.8"};
    inState.documentSelfLink = "test";

    DhcpConfigurationService.State outState =
        TestUtils.doPost(this.host, inState,
            DhcpConfigurationService.State.class,
            this.uri);

    // patch the state.
    DhcpConfigurationService.State patchState =
        new DhcpConfigurationService.State();
    patchState.isEnabled = false;
    patchState.hostBootImageReference = new URI("http://somehost:8000/CargosImageNew.vhd");
    patchState.routerAddresses = new String[]{"1.1.1.1"};
    patchState.nameServerAddresses = new String[]{"1.1.1.1"};

    URI patchUri = UriUtils.buildUri(this.host, outState.documentSelfLink);

    this.host.testStart(1);
    Operation patch = Operation
        .createPatch(patchUri)
        .setBody(patchState).setCompletion(this.host.getCompletion());
    this.host.send(patch);
    this.host.testWait();

    outState = this.host.getServiceState(null,
        DhcpConfigurationService.State.class,
        patchUri);

    assertThat(outState, is(notNullValue()));
    assertThat(outState.isEnabled, is(patchState.isEnabled));
    assertThat(outState.hostBootImageReference, is(patchState.hostBootImageReference));
    assertThat(outState.routerAddresses[0], is(patchState.routerAddresses[0]));
    assertThat(outState.nameServerAddresses[0], is(patchState.nameServerAddresses[0]));
  }
}
