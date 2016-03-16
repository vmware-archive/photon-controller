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

package com.vmware.photon.controller.housekeeper.dcp;

import com.vmware.photon.controller.common.config.BadConfigException;
import com.vmware.photon.controller.common.config.ConfigBuilder;
import com.vmware.photon.controller.housekeeper.Config;

import org.testng.annotations.Test;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.testng.Assert.fail;

/**
 * Tests {@link XenonConfig}.
 */
public class XenonConfigTest {

  private XenonConfig xenonConfig;

  @Test
  public void testDcpStoragePath() throws BadConfigException {
    xenonConfig = ConfigBuilder.build(Config.class,
        XenonConfigTest.class.getResource("/config.yml").getPath()).getDcp();
    assertThat(xenonConfig.getStoragePath(), is("/tmp/dcp/housekeeper/"));
  }

  @Test
  public void testInvalidBatchSize() {
    try {
      xenonConfig = ConfigBuilder.build(XenonConfig.class,
          XenonConfigTest.class.getResource("/dcpConfig_invalid.yml").getPath());
      fail();
    } catch (BadConfigException e) {
    }
  }
}
