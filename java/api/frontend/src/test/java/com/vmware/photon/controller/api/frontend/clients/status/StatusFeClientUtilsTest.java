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

package com.vmware.photon.controller.api.frontend.clients.status;

import com.vmware.photon.controller.api.model.Component;
import com.vmware.photon.controller.api.model.ComponentStatus;
import com.vmware.photon.controller.api.model.builders.ComponentInstanceBuilder;
import com.vmware.photon.controller.api.model.builders.ComponentStatusBuilder;
import com.vmware.photon.controller.status.gen.StatusType;

import com.google.common.collect.ImmutableList;
import org.testng.annotations.Test;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;


/**
 * Tests {@link StatusFeClientUtils}.
 */
public class StatusFeClientUtilsTest {

  @Test
  public void testComputeComponentStatusWithNoError() {
    ComponentStatus componentStatus = new ComponentStatusBuilder()
        .component(Component.PHOTON_CONTROLLER)
        .instances(ImmutableList.of(
            new ComponentInstanceBuilder().status(StatusType.READY).build(),
            new ComponentInstanceBuilder().status(StatusType.INITIALIZING).build()
        ))
        .build();

    StatusFeClientUtils.computeSingleComponentStatus(componentStatus);
    assertThat(componentStatus.getStatus(), is(StatusType.INITIALIZING));
    assertThat(componentStatus.getStats().get(StatusType.READY.toString()), is("1"));
    assertThat(componentStatus.getStats().get(StatusType.INITIALIZING.toString()), is("1"));
  }

  @Test
  public void testComputeComponentStatusWithPartialError() {
    ComponentStatus componentStatus = new ComponentStatusBuilder()
        .component(Component.PHOTON_CONTROLLER)
        .instances(ImmutableList.of(
            new ComponentInstanceBuilder().status(StatusType.READY).build(),
            new ComponentInstanceBuilder().status(StatusType.ERROR).build()
        ))
        .build();

    StatusFeClientUtils.computeSingleComponentStatus(componentStatus);
    assertThat(componentStatus.getStatus(), is(StatusType.PARTIAL_ERROR));
    assertThat(componentStatus.getStats().get(StatusType.READY.toString()), is("1"));
    assertThat(componentStatus.getStats().get(StatusType.ERROR.toString()), is("1"));
  }

  @Test
  public void testComputeComponentStatusWithError() {
    ComponentStatus componentStatus = new ComponentStatusBuilder()
        .component(Component.PHOTON_CONTROLLER)
        .instances(ImmutableList.of(
            new ComponentInstanceBuilder().status(StatusType.ERROR).build(),
            new ComponentInstanceBuilder().status(StatusType.ERROR).build()
        ))
        .build();

    StatusFeClientUtils.computeSingleComponentStatus(componentStatus);
    assertThat(componentStatus.getStatus(), is(StatusType.ERROR));
    assertThat(componentStatus.getStats().get(StatusType.ERROR.toString()), is("2"));
  }

}
