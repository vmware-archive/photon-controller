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

package com.vmware.photon.controller.provisioning.helpers;

import org.testng.annotations.AfterGroups;
import org.testng.annotations.BeforeGroups;

/**
 * Abstract base class that creates a common
 * reusable DCP ServiceHost for unit-tests.
 */
public abstract class ReusableHost {

  public static final int HOST_COUNT = 1;
  public static final String TAG = "REUSE_HOST";

  protected TestEnvironment machine;

  @BeforeGroups(groups = {ReusableHost.TAG})
  public void setUpClass() throws Throwable {
    machine = new TestEnvironment(HOST_COUNT);
    machine.start();
  }

  @AfterGroups(groups = {ReusableHost.TAG})
  public void tearDownClass() throws Throwable {
    if (machine != null) {
      machine.stop();
      machine = null;
    }
  }
}
