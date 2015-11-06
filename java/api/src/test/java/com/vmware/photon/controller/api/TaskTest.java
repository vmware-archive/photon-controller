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

package com.vmware.photon.controller.api;

import com.vmware.photon.controller.api.base.Model;

import org.hamcrest.MatcherAssert;
import org.testng.annotations.Test;
import static org.hamcrest.core.Is.isA;

/**
 * Tests {@link Task}.
 */
public class TaskTest {

  @Test
  public void testIsAModel() throws Exception {
    MatcherAssert.assertThat(new Task(), isA(Model.class));
  }
}
