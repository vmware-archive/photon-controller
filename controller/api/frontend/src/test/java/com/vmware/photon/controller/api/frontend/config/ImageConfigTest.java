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

package com.vmware.photon.controller.api.frontend.config;

import org.testng.annotations.Test;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.testng.Assert.fail;

/**
 * Tests {@link ImageConfig}.
 */
public class ImageConfigTest {

  private ImageConfig config;

  @Test
  private void dummy() {
  }

  /**
   * Tests for the imageReplicationTimeout property.
   */
  public class ImageReplicationTimeout {

    @Test
    public void testDefaultImageReplicationTimeout() throws Exception {
      config = ConfigurationUtils.parseConfiguration(
          ImageConfigTest.class.getResource("/config.yml").getPath()).getImage();
      assertThat(config.getReplicationTimeout().toSeconds(), is(3600L));
    }

    @Test
    public void testExplicitImageReplicationTimeout() throws Exception {
      config = ConfigurationUtils.parseConfiguration(
          ImageConfigTest.class.getResource("/config_valid_image_replication_timeout.yml").getPath()).getImage();
      assertThat(config.getReplicationTimeout().toSeconds(), is(600L));
    }

    @Test
    public void testInvalidImageReplicationTimeout() {
      try {
        ConfigurationUtils.parseConfiguration(
            ImageConfigTest.class.getResource("/config_invalid_image_replication_timeout.yml").getPath());
        fail("invalid config did not fail");
      } catch (Exception e) {
        assertThat(e.getMessage(), containsString("replicationTimeout"));
      }
    }
  }
}
