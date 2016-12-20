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

import com.vmware.photon.controller.api.model.base.Model;
import com.vmware.photon.controller.api.model.helpers.JsonHelpers;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import org.hamcrest.MatcherAssert;
import org.testng.annotations.Test;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.core.Is.isA;
import static uk.co.datumedge.hamcrest.json.SameJSONAs.sameJSONAs;

import java.io.IOException;
import java.util.Date;
import java.util.List;

/**
 * Tests {@link Task}.
 */
public class TaskTest {

  @Test
  public void testIsAModel() throws Exception {
    MatcherAssert.assertThat(new Task(), isA(Model.class));
  }

  /**
   * Tests task serialization.
   */
  public class TaskSerializationTest {

    @Test
    public void serialize() throws Exception {
      Task task = new Task();
      task.setId("id");
      task.setSelfLink("http://localhost:9080/v1/tasks/task-id");
      task.setQueuedTime(new Date(10));
      task.setStartedTime(new Date(11));
      task.setEndTime(new Date(12));
      task.setOperation(Operation.ATTACH_DISK.getOperation());
      task.setState("ERROR");

      Task.Entity entity = new Task.Entity();
      entity.setId("entity-id");
      entity.setKind(Vm.KIND);
      task.setEntity(entity);
      task.setSteps(createSteps());
      task.setResourceProperties("resource-property");

      String actualJson = JsonHelpers.asJson(task);
      String expectedJson = JsonHelpers.jsonFixture("fixtures/tasks.json");
      assertThat(actualJson, is(sameJSONAs(expectedJson)));
    }

    @Test
    public void deserialize() throws IOException {
      Task parsedTask = JsonHelpers.fromJson(JsonHelpers.jsonFixture("fixtures/tasks.json"), Task.class);
      assertThat(parsedTask.getId(), equalTo("id"));
      assertThat(parsedTask.getSelfLink(), equalTo("http://localhost:9080/v1/tasks/task-id"));
      assertThat(parsedTask.getQueuedTime(), equalTo(new Date(10)));
      assertThat(parsedTask.getStartedTime(), equalTo(new Date(11)));
      assertThat(parsedTask.getEndTime(), equalTo(new Date(12)));
      assertThat(parsedTask.getEntity().getId(), equalTo("entity-id"));
      assertThat(parsedTask.getEntity().getKind(), equalTo("vm"));
      assertThat(parsedTask.getState(), equalTo("ERROR"));
      assertThat(parsedTask.getSteps(), equalTo(createSteps()));
      assertThat((String) parsedTask.getResourceProperties(), equalTo("resource-property"));
    }

    private List<Step> createSteps() {
      Step step1 = new Step();
      step1.setSequence(0);
      step1.setQueuedTime(new Date(10));
      step1.setStartedTime(new Date(11));
      step1.setEndTime(new Date(12));
      step1.addError(new ApiError("ErrorCode", "Some message", ImmutableMap.of("foo", "bar")));
      step1.setOperation(Operation.DELETE_VM.getOperation());
      step1.setState("ERROR");
      step1.setOptions(ImmutableMap.of("key", "value"));

      Step step2 = new Step();
      step2.setSequence(1);
      step2.setQueuedTime(new Date(10));
      step2.setStartedTime(new Date(11));
      step2.setEndTime(new Date(12));
      step2.setOperation(Operation.CREATE_VM.getOperation());
      step2.setState("QUEUED");

      return ImmutableList.of(step1, step2);
    }
  }
}
