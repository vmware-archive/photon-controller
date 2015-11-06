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

package com.vmware.photon.controller.api;

import com.vmware.photon.controller.api.base.Base;
import com.vmware.photon.controller.api.builders.AuthInfoBuilder;
import com.vmware.photon.controller.api.helpers.Validator;
import static com.vmware.photon.controller.api.helpers.JsonHelpers.asJson;
import static com.vmware.photon.controller.api.helpers.JsonHelpers.fromJson;
import static com.vmware.photon.controller.api.helpers.JsonHelpers.jsonFixture;

import com.google.common.collect.ImmutableList;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.isA;
import static org.hamcrest.CoreMatchers.startsWith;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.IsEqual.equalTo;

import java.util.Arrays;

/**
 * Tests {@link Deployment}.
 */
public class DeploymentTest {

  private Deployment createDeployment(
      String imageDatastore,
      String syslogEndpoint,
      String ntpEndpoint) {
    Deployment deployment = new Deployment();
    deployment.setId("id");
    deployment.setImageDatastore(imageDatastore);
    deployment.setSyslogEndpoint(syslogEndpoint);
    deployment.setNtpEndpoint(ntpEndpoint);
    deployment.setAuth(new AuthInfoBuilder()
        .enabled(true)
        .endpoint("10.146.64.236")
        .port(443)
        .tenant("t")
        .username("u")
        .password("p")
        .securityGroups(Arrays.asList(new String[] { "adminGroup1", "adminGroup2"}))
        .build());
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
      assertThat(new Deployment(), isA(Base.class));
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
          {createDeployment("i", "0.0.0.1", "0.0.0.2")},
          {createDeployment("i", null, null)},
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
          {createDeployment(null, "0.0.0.1", "0.0.0.2"),
              "imageDatastore may not be null (was null)"},
          {createDeployment("", "0.0.0.1", "0.0.0.2"),
              "imageDatastore size must be between 1 and 2147483647 (was )"},
          {createDeployment("i", "fake", "0.0.0.2"),
              "syslogEndpoint fake is invalid IP or Domain Address"},
          {createDeployment("i", "0.0.0.2", "fake"),
              "ntpEndpoint fake is invalid IP or Domain Address"},
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
          "Deployment{id=id, Kind=deployment, imageDatastore=image-datastore, syslogEndpoint=0.0.0.1, " +
              "ntpEndpoint=0.0.0.2, useImageDatastoreForVms=false, " +
              "auth=AuthInfo{enabled=true, endpoint=10.146.64.236, port=443," +
              " tenant=t, username=u, password=p, securityGroups=adminGroup1,adminGroup2}, loadBalancerEnabled=true," +
              " migrationProgress=null}";
      Deployment deployment = createDeployment("image-datastore", "0.0.0.1", "0.0.0.2");
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
      Deployment deployment = createDeployment("image-datastore", "0.0.0.1", "0.0.0.2");
      deployment.setState(DeploymentState.CREATING);
      String json = jsonFixture(JSON_FILE);

      assertThat(asJson(deployment), is(equalTo(json)));
      assertThat(fromJson(json, Deployment.class), is(deployment));
    }
  }
}
