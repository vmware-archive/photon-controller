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

import com.vmware.photon.controller.api.builders.ComponentInstanceBuilder;
import com.vmware.photon.controller.api.builders.ComponentStatusBuilder;
import com.vmware.photon.controller.api.builders.SystemStatusBuilder;
import com.vmware.photon.controller.api.helpers.Validator;
import com.vmware.photon.controller.status.gen.StatusType;
import static com.vmware.photon.controller.api.helpers.JsonHelpers.asJson;
import static com.vmware.photon.controller.api.helpers.JsonHelpers.fromJson;
import static com.vmware.photon.controller.api.helpers.JsonHelpers.jsonFixture;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static uk.co.datumedge.hamcrest.json.SameJSONAs.sameJSONAs;

import java.util.ArrayList;
import java.util.List;

/**
 * Tests {@link SystemStatus}.
 */
public class SystemStatusTest {

  private SystemStatus createSystemStatus() {
    SystemStatusBuilder systemStatusBuilder = new SystemStatusBuilder();
    systemStatusBuilder.status(StatusType.ERROR);
    int i = 1;
    systemStatusBuilder.components(ImmutableList.of(
        new ComponentStatusBuilder().component(Component.PHOTON_CONTROLLER).status(StatusType.READY)
            .instances(ImmutableList.of(
                new ComponentInstanceBuilder().address("127.0.0." + i++).status(StatusType.READY).build(),
                new ComponentInstanceBuilder().address("127.0.0." + i++).status(StatusType.READY).build(),
                new ComponentInstanceBuilder().address("127.0.0." + i++).status(StatusType.READY).build()))
            .message("MGMT plane is deployed")
            .stats(ImmutableMap.of("READY", "1"))
            .buildInfo("version 0.0.1")
            .build()
    ));
    return systemStatusBuilder.build();
  }

  /**
   * Dummy test case to make Intellij recognize this as a test class.
   */
  @Test(enabled = true)
  private void dummy() {
  }

  /**
   * Tests for validations.
   */
  public class ValidationTest {

    private Validator validator = new Validator();

    @Test
    public void testValidSystemStatus() {
      ImmutableList<String> violations = validator.validate(createSystemStatus());
      assertThat(violations.isEmpty(), is(true));
    }

    @DataProvider(name = "InvalidSystemStatuses")
    public Object[][] getInvalidSystemStatuses() {
      List<Object[]> l = new ArrayList<>();
      SystemStatus status = createSystemStatus();
      status.setStatus(null);
      l.add(new Object[]{status, "status may not be null (was null)"});

      status = createSystemStatus();
      status.setComponents(null);
      l.add(new Object[]{status, "components may not be null (was null)"});

      status = createSystemStatus();
      status.getComponents().get(0).setComponent(null);
      l.add(new Object[]{status, "components[0].component may not be null (was null)"});

      status = createSystemStatus();
      status.getComponents().get(0).setStatus(null);
      l.add(new Object[]{status, "components[0].status may not be null (was null)"});

      status = createSystemStatus();
      status.getComponents().get(0).setInstances(null);
      l.add(new Object[]{status, "components[0].instances may not be null (was null)"});

      status = createSystemStatus();
      status.getComponents().get(0).getInstances().get(0).setAddress("invalid address");
      l.add(new Object[]{status, "components[0].instances[0].address invalid address is invalid IP " +
          "or Domain Address (was invalid address)"});

      status = createSystemStatus();
      status.getComponents().get(0).getInstances().get(0).setStatus(null);
      l.add(new Object[]{status, "components[0].instances[0].status may not be null (was null)"});

      return l.toArray(new Object[l.size()][]);
    }

    @Test(dataProvider = "InvalidSystemStatuses")
    public void testInvalidSystemStatus(SystemStatus status, String errorMsg) {
      ImmutableList<String> violations = validator.validate(status);
      assertThat(violations.size(), is(1));
      assertThat(violations.get(0), is(errorMsg));
    }
  }

  /**
   * Tests {@link SystemStatus#toString()}.
   */
  public class ToStringTest {

    @Test
    public void testCorrectString() {
      String expectedString =
          "SystemStatus{status=ERROR, " +
          "components=[ComponentStatus{status=READY, message=MGMT plane is deployed, stats={READY=1}, " +
          "component=photon-controller, instances=[ComponentInstance{status=READY, message=null, stats=null, " +
          "address=127.0.0.1, buildInfo=null}, ComponentInstance{status=READY, message=null, stats=null, " +
          "address=127.0.0.2, buildInfo=null}, ComponentInstance{status=READY, message=null, stats=null, " +
          "address=127.0.0.3, buildInfo=null}], buildInfo=version 0.0.1}]}";
      SystemStatus systemStatus = createSystemStatus();
      assertThat(systemStatus.toString(), is(expectedString));
    }
  }

  /**
   * Tests JSON serialization.
   */
  public class SerializationTest {

    private static final String STATUS_FIXTURE_FILE = "fixtures/system-status.json";

    @Test
    public void statusSerialization() throws Exception {
      SystemStatus systemStatus = createSystemStatus();
      assertThat(asJson(systemStatus), sameJSONAs(jsonFixture(STATUS_FIXTURE_FILE)).allowingAnyArrayOrdering());
      assertThat(fromJson(jsonFixture(STATUS_FIXTURE_FILE), SystemStatus.class), is(systemStatus));
    }
  }

}
