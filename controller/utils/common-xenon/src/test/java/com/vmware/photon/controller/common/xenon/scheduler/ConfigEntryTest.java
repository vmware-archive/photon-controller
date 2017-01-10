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

package com.vmware.photon.controller.common.xenon.scheduler;


import com.vmware.photon.controller.common.xenon.helpers.services.TestServiceWithStage;

import org.hamcrest.Matchers;
import org.testng.annotations.Test;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * Tests {@link ConfigEntry}.
 */
public class ConfigEntryTest {

  private ConfigEntry configEntry = new ConfigEntry();

  @Test
  public void testSetConfigEntry() {
    configEntry.setService(TestServiceWithStage.class);
    configEntry.setTasksLimit(10);
    assertThat(configEntry.getService().getName(),
        Matchers.is("com.vmware.photon.controller.common.xenon.helpers.services.TestServiceWithStage"));
    assertThat(configEntry.getTasksLimit(), is(10));
  }
}
