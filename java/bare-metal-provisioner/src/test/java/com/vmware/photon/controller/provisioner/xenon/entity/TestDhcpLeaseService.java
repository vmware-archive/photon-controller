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

import com.vmware.photon.controller.provisioner.xenon.entity.DhcpLeaseService.DhcpLeaseState;
import com.vmware.photon.controller.provisioner.xenon.entity.DhcpSubnetService.DhcpSubnetState;
import com.vmware.photon.controller.provisioner.xenon.helpers.TestUtils;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.common.test.VerificationHost;

import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;

import java.net.URI;
import java.util.concurrent.TimeUnit;

/**
 * This class implements tests for the {@link DhcpLeaseService} class.
 */
public class TestDhcpLeaseService {
  public VerificationHost host;

  private URI leaseFactory;
  private URI subnetFactory;

  @BeforeMethod
  public void setUp() throws Exception {
    this.host = VerificationHost.create(0);
    try {
      this.host.start();
      TestUtils.startDhcpServices(this.host);
    } catch (Throwable e) {
      throw new Exception(e);
    }
    this.leaseFactory = TestUtils.register(this.host, DhcpLeaseServiceFactory.class);
    this.subnetFactory = TestUtils.register(this.host, DhcpSubnetServiceFactory.class);
  }

  @AfterMethod
  public void tearDown() {
    this.host.tearDown();
  }

  public DhcpSubnetState createSubnet() throws Throwable {
    DhcpSubnetState state = new DhcpSubnetState();
    state.subnetAddress = "192.168.1.0/24";
    state.ranges = new DhcpSubnetState.Range[]{new DhcpSubnetState.Range()};
    state.ranges[0].low = "192.168.1.1";
    state.ranges[0].high = "192.168.1.10";
    return TestUtils.doPost(this.host, state, DhcpSubnetState.class, this.subnetFactory);
  }

  public DhcpLeaseState createLease() throws Throwable {
    DhcpLeaseState lease = new DhcpLeaseState();
    lease.networkDescriptionLink = createSubnet().documentSelfLink;
    lease.ip = "192.168.1.1";
    lease.mac = "001122334455";
    return TestUtils.doPost(this.host, lease, DhcpLeaseState.class, this.leaseFactory);
  }

  @Test
  public void testCreateLease() throws Throwable {
    DhcpLeaseState out = createLease();
    assertThat(out.documentSelfLink, is(notNullValue()));
  }

  @Test
  public void testCreateLeaseWithoutNetworkDescriptionLink() throws Throwable {
    DhcpLeaseState lease = new DhcpLeaseState();
    lease.ip = "192.168.1.1";
    lease.mac = "001122334455";

    try {
      TestUtils.doPost(this.host, lease, DhcpLeaseState.class, this.leaseFactory);
      Assert.fail("expected failure");
    } catch (Throwable e) {
      assertThat("networkDescriptionLink not specified", is(equalTo(e.getMessage())));
    }
  }

  @Test
  public void testCreateLeaseWithoutMAC() throws Throwable {
    DhcpSubnetState subnet = createSubnet();

    DhcpLeaseState lease = new DhcpLeaseState();
    lease.networkDescriptionLink = subnet.documentSelfLink;
    lease.ip = "192.168.1.1";

    try {
      TestUtils.doPost(this.host, lease, DhcpLeaseState.class, this.leaseFactory);
      Assert.fail("expected failure");
    } catch (Throwable e) {
      assertThat("MAC not specified", is(equalTo(e.getMessage())));
    }
  }

  @Test
  public void testCreateLeaseWithoutIP() throws Throwable {
    DhcpSubnetState subnet = createSubnet();

    DhcpLeaseState lease = new DhcpLeaseState();
    lease.documentExpirationTimeMicros = 0;
    lease.networkDescriptionLink = subnet.documentSelfLink;
    lease.mac = "001122334455";

    try {
      TestUtils.doPost(this.host, lease, DhcpLeaseState.class, this.leaseFactory);
      Assert.fail("expected failure");
    } catch (Throwable e) {
      assertThat("IP not specified", is(equalTo(e.getMessage())));
    }
  }

  @Test
  public void testLeasePatch() throws Throwable {
    DhcpLeaseState out = createLease();
    out.documentExpirationTimeMicros = Utils.getNowMicrosUtc() + TimeUnit.MINUTES.toMicros(20);

    DhcpLeaseState patched = patch(out);
    assertThat(out.documentExpirationTimeMicros, is(equalTo(patched.documentExpirationTimeMicros)));
  }

  @Test
  public void testLeasePatchWithoutExpirationTime() throws Throwable {
    DhcpLeaseState out = createLease();
    out.documentExpirationTimeMicros = 0;

    try {
      patch(out);
      Assert.fail("expected failure");
    } catch (Throwable e) {
      assertThat("cannot patch lease to not expire", is(equalTo(e.getMessage())));
    }
  }

  private DhcpLeaseState patch(DhcpLeaseState state) throws Throwable {
    final DhcpLeaseState[] doc = {null};
    this.host.testStart(1);
    Operation post = Operation
        .createPatch(UriUtils.buildUri(this.host, state.documentSelfLink))
        .setBody(state).setCompletion(
            (o, e) -> {
              if (e != null) {
                host.failIteration(e);
                return;
              }
              doc[0] = o.getBody(DhcpLeaseState.class);
              host.completeIteration();
            });
    this.host.send(post);
    this.host.testWait();
    this.host.logThroughput();

    return doc[0];
  }
}
