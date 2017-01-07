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

package com.vmware.photon.controller.api.backend.helpers;

import com.vmware.xenon.common.TaskState;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

import java.util.List;

/**
 * This class implements helper routines for tests.
 */
public class TestHelper {

  public static Object[][] toDataProvidersList(List<?> list) {
    Object[][] objects = new Object[list.size()][1];
    for (int i = 0; i < list.size(); ++i) {
      objects[i][0] = list.get(i);
    }
    return objects;
  }

  public static void assertTaskStateFinished(TaskState taskState) {
    assertThat(
        String.format("Unexpected task stage result %s (message: %s, stack trace: %s)",
            taskState.stage,
            null != taskState.failure ? taskState.failure.message : "null",
            null != taskState.failure ? taskState.failure.stackTrace : "null"),
        taskState.stage,
        is(TaskState.TaskStage.FINISHED));
  }
}
