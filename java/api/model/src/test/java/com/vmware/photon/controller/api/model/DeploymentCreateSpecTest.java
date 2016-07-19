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

import com.vmware.photon.controller.api.model.builders.AuthConfigurationSpecBuilder;
import com.vmware.photon.controller.api.model.builders.NetworkConfigurationCreateSpecBuilder;
import com.vmware.photon.controller.api.model.builders.StatsInfoBuilder;
import com.vmware.photon.controller.api.model.helpers.JsonHelpers;
import com.vmware.photon.controller.api.model.helpers.Validator;

import com.google.common.collect.ImmutableList;
import org.hamcrest.MatcherAssert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.core.StringStartsWith.startsWith;
import static uk.co.datumedge.hamcrest.json.SameJSONAs.sameJSONAs;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Tests {@link DeploymentCreateSpec}.
 */
public class DeploymentCreateSpecTest {
  private AuthConfigurationSpec enabledAuthInfo = new AuthConfigurationSpecBuilder()
      .enabled(true)
      .tenant("t")
      .password("p")
      .securityGroups(Arrays.asList(new String[]{"adminGroup1", "adminGroup2"}))
      .build();

  private AuthConfigurationSpec disabledAuthInfo = new AuthConfigurationSpecBuilder()
      .enabled(false)
      .build();

  private NetworkConfigurationCreateSpec networkConfiguration = new NetworkConfigurationCreateSpecBuilder()
      .virtualNetworkEnabled(true)
      .networkManagerAddress("1.2.3.4")
      .networkManagerUsername("networkManagerUsername")
      .networkManagerPassword("networkManagerPassword")
      .networkTopRouterId("networkTopRouterId")
      .build();

  private StatsInfo enabledStatsInfo = new StatsInfoBuilder()
      .enabled(true)
      .storeEndpoint("10.146.64.111")
      .storePort(2004)
      .build();

  private StatsInfo disabledStatsInfo = new StatsInfoBuilder()
      .enabled(false)
      .build();

  private DeploymentCreateSpec createDeploymentCreateSpec(
      Set<String> imageDatastores,
      String syslogEndpoint,
      String ntpEndpoint) {

      return createDeploymentCreateSpec(
          imageDatastores, syslogEndpoint, ntpEndpoint, new StatsInfoBuilder().build(),
          new AuthConfigurationSpecBuilder().build());
  }

  private DeploymentCreateSpec createDeploymentCreateSpec(
      Set<String> imageDatastores,
      String syslogEndpoint,
      String ntpEndpoint,
      StatsInfo stats) {

    return createDeploymentCreateSpec(
        imageDatastores, syslogEndpoint, ntpEndpoint, stats, new AuthConfigurationSpecBuilder().build());

  }

  private DeploymentCreateSpec createDeploymentCreateSpec(
      Set<String> imageDatastores,
      String syslogEndpoint,
      String ntpEndpoint,
      AuthConfigurationSpec authInfo) {

    return createDeploymentCreateSpec(
        imageDatastores, syslogEndpoint, ntpEndpoint, new StatsInfoBuilder().build(), authInfo);
  }

  private DeploymentCreateSpec createDeploymentCreateSpec(
      Set<String> imageDatastores,
      String syslogEndpoint,
      String ntpEndpoint,
      StatsInfo stats,
      AuthConfigurationSpec authInfo) {

    DeploymentCreateSpec spec = new DeploymentCreateSpec();

    spec.setImageDatastores(imageDatastores);
    spec.setSyslogEndpoint(syslogEndpoint);
    spec.setNtpEndpoint(ntpEndpoint);
    spec.setStats(stats);
    spec.setAuth(authInfo);
    spec.setNetworkConfiguration(networkConfiguration);

    return spec;
  }

  /**
   * Dummy test case to make Intellij recognize this as a test class.
   */
  @Test(enabled = false)
  private void dummy() {
  }

  /**
   * Tests for validations.
   */
  public class ValidationTest {

    private Validator validator = new Validator();

    @DataProvider(name = "validDeployments")
    public Object[][] getValidDeployments() {
      return new Object[][]{
          {createDeploymentCreateSpec(Collections.singleton("i"), "0.0.0.1", "0.0.0.2")},
          {createDeploymentCreateSpec(Collections.singleton("i"), null, null)},
          {createDeploymentCreateSpec(Collections.singleton("i"), "0.0.0.1", "0.0.0.2", enabledAuthInfo)},
          {createDeploymentCreateSpec(Collections.singleton("i"), "0.0.0.1", "0.0.0.2", disabledAuthInfo)},
          {createDeploymentCreateSpec(Collections.singleton("i"), "0.0.0.1", "0.0.0.2", enabledStatsInfo)},
          {createDeploymentCreateSpec(Collections.singleton("i"), "0.0.0.1", "0.0.0.2", disabledStatsInfo)}
      };
    }

