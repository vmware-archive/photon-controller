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

package com.vmware.photon.controller.apife.config;

import io.dropwizard.configuration.ConfigurationValidationException;
import org.testng.annotations.Test;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;


/**
 * Tests {@link com.vmware.photon.controller.apife.config.PaginationConfig}.
 */
public class PaginationConfigTest {

  @Test
  public void testWithValidData() throws Exception {
    PaginationConfig config = ConfigurationUtils.parseConfiguration(
        PaginationConfigTest.class.getResource("/config.yml").getPath()).getPaginationConfig();
    assertThat(config.getDefaultPageSize(), is(10));
    assertThat(config.getMaxPageSize(), is(100));
  }

  @Test
  public void testWithoutConfig() throws Exception {
    PaginationConfig config = ConfigurationUtils.parseConfiguration(
        PaginationConfigTest.class.getResource("/local_image_datastore_config.yml").getPath()).getPaginationConfig();
    assertThat(config.getDefaultPageSize(), is(PaginationConfig.DEFAULT_DEFAULT_PAGE_SIZE));
    assertThat(config.getMaxPageSize(), is(PaginationConfig.DEFAULT_MAX_PAGE_SIZE));
  }

  @Test
  public void testWithInvalidData() throws Exception {
    try {
      PaginationConfig config = ConfigurationUtils.parseConfiguration(
          PaginationConfigTest.class.getResource("/bad_config.yml").getPath()).getPaginationConfig();
    } catch (ConfigurationValidationException e) {
      assertThat(e.getErrors().contains("paginationConfig.defaultPageSize must be greater than or equal to 1 (was " +
          "0)"), is(true));
      assertThat(e.getErrors().contains("paginationConfig.valid maxPageSize should be equal or larger than " +
          "defaultPageSize (was false)"), is(true));
    }
  }
}
