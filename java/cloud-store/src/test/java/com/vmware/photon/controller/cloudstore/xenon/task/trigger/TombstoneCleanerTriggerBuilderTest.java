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

package com.vmware.photon.controller.cloudstore.xenon.task.trigger;

import com.vmware.photon.controller.cloudstore.xenon.task.TombstoneCleanerFactoryService;
import com.vmware.photon.controller.cloudstore.xenon.task.TombstoneCleanerService;
import com.vmware.photon.controller.common.xenon.scheduler.TaskTriggerService;
import com.vmware.xenon.common.Utils;

import org.hamcrest.Matchers;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

import java.lang.reflect.Type;
import java.util.concurrent.TimeUnit;

/**
 * Tests for {@link TombstoneCleanerTriggerBuilder}.
 */
public class TombstoneCleanerTriggerBuilderTest {

  TombstoneCleanerTriggerBuilder builder;

  /**
   * Dummy test case to make Intellij recognize this as a test class.
   */
  @Test(enabled = false)
  private void dummy() {
  }

  /**
   * Test the build method.
   */
  public class BuildTest {

    Long triggerInterval;
    Long taskExpirationAge;
    Long tombstoneExpirationAge;

    @BeforeMethod
    private void setUp() {
      triggerInterval = TimeUnit.MINUTES.toMillis(30);
      taskExpirationAge = triggerInterval * 5;
      tombstoneExpirationAge = TimeUnit.HOURS.toMillis(5);

      builder = new TombstoneCleanerTriggerBuilder(triggerInterval, taskExpirationAge, tombstoneExpirationAge);
    }

    /**
     * Tests the successful case.
     *
     * @throws Throwable
     */
    @Test
    public void testSuccess() throws Throwable {
      TaskTriggerService.State state = builder.build();
      assertThat(state.triggerIntervalMillis, is(triggerInterval.intValue()));
      assertThat(state.taskExpirationAgeMillis, is(taskExpirationAge.intValue()));

      assertThat(state.triggerStateClassName, is(TombstoneCleanerService.State.class.getName()));
      assertThat(state.factoryServiceLink, Matchers.is(TombstoneCleanerFactoryService.SELF_LINK));

      Type stateType = Class.forName(state.triggerStateClassName);
      TombstoneCleanerService.State triggerState = Utils.fromJson(state.serializedTriggerState, stateType);
      assertThat(triggerState.tombstoneExpirationAgeMillis, is(tombstoneExpirationAge));
    }
  }
}
