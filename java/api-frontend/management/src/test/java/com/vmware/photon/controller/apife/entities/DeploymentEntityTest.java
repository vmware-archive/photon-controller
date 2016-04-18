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

package com.vmware.photon.controller.apife.entities;

import com.vmware.photon.controller.api.DeploymentState;
import com.vmware.photon.controller.api.StatsStoreType;

import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

import java.util.Arrays;
import java.util.Collections;

/**
 * Tests {@link DeploymentEntity}.
 */
public class DeploymentEntityTest {
  private DeploymentEntity entity;

  /**
   * Dummy test case to make Intellij recognize this as a test class.
   */
  @Test(enabled = false)
  private void dummy() {
  }

  /**
   * Test getters and setters.
   */
  public class AccessorsTest {
    @BeforeMethod
    public void setUp() {
      entity = new DeploymentEntity();
    }

    @Test
    public void testAll() {
      entity.setState(DeploymentState.READY);
      entity.setSyslogEndpoint("http://syslog");
      entity.setStatsEnabled(true);
      entity.setStatsStoreEndpoint("http://statsStore");
      entity.setStatsStorePort(2004);
      entity.setStatsStoreType(StatsStoreType.GRAPHITE);
      entity.setAuthEnabled(true);
      entity.setOauthEndpoint("192.168.0.1");
      entity.setOauthPort(443);
      entity.setOauthTenant("tenant");
      entity.setOauthUsername("username");
      entity.setOauthPassword("password");
      entity.setOauthSecurityGroups(Arrays.asList(new String[]{"authGroup1, authGroup2"}));
      entity.setNetworkManagerAddress("1.2.3.4");
      entity.setNetworkManagerUsername("networkManagerUsername");
      entity.setNetworkManagerPassword("networkManagerPassword");
      entity.setNtpEndpoint("http://ntp");
      entity.setImageDatastores(Collections.singleton("datastore1"));
      entity.setUseImageDatastoreForVms(true);
      entity.setOperationId("opid");
      entity.setLoadBalancerAddress("0.0.0.1");

      assertThat(entity.getState(), is(DeploymentState.READY));
      assertThat(entity.getSyslogEndpoint(), is("http://syslog"));
      assertThat(entity.getStatsStoreEndpoint(), is("http://statsStore"));
      assertThat(entity.getAuthEnabled(), is(true));
      assertThat(entity.getOauthEndpoint(), is("192.168.0.1"));
      assertThat(entity.getOauthPort(), is(443));
      assertThat(entity.getOauthTenant(), is("tenant"));
      assertThat(entity.getOauthUsername(), is("username"));
      assertThat(entity.getOauthPassword(), is("password"));
      assertThat(entity.getOauthSecurityGroups(), is(Arrays.asList(new String[]{"authGroup1, authGroup2"})));
      assertThat(entity.getNetworkManagerAddress(), is("1.2.3.4"));
      assertThat(entity.getNetworkManagerUsername(), is("networkManagerUsername"));
      assertThat(entity.getNetworkManagerPassword(), is("networkManagerPassword"));
      assertThat(entity.getNtpEndpoint(), is("http://ntp"));
      Assert.assertTrue(entity.getImageDatastores().contains("datastore1"));
      assertThat(entity.getUseImageDatastoreForVms(), is(true));
      assertThat(entity.getOperationId(), is("opid"));
      assertThat(entity.getLoadBalancerAddress(), is("0.0.0.1"));
    }
  }

  /**
   * Test equals method.
   */
  public class EqualsTest {
    @BeforeMethod
    public void setUp() {
      entity = new DeploymentEntity();
      entity.setSyslogEndpoint("http://syslog");
      entity.setStatsEnabled(true);
      entity.setStatsStoreEndpoint("http://statsStore");
      entity.setStatsStorePort(2004);
      entity.setStatsStoreType(StatsStoreType.GRAPHITE);
      entity.setAuthEnabled(true);
      entity.setOauthEndpoint("192.168.0.1");
      entity.setOauthPort(443);
      entity.setOauthTenant("t");
      entity.setOauthUsername("u");
      entity.setOauthPassword("p");
      entity.setOauthSecurityGroups(Arrays.asList(new String[]{"adminGroup1", "adminGroup2"}));
      entity.setNetworkManagerAddress("1.2.3.4");
      entity.setNetworkManagerUsername("networkManagerUsername");
      entity.setNetworkManagerPassword("networkManagerPassword");
      entity.setNtpEndpoint("http://ntp");
      entity.setImageDatastores(Collections.singleton("datastore1"));
      entity.setUseImageDatastoreForVms(true);
      entity.setLoadBalancerAddress("0.0.0.1");
    }

    @Test
    public void testEqualsItself() {
      assertThat(entity.equals(entity), is(true));
    }

    @Test
    public void testEqualsClone() throws Throwable {
      assertThat(entity.equals(entity.clone()), is(true));
    }

    @Test
    public void testStateIsIgnored() throws Throwable {
      DeploymentEntity clone = (DeploymentEntity) entity.clone();
      clone.setState(DeploymentState.READY);

      assertThat(entity.equals(clone), is(true));
    }

    @Test
    public void testOperationIdIsIgnored() throws Throwable {
      DeploymentEntity clone = (DeploymentEntity) entity.clone();
      clone.setOperationId("opid");

      assertThat(entity.equals(clone), is(true));
    }

    @Test
    public void testDoesNotEqual() throws Throwable {
      DeploymentEntity other = (DeploymentEntity) entity.clone();
      other.setSyslogEndpoint(null);

      assertThat(entity.equals(other), is(false));
    }
  }
}
