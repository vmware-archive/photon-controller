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

package com.vmware.photon.controller.common.dcp.scheduler;

import com.vmware.photon.controller.common.dcp.helpers.services.TestServiceWithStage;

import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;

/**
 * Tests {@link TaskSchedulerServiceStateBuilder}.
 */
public class TaskSchedulerServiceStateBuilderTest {

  private String testServiceLink = TaskSchedulerServiceFactory.SELF_LINK + "/test-service";

  private TaskSchedulerServiceStateBuilder builder;

  @Test
  public void testGetSuffixFromSelfLink() {
    assertThat(TaskSchedulerServiceStateBuilder.getSuffixFromSelfLink(testServiceLink), is("/test-service"));
  }

  @Test
  public void testGetStartPatch() throws Throwable {
    assertThat(TaskSchedulerServiceStateBuilder.getStartPatch(TestServiceWithStage.class),
        instanceOf(TestServiceWithStage.State.class));
  }

  /**
   * Test the build method.
   */
  public class BuildTest {

    TaskSchedulerServiceStateBuilder.Config config;

    @BeforeMethod
    private void setUp() {
      builder = new TaskSchedulerServiceStateBuilder();
      config = new TaskSchedulerServiceStateBuilder.Config(TestServiceWithStage.class, 10);
    }

    /**
     * Tests the successful case.
     *
     * @throws Throwable
     */
    @Test
    public void testSuccess() throws Throwable {
      TaskSchedulerService.State state = builder.build(config);
      assertThat(state.schedulerServiceClassName, is(TestServiceWithStage.class.getTypeName()));
      assertThat(state.tasksLimits, is(10));
    }

    /**
     * Tests that default null config will throw exception.
     *
     * @throws Throwable
     */
    @Test
    public void testConfigNotProvided() throws Throwable {
      try {
        builder.build(null);
      } catch (NullPointerException e) {
        assertThat(e.getMessage(), is("config should not be null"));
      }
    }

    /**
     * Tests that empty config will throw exception.
     *
     * @throws Throwable
     */
    @Test
    public void testWrongConfigTypeProvided() throws Throwable {
      TaskStateBuilderConfig config = new TaskStateBuilderConfig() {
      };

      try {
        builder.build(config);
      } catch (IllegalArgumentException e) {
        assertThat(e.getMessage(), is("config should be an instance of TaskSchedulerServiceStateBuilder.Config"));
      }
    }
  }
}
