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
import com.vmware.photon.controller.provisioner.xenon.entity.DhcpSubnetService.AcquireLeaseRequest;
import com.vmware.photon.controller.provisioner.xenon.entity.DhcpSubnetService.DhcpSubnetState;
import com.vmware.photon.controller.provisioner.xenon.helpers.IPRange;
import com.vmware.photon.controller.provisioner.xenon.helpers.TestUtils;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.ServiceDocumentQueryResult;
import com.vmware.xenon.common.ServiceErrorResponse;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.common.test.VerificationHost;

import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;

import java.net.URI;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * This class implements tests for the {@link DhcpSubnetService} class.
 */
public class TestDhcpSubnetService {
  public VerificationHost host;

  private URI subnetFactory;
  private URI leasesFactory;

  public int nodeCount = 3;

  @BeforeMethod
  public void setUp() throws Exception {
    this.host = VerificationHost.create(0);
    try {
      this.host.start();
      TestUtils.startDhcpServices(this.host);
    } catch (Throwable e) {
      throw new Exception(e);
    }

    this.subnetFactory = UriUtils.buildUri(this.host,
        DhcpSubnetServiceFactory.class);
    this.leasesFactory = UriUtils.buildUri(this.host,
        DhcpLeaseServiceFactory.class);
  }

  private void setUpPeers() throws Throwable {
    if (this.nodeCount == 0) {
      return;
    }

    this.host.setUpPeerHosts(this.nodeCount);
    // set short maintenance interval before joining, for quick convergence
    for (URI baseHostUri : this.host.getNodeGroupMap().keySet()) {
      VerificationHost h = this.host.getInProcessHostMap().get(baseHostUri);
      TestUtils.startDhcpServices(h);
    }

    this.host.joinNodesAndVerifyConvergence(nodeCount, this.nodeCount, false);

    this.subnetFactory = UriUtils.buildUri(this.host.getPeerHost(),
        DhcpSubnetServiceFactory.class);
    this.leasesFactory = UriUtils.buildUri(this.host.getPeerHost(),
        DhcpLeaseServiceFactory.class);
  }


  @AfterMethod
  public void tearDown() {
    this.host.tearDownInProcessPeers();
    this.host.toggleNegativeTestMode(false);
    this.host.tearDown();
  }

  public DhcpSubnetState sampleSubnet() {
    DhcpSubnetState state = new DhcpSubnetState();
    state.id = UUID.randomUUID().toString();
    state.subnetAddress = "192.168.1.0/16";
    state.ranges = new DhcpSubnetState.Range[3];

    state.ranges[0] = new DhcpSubnetState.Range();
    state.ranges[0].low = "192.168.1.10";
    state.ranges[0].high = "192.168.1.20";

    state.ranges[1] = new DhcpSubnetState.Range();
    state.ranges[1].low = "192.168.1.110";
    state.ranges[1].high = "192.168.1.120";

    state.ranges[2] = new DhcpSubnetState.Range();
    state.ranges[2].low = "192.168.8.110";
    state.ranges[2].high = "192.168.8.120";

    return state;
  }

  public DhcpSubnetState createSubnet() throws Throwable {
    return createSubnet(sampleSubnet());
  }

  public DhcpSubnetState createSubnet(DhcpSubnetState state) throws Throwable {
    final String[] selfLink = {null};
    Operation post = Operation.createPost(this.subnetFactory)
        .setBody(state)
        .setCompletion((o, e) -> {
          try {
            if (e != null) {
              throw e;
            }

            ServiceDocument s = o.getBody(ServiceDocument.class);
            if (s.documentSelfLink == null) {
              throw new IllegalStateException("documentSelfLink is null");
            }

            selfLink[0] = s.documentSelfLink;
            this.host.completeIteration();
          } catch (Throwable ex) {
            this.host.failIteration(e);
          }
        });

    this.host.testStart(1);
    this.host.send(post);
    this.host.testWait();

    state.documentSelfLink = selfLink[0];
    return state;
  }

