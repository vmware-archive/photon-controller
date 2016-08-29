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

package com.vmware.photon.controller.api.model;

import com.vmware.photon.controller.api.model.base.Base;
import com.vmware.photon.controller.api.model.builders.AuthInfoBuilder;
import com.vmware.photon.controller.api.model.builders.NetworkConfigurationBuilder;
import com.vmware.photon.controller.api.model.builders.StatsInfoBuilder;
import com.vmware.photon.controller.api.model.helpers.JsonHelpers;
import com.vmware.photon.controller.api.model.helpers.Validator;

import com.google.common.collect.ImmutableList;
import org.hamcrest.MatcherAssert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.isA;
import static org.hamcrest.CoreMatchers.startsWith;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.IsEqual.equalTo;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Tests {@link Deployment}.
 */
public class DeploymentTest {

  private Deployment createDeployment(
      Set<String> imageDatastores,
      String syslogEndpoint,
      String ntpEndpoint,
      String loadBalancerAddress) {

    Deployment deployment = new Deployment();
    deployment.setId("id");
    deployment.setImageDatastores(imageDatastores);
    deployment.setSyslogEndpoint(syslogEndpoint);
    deployment.setNtpEndpoint(ntpEndpoint);
    deployment.setStats(new StatsInfoBuilder()
        .storeEndpoint("10.146.64.111")
        .storePort(2004).enabled(true)
        .storeType(StatsStoreType.GRAPHITE)
        .build());
    deployment.setAuth(new AuthInfoBuilder()
        .enabled(true)
        .endpoint("10.146.64.236")
        .port(443)
        .tenant("t")
        .username("u")
        .password("p")
        .securityGroups(Arrays.asList(new String[]{"adminGroup1", "adminGroup2"}))
        .build());

    IpRange ipRange = new IpRange();
    ipRange.setStart("192.168.0.2");
    ipRange.setEnd("192.168.0.254");

    deployment.setNetworkConfiguration(new NetworkConfigurationBuilder()
        .sdnEnabled(true)
        .networkManagerAddress("1.2.3.4")
        .networkManagerUsername("networkManagerUsername")
        .networkManagerPassword("networkManagerPassword")
        .networkZoneId("networkZoneId")
        .networkTopRouterId("networkTopRouterId")
        .edgeClusterId("edgeClusterId")
        .ipRange("10.0.0.1/24")
        .floatingIpRange(ipRange)
        .dhcpServers(Arrays.asList("192.10.0.1", "192.20.0.1"))
        .snatIp("192.168.0.1")
        .build());
    deployment.setLoadBalancerEnabled(true);
    deployment.setLoadBalancerAddress(loadBalancerAddress);
    return deployment;
  }

  /**
   * Dummy test case to make Intellij recognize this as a test class.
   */
  @Test(enabled = false)
  private void dummy() {
  }

  /**
   * Tests for is Deployment a Base model.
   */
  public class IsModelTest {

    @Test
    public void testIsABaseModel() throws Exception {
      MatcherAssert.assertThat(new Deployment(), isA(Base.class));
    }
  }

  /**
   * Tests for validations.
   */
  public class ValidationTest {

    Validator validator = new Validator();

    @DataProvider(name = "validDeployments")
    public Object[][] getValidDeployments() {
      return new Object[][]{
          {createDeployment(Collections.singleton("i"), "0.0.0.1", "0.0.0.2", "0.0.0.4")},
          {createDeployment(Collections.singleton("i"), null, null, null)},
      };
    }

    @Test(dataProvider = "validDeployments")
    public void testValidDeployment(Deployment deployment) {
      ImmutableList<String> violations = validator.validate(deployment);
      assertThat(violations.isEmpty(), is(true));
    }

    @DataProvider(name = "invalidDeployments")
    public Object[][] getInvalidDeployments() {
      return new Object[][]{
          {createDeployment(null, "0.0.0.1", "0.0.0.2", "0.0.0.4"),
              "imageDatastores may not be null (was null)"},
          {createDeployment(new HashSet<String>(), "0.0.0.1", "0.0.0.2", "0.0.0.4"),
              "imageDatastores size must be between 1 and 2147483647 (was [])"},
          {createDeployment(Collections.singleton("i"), "fake", "0.0.0.2", "0.0.0.4"),
              "syslogEndpoint fake is invalid IP or Domain Address"},
          {createDeployment(Collections.singleton("i"), "0.0.0.2", "fake", "0.0.0.4"),
              "ntpEndpoint fake is invalid IP or Domain Address"},
          {createDeployment(Collections.singleton("i"), "0.0.0.2", "0.0.0.1", "fake"),
              "loadBalancerAddress fake is invalid IP or Domain Address"},
      };
    }

    @Test(dataProvider = "invalidDeployments")
    public void testInvalidDeployments(Deployment deployment, String errorMsg) {
      ImmutableList<String> violations = validator.validate(deployment);

      assertThat(violations.size(), is(1));
      assertThat(violations.get(0), startsWith(errorMsg));
    }

  }

  /**
   * Tests {@link Deployment#toString()}.
   */
  public class ToStringTest {

    @Test
    public void testCorrectString() {
      String expectedString =
          "Deployment{id=id, Kind=deployment, imageDatastores=image-datastore1,image-datastore2, " +
              "syslogEndpoint=0.0.0.1, stats=StatsInfo{enabled=true, storeEndpoint=10.146.64.111, " +
              "storePort=2004, storeType=GRAPHITE}, " +
              "ntpEndpoint=0.0.0.2, useImageDatastoreForVms=false, " +
              "auth=AuthInfo{enabled=true, endpoint=10.146.64.236, port=443, " +
              "tenant=t, securityGroups=adminGroup1,adminGroup2}, " +
              "networkConfiguration=NetworkConfiguration{sdnEnabled=true, networkManagerAddress=1.2.3.4, " +
              "networkZoneId=networkZoneId, networkTopRouterId=networkTopRouterId, ipRange=10.0.0.1/24, " +
              "floatingIpRange=IpRange{start=192.168.0.2, end=192.168.0.254}, snatIp=192.168.0.1, " +
              "edgeClusterId=edgeClusterId, dhcpServers=192.10.0.1,192.20.0.1}, " +
              "loadBalancerEnabled=true, loadBalancerAddress=0.0.0.4, migrationProgress=null, " +
              "clusterConfigurations=null}";
      HashSet<String> imageDatastores = new HashSet<String>();
      imageDatastores.add("image-datastore1");
      imageDatastores.add("image-datastore2");
      Deployment deployment = createDeployment(
          imageDatastores, "0.0.0.1", "0.0.0.2", "0.0.0.4");
      assertThat(deployment.toString(), is(expectedString));
    }
  }

  /**
   * Tests JSON serialization.
   */
  public class SerializationTest {

    private static final String JSON_FILE = "fixtures/deployment.json";

    @Test
    public void testSerialization() throws Exception {
      Deployment deployment = createDeployment(
          Collections.singleton("image-datastore"), "0.0.0.1", "0.0.0.2", "0.0.0.4");
      deployment.setState(DeploymentState.CREATING);
      String json = JsonHelpers.jsonFixture(JSON_FILE);

      MatcherAssert.assertThat(JsonHelpers.asJson(deployment), is(equalTo(json)));
      MatcherAssert.assertThat(JsonHelpers.fromJson(json, Deployment.class), is(deployment));
    }
  }
}
