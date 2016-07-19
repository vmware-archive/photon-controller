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

import com.vmware.photon.controller.api.model.helpers.JsonHelpers;

import com.google.common.collect.ImmutableMap;
import org.hamcrest.MatcherAssert;
import org.testng.annotations.Test;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static uk.co.datumedge.hamcrest.json.SameJSONAs.sameJSONAs;

import java.io.IOException;
import java.util.Date;
import java.util.Map;

/**
 * Tests step serialization.
 */
public class StepSerializationTest {

  @Test
  public void serialize() throws Exception {
    Step step = new Step();
    step.setSequence(1);
    step.setQueuedTime(new Date(10));
    step.setStartedTime(new Date(11));
    step.setEndTime(new Date(12));
    step.addError(new ApiError("ErrorCode", "Some message", ImmutableMap.of("foo", "bar")));
    step.addWarning(new ApiError("ErrorCode", "Some message", ImmutableMap.of("foo", "bar")));
    step.setOperation(Operation.DELETE_VM.getOperation());
    step.setState("ERROR");
    step.setOptions(ImmutableMap.of("key", "value"));

    MatcherAssert.assertThat(JsonHelpers.asJson(step), is(sameJSONAs(JsonHelpers.jsonFixture("fixtures/steps.json"))));
  }

  @Test
  public void deserialize() throws IOException {
    Step parsedStep = JsonHelpers.fromJson(JsonHelpers.jsonFixture("fixtures/steps.json"), Step.class);
    assertThat(parsedStep.getSequence(), equalTo(1));
    assertThat(parsedStep.getQueuedTime(), equalTo(new Date(10)));
    assertThat(parsedStep.getStartedTime(), equalTo(new Date(11)));
    assertThat(parsedStep.getEndTime(), equalTo(new Date(12)));
    assertThat(parsedStep.getState(), equalTo("ERROR"));
    assertThat(parsedStep.getOptions(), equalTo((Map<String, String>) ImmutableMap.of("key", "value")));
    assertThat(parsedStep.getWarnings().get(0).getCode(), equalTo("ErrorCode"));
  }
}