  public static int subnetSize(DhcpSubnetState state) {
    int available = 0;
    for (DhcpSubnetState.Range range : state.ranges) {
      IPRange r = new IPRange(range);
      available += r.size();
    }

    return available;
  }

  @Test
  public void testCreateSubnet() throws Throwable {

    DhcpSubnetState in = sampleSubnet();
    DhcpSubnetState out =
        TestUtils.doPost(this.host, in, DhcpSubnetState.class,
            this.subnetFactory);

    assertThat(in.subnetAddress, equalTo(out.subnetAddress));
    assertThat(in.ranges[0].low, equalTo(out.ranges[0].low));
    assertThat(in.ranges[0].high, equalTo(out.ranges[0].high));

    // Expect the id to be used as the documentSelfLink
    int pos = out.documentSelfLink.lastIndexOf('/');
    assertThat(in.id, is(equalTo(out.documentSelfLink.substring(pos + 1))));
  }

  @Test
  public void testCreateSubnetWithoutId() throws Throwable {
    DhcpSubnetState in = sampleSubnet();
    in.id = "";

    DhcpSubnetState out = TestUtils.doPost(this.host, in, DhcpSubnetState.class,
        this.subnetFactory);
    assertThat(out.id, is(notNullValue()));
    assertThat(out.id, not(equalTo("")));
  }

  @Test
  public void testInvalidCIDR() throws Throwable {
    DhcpSubnetState in = sampleSubnet();
    in.subnetAddress = "192.168.1.0";

    Exception e = null;
    try {
      TestUtils
          .doPost(this.host.getPeerHost(), in, DhcpSubnetState.class, this.subnetFactory);
    } catch (Exception ex) {
      e = ex;
    }

    assertThat(e, is(notNullValue()));
  }

  @Test
  public void testLeaseAcquire() throws Throwable {
    setUpPeers();

    DhcpSubnetState dhcpSubnetState = createSubnet();
    int available = subnetSize(dhcpSubnetState);

    Set<Operation> completed = Collections.synchronizedSet(new HashSet<>());

    Collection<Operation> failed = new LinkedList<>();

    this.host.testStart(available + 1);
    for (int i = 0; i < available + 1; i++) {
      acquireLease(String.format("%012x", i), dhcpSubnetState, (o, e) -> {
        if (e != null) {
          failed.add(o);
        } else {
          if (!completed.add(o)) {
            this.host.failIteration(new IllegalStateException("add failed"));
            return;
          }
        }
        host.completeIteration();
      });
    }
    this.host.testWait();

    Iterator<Operation> it = completed.iterator();
    while (it.hasNext()) {
      Operation o = it.next();
      DhcpLeaseState dhcpLeaseState = o.getBody(DhcpLeaseState.class);
      this.host.log(dhcpLeaseState.ip);
    }

    // We're allocating against all of the defined ranges plus 1.  Catch that error.
    assertThat(available, is(completed.size()));
    assertThat(1, is(failed.size()));

    // Expect the failure to indicate no addresses are available in the dynamic range
    Operation failure = failed.iterator().next();
    ServiceErrorResponse errorResponse = failure.getBody(ServiceErrorResponse.class);
    assertThat(errorResponse.message, containsString("No address available in subnet"));
  }

  /**
   * Test acquiring a lease for a single mac.  Then, issue another acquire request
   * and verify the existing lease gets updated.
   *
   * @throws Throwable
   */
  @Test
  public void testLeaseAcquireWithPatch() throws Throwable {
    setUpPeers();

    DhcpSubnetState dhcpSubnetState = createSubnet();

    this.host.testStart(1);
    acquireLease("00:11:22:33:44:55:66", dhcpSubnetState, (o, e) -> {
      if (e != null) {
        this.host.failIteration(e);
        return;
      }

      ServiceDocument doc = o.getBody(ServiceDocument.class);
      if (doc.documentVersion != 0) {
        this.host.failIteration(new IllegalStateException("documentVersion mismatch"));
        return;
      }
      host.completeIteration();
    });
    this.host.testWait();

    this.host.testStart(1);
    acquireLease("00:11:22:33:44:55:66", dhcpSubnetState, (o, e) -> {
      if (e != null) {
        this.host.failIteration(e);
        return;
      }

      ServiceDocument doc = o.getBody(ServiceDocument.class);
      if (doc.documentVersion != 1) {
        this.host.failIteration(new IllegalStateException("documentVersion mismatch"));
        return;
      }
      host.completeIteration();
    });
    this.host.testWait();
  }

