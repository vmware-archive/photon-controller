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

package com.vmware.photon.controller.api.frontend.entities;

import com.vmware.photon.controller.api.model.ApiError;

import com.google.common.collect.ImmutableMap;
import org.testng.annotations.Test;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;

import java.util.Map;

/**
 * Tests {@link StepErrorEntity}.
 */
public class StepErrorEntityTest {
  @Test
  public void testMapToApiError() throws Exception {
    Map<String, String> data = ImmutableMap.of("foo", "bar", "baz", "zaz");

    StepErrorEntity stepErrorEntity = new StepErrorEntity();
    stepErrorEntity.setCode("foo");
    stepErrorEntity.setData(data);
    stepErrorEntity.setStep(new StepEntity());
    stepErrorEntity.setMessage("something happened");

    ApiError apiError = stepErrorEntity.toApiError();

    assertThat(apiError.getCode(), is("foo"));
    assertThat(apiError.getData(), is(data));
    assertThat(apiError.getMessage(), is("something happened"));
  }
}
