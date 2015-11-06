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

package com.vmware.photon.controller.common.clients;

import org.testng.annotations.Test;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

/**
 * Tests for {@link HousekeeperClientConfig}.
 */
public class HousekeeperClientConfigTest {

  @Test
  private void dummy() {
  }

  /**
   * Tests for getters and setter.
   */
  public class GetterSetterTest {
    @Test
    public void testSuccess() {
      HousekeeperClientConfig config = new HousekeeperClientConfig();
      config.setImageReplicationTimeout(10L);

      assertThat(config.getImageReplicationTimeout(), is(10L));
    }
  }
}
