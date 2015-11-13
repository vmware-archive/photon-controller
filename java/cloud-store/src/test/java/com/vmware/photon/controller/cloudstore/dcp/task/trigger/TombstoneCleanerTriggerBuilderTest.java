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

package com.vmware.photon.controller.cloudstore.dcp.task.trigger;

import com.vmware.dcp.common.Utils;
import com.vmware.photon.controller.cloudstore.dcp.task.TombstoneCleanerFactoryService;
import com.vmware.photon.controller.cloudstore.dcp.task.TombstoneCleanerService;
import com.vmware.photon.controller.common.dcp.scheduler.TaskStateBuilderConfig;
import com.vmware.photon.controller.common.dcp.scheduler.TaskTriggerService;

import org.hamcrest.Matchers;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

import java.lang.reflect.Type;

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

    TombstoneCleanerTriggerBuilder.Config config;

    int taskExpirationAge;

    @BeforeMethod
    private void setUp() {
      builder = new TombstoneCleanerTriggerBuilder();
      taskExpirationAge = 1 * 60 * 60 * 1000 * 5;
      config = new TombstoneCleanerTriggerBuilder.Config(5 * 60 * 60 * 1000L, taskExpirationAge);
    }

    /**
     * Tests the successful case.
     *
     * @throws Throwable
     */
    @Test
    public void testSuccess() throws Throwable {
      TaskTriggerService.State state = builder.build(config);
      assertThat(state.taskExpirationAge, is(taskExpirationAge));

      assertThat(state.triggerStateClassName, is(TombstoneCleanerService.State.class.getName()));
      assertThat(state.factoryServiceLink, Matchers.is(TombstoneCleanerFactoryService.SELF_LINK));

      Type stateType = Class.forName(state.triggerStateClassName);
      TombstoneCleanerService.State triggerState = Utils.fromJson(state.serializedTriggerState, stateType);
      assertThat(triggerState.tombstoneExpirationAge, is(config.getTombstoneExpirationAge()));
    }

    /**
     * Tests that default tombstone expiration value is used when no config is provided.
     *
     * @throws Throwable
     */
    @Test
    public void testConfigNotProvided() throws Throwable {
      TaskTriggerService.State state = builder.build(null);

      Type stateType = Class.forName(state.triggerStateClassName);
      TombstoneCleanerService.State triggerState = Utils.fromJson(state.serializedTriggerState, stateType);
      assertThat(triggerState.tombstoneExpirationAge,
          is(TombstoneCleanerTriggerBuilder.DEFAULT_TOMBSTONE_EXPIRATION_AGE_MILLIS));
    }

    /**
     * Tests that default tombstone expiration value is used when no config is provided.
     *
     * @throws Throwable
     */
    @Test
    public void testWrongConfigTypeProvided() throws Throwable {
      TaskStateBuilderConfig config = new TaskStateBuilderConfig() {
      };

      TaskTriggerService.State state = builder.build(config);

      Type stateType = Class.forName(state.triggerStateClassName);
      TombstoneCleanerService.State triggerState = Utils.fromJson(state.serializedTriggerState, stateType);
      assertThat(triggerState.tombstoneExpirationAge,
          is(TombstoneCleanerTriggerBuilder.DEFAULT_TOMBSTONE_EXPIRATION_AGE_MILLIS));
    }
  }
}