    @Test(dataProvider = "validDeployments")
    public void testValidDeployment(DeploymentCreateSpec spec) {
      ImmutableList<String> violations = validator.validate(spec);
      assertThat(violations.isEmpty(), is(true));
    }

    @DataProvider(name = "invalidDeployments")
    public Object[][] getInvalidDeployments() {
      return new Object[][]{
          {createDeploymentCreateSpec(null, "0.0.0.1", "0.0.0.2"),
              "imageDatastores may not be null (was null)"},
          {createDeploymentCreateSpec(new HashSet<String>(), "0.0.0.1", "0.0.0.2"),
              "imageDatastores size must be between 1 and 2147483647 (was [])"},
          {createDeploymentCreateSpec(Collections.singleton("i"), "fake", "0.0.0.2"),
              "syslogEndpoint fake is invalid IP or Domain Address"},
          {createDeploymentCreateSpec(Collections.singleton("i"), "0.0.0.2", "fake"),
              "ntpEndpoint fake is invalid IP or Domain Address"},
          {createDeploymentCreateSpec(Collections.singleton("i"), "0.0.0.2", "0.0.0.1", (StatsInfo) null),
              "stats may not be null (was null)"},
          {createDeploymentCreateSpec(Collections.singleton("i"), "0.0.0.1", "0.0.0.2", (AuthConfigurationSpec) null),
              "auth may not be null (was null)"}
      };
    }

    @Test(dataProvider = "invalidDeployments")
    public void testInvalidDeployments(DeploymentCreateSpec spec, String errorMsg) {
      List<String> violations = new ArrayList<>();

      violations.addAll(validator.validate(spec));
      if (spec.getAuth() != null) {
        violations.addAll(validator.validate(spec.getAuth()));
      }

      assertThat(violations.size(), is(1));
      assertThat(violations.get(0), startsWith(errorMsg));
    }

  }

  /**
   * Tests JSON serialization.
   */
  public class SerializationTest {

    private static final String JSON_FILE_WITH_AUTH = "fixtures/deployment-create-spec-with-auth.json";
    private static final String JSON_FILE_WITHOUT_AUTH = "fixtures/deployment-create-spec-without-auth.json";
    private static final String JSON_FILE_WITH_STATSINFO = "fixtures/deployment-create-spec-with-stats.json";
    private static final String JSON_FILE_WITHOUT_STATSINFO = "fixtures/deployment-create-spec-without-stats.json";


    @Test
    public void testSerializationWithAuth() throws Exception {
      DeploymentCreateSpec spec = createDeploymentCreateSpec(
          Collections.singleton("image-datastore"), "0.0.0.1", "0.0.0.2", enabledAuthInfo);

      String json = JsonHelpers.jsonFixture(JSON_FILE_WITH_AUTH);

      MatcherAssert.assertThat(JsonHelpers.asJson(spec), sameJSONAs(json).allowingAnyArrayOrdering());
      MatcherAssert.assertThat(JsonHelpers.fromJson(json, DeploymentCreateSpec.class), is(spec));
    }

    @Test
    public void testSerializationWithoutAuth() throws Exception {
      DeploymentCreateSpec spec = createDeploymentCreateSpec(
          Collections.singleton("image-datastore"), "0.0.0.1", "0.0.0.2", disabledAuthInfo);

      String json = JsonHelpers.jsonFixture(JSON_FILE_WITHOUT_AUTH);

      MatcherAssert.assertThat(JsonHelpers.asJson(spec), sameJSONAs(json).allowingAnyArrayOrdering());
      MatcherAssert.assertThat(JsonHelpers.fromJson(json, DeploymentCreateSpec.class), is(spec));
    }

    @Test
    public void testSerializationWithStatsInfo() throws Exception {
      DeploymentCreateSpec spec = createDeploymentCreateSpec(
          Collections.singleton("image-datastore"), "0.0.0.1", "0.0.0.2", enabledStatsInfo);

      String json = JsonHelpers.jsonFixture(JSON_FILE_WITH_STATSINFO);

      MatcherAssert.assertThat(JsonHelpers.asJson(spec), sameJSONAs(json).allowingAnyArrayOrdering());
      MatcherAssert.assertThat(JsonHelpers.fromJson(json, DeploymentCreateSpec.class), is(spec));
    }

    @Test
    public void testSerializationWithoutStatsInfo() throws Exception {
      DeploymentCreateSpec spec = createDeploymentCreateSpec(
          Collections.singleton("image-datastore"), "0.0.0.1", "0.0.0.2", disabledStatsInfo);

      String json = JsonHelpers.jsonFixture(JSON_FILE_WITHOUT_STATSINFO);

      MatcherAssert.assertThat(JsonHelpers.asJson(spec), sameJSONAs(json).allowingAnyArrayOrdering());
      MatcherAssert.assertThat(JsonHelpers.fromJson(json, DeploymentCreateSpec.class), is(spec));
    }
  }
}
