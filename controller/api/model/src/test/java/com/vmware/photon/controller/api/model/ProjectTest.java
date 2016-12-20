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

import com.vmware.photon.controller.api.model.base.VisibleModel;
import static com.vmware.photon.controller.api.model.helpers.JsonHelpers.asJson;
import static com.vmware.photon.controller.api.model.helpers.JsonHelpers.fromJson;
import static com.vmware.photon.controller.api.model.helpers.JsonHelpers.jsonFixture;

import org.hamcrest.MatcherAssert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.isA;
import static org.hamcrest.MatcherAssert.assertThat;
import static uk.co.datumedge.hamcrest.json.SameJSONAs.sameJSONAs;

import java.util.ArrayList;
import java.util.List;

/**
 * Tests {@link Project}.
 */
public class ProjectTest {

  @Test
  public void testIsAVisibleModel() throws Exception {
    MatcherAssert.assertThat(new Project(), isA(VisibleModel.class));
  }

  /**
   * Tests JSON serialization.
   */
  public class SerializationTest {

    private static final String JSON_FILE = "fixtures/project.json";

    private Project project;

    @BeforeMethod
    public void setUp() {
      project = createValidProject();
    }

    @Test
    public void testSerialization() throws Exception {
      String json = jsonFixture(JSON_FILE);

      assertThat(asJson(project), sameJSONAs(json).allowingAnyArrayOrdering());
      assertThat(fromJson(json, Project.class), is(project));
    }

    private Project createValidProject() {
      List<QuotaLineItem> testLimits = new ArrayList<>();
      testLimits.add(new QuotaLineItem("vm.cost", 50.0, QuotaUnit.COUNT));
      testLimits.add(new QuotaLineItem("persistent-disk.cost", 50.0, QuotaUnit.COUNT));
      testLimits.add(new QuotaLineItem("network.cost", 50.0, QuotaUnit.COUNT));
      testLimits.add(new QuotaLineItem("ephemeral-disk.cost", 50.0, QuotaUnit.COUNT));

      List<QuotaLineItem> testUsage = new ArrayList<>();
      testUsage.add(new QuotaLineItem("vm.cost", 27.5, QuotaUnit.COUNT));
      testUsage.add(new QuotaLineItem("persistent-disk.cost", 13.125, QuotaUnit.COUNT));
      testUsage.add(new QuotaLineItem("network.cost", 11.5, QuotaUnit.COUNT));
      testUsage.add(new QuotaLineItem("ephemeral-disk.cost", 5.25, QuotaUnit.COUNT));

      ProjectTicket resourceTicket = new ProjectTicket();
      resourceTicket.setLimits(testLimits);
      resourceTicket.setUsage(testUsage);
      resourceTicket.setTenantTicketId("SOME-TENANT-ID");
      resourceTicket.setTenantTicketName("SOME-TENANT-NAME");

      Project project = new Project();
      project.setId("1-base");
      project.setSelfLink("http://localhost:9080/v1/projects/1-base");
      project.setName("myproject");
      project.setResourceTicket(resourceTicket);
      project.setTenantId("tenant-id");

      return project;
    }
  }
}
