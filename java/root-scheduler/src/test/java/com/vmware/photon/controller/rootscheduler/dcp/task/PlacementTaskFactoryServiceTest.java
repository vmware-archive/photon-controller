/*
 * Copyright 2016 VMware, Inc. All Rights Reserved.
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

package com.vmware.photon.controller.rootscheduler.dcp.task;


import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * Implements tests for {@link PlacementTaskFactoryService}.
 */
public class PlacementTaskFactoryServiceTest {
  private PlacementTaskFactoryService target;

  @BeforeMethod
  public void before() {
    target = new PlacementTaskFactoryService();
  }

  @Test
  public void instancesDiffer() {
    assertThat(target.createServiceInstance(), not(is(target.createServiceInstance())));
  }
}
