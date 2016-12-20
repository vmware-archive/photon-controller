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

package com.vmware.photon.controller.deployer.xenon;

import com.vmware.photon.controller.api.model.ApiError;
import com.vmware.photon.controller.api.model.Step;
import com.vmware.photon.controller.api.model.Task;

import java.util.ArrayList;
import java.util.List;

/**
 * This class implements helper routines for testing Photon Controller REST API calls.
 */
public class ApiTestUtils {

  public static Task createFailingTask(int stepCount, int errorCount, String errorCode, String errorMessage) {
    List<Step> taskSteps = new ArrayList<>();
    for (int i = 0; i < stepCount; i++) {
      Step step = new Step();
      step.setOperation("Step operation " + String.valueOf(i));
      step.setState("COMPLETED");
      taskSteps.add(step);
    }

    Step failingStep = new Step();
    for (int i = 0; i < errorCount; i++) {
      ApiError apiError = new ApiError();
      apiError.setCode(errorCode + String.valueOf(i));
      apiError.setMessage(errorMessage + String.valueOf(i));
      failingStep.addError(apiError);
    }

    failingStep.setOperation("Failing step operation");
    failingStep.setState("ERROR");
    taskSteps.add(failingStep);
    Task task = new Task();
    task.setOperation("Task operation");
    task.setState("ERROR");
    task.setSteps(taskSteps);
    return task;
  }

  public static Task createUnknownTask() {
    Task task = new Task();
    task.setState("unknown");

    return task;
  }
}