  @Test
  public void testExpireLeases() throws Throwable {
    setUpPeers();

    this.host.setTimeoutSeconds(60);

    // we just need some small ranges
    DhcpSubnetState dhcpSubnetState = sampleSubnet();
    dhcpSubnetState.ranges = new DhcpSubnetState.Range[]{dhcpSubnetState.ranges[0],
        dhcpSubnetState.ranges[1]};
    dhcpSubnetState = createSubnet(dhcpSubnetState);
    int available = subnetSize(dhcpSubnetState);

    // expire in 2 seconds
    long delta = TimeUnit.SECONDS.toMicros(2)
        + DhcpLeaseService.EXPIRE_DELTA_US;
    long expiration = Utils.getNowMicrosUtc() + delta;

    this.host.testStart(available);
    for (int i = 0; i < available; i++) {
      AcquireLeaseRequest request = new AcquireLeaseRequest();
      request.mac = String.format("%012x", i);

      // force the leases to expire nearly immediately
      request.expirationTimeMicros = expiration;

      Operation patch = Operation
          .createPatch(
              UriUtils.extendUri(this.host.getPeerHost().getUri(),
                  dhcpSubnetState.documentSelfLink))
          .setBody(request)
          .setCompletion((o, e) -> {
            if (e != null) {
              this.host.failIteration(e);
              return;
            }
            this.host.completeIteration();
          });
      this.host.send(patch);
    }
    this.host.testWait();

    try {
      waitForLeasesToExpire(dhcpSubnetState.documentSelfLink, delta);
    } catch (Throwable e) {
      Assert.fail(e.toString());
      return;
    }

    // now acquire all of the leases again and check none borked.
    this.host.testStart(available);
    for (int i = 0; i < available; i++) {
      acquireLease(String.format("%012x", i + available), dhcpSubnetState, (o, e) -> {
        if (e != null) {
          this.host.failIteration(e);
          return;
        }

        host.completeIteration();
      });
    }
    this.host.testWait();
  }

  private void waitForLeasesToExpire(String subnetLink, long delta) throws Throwable {
    AtomicBoolean leasesExist = new AtomicBoolean(true);

    delta = Math.max(TimeUnit.SECONDS.toMicros(this.host.getTimeoutSeconds()), delta);
    long expirationTime = Utils.getNowMicrosUtc() + delta;

    do {
      this.host.testStart(1);
      this.host.sendRequest(Operation
          .createGet(this.leasesFactory)
          .setReferer(this.host.getReferer())
          .setCompletion(
              (o, e) -> {
                ServiceDocumentQueryResult res = o
                    .getBody(ServiceDocumentQueryResult.class);
                leasesExist.compareAndSet(true, res.documentLinks.size() > 0);
                this.host.completeIteration();
              }));
      this.host.testWait();
      Thread.sleep(250);
    } while (leasesExist.get() && Utils.getNowMicrosUtc() < expirationTime);

    if (leasesExist.get()) {
      throw new TimeoutException("Leases still exist");
    }
  }

  private void acquireLease(String mac, DhcpSubnetState subnet, Operation.CompletionHandler h) {
    AcquireLeaseRequest request = new AcquireLeaseRequest();
    request.mac = mac;
    Operation patch = Operation
        .createPatch(
            UriUtils.buildUri(this.host.getPeerHost().getUri(), subnet.documentSelfLink))
        .setBody(request)
        .setCompletion(h);
    this.host.send(patch);
  }
}
