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

import com.vmware.photon.controller.common.config.BadConfigException;

import org.testng.annotations.Test;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

/**
 * Tests {@link AuthConfig}.
 */
public class AuthConfigTest {

  private AuthConfig config;

  @Test
  private void dummy() {
  }

  /**
   * Tests for enableAuth property.
   */
  public class EnableAuthTests {

    @Test
    public void testWithValidData() throws Exception {
      config = ConfigurationUtils.parseConfiguration(
          AuthConfigTest.class.getResource("/config.yml").getPath()).getAuth();
      assertThat(config.isAuthEnabled(), is(true));
    }

    @Test
    public void testWithoutConfig() throws Exception {
      config = ConfigurationUtils.parseConfiguration(
          AuthConfigTest.class.getResource("/local_image_datastore_config.yml").getPath()).getAuth();
      assertThat(config.isAuthEnabled(), is(false));
    }

    @Test
    public void testSetEnableAuth() throws BadConfigException {
      config = new AuthConfig();

      config.setEnableAuth(true);
      assertThat(config.isAuthEnabled(), is(true));

      config.setEnableAuth(false);
      assertThat(config.isAuthEnabled(), is(false));
    }
  }

  /**
   * Tests for auth_server_address property.
   */
  public class AuthServerAddressTests {

    @Test
    public void testWithValidData() throws Exception {
      config = ConfigurationUtils.parseConfiguration(
          AuthConfigTest.class.getResource("/config.yml").getPath()).getAuth();
      assertThat(config.getAuthServerAddress(), is(notNullValue()));
    }

    @Test
    public void testWithoutConfig() throws Exception {
      config = ConfigurationUtils.parseConfiguration(
          AuthConfigTest.class.getResource("/local_image_datastore_config.yml").getPath()).getAuth();
      assertThat(config.getAuthServerAddress(), nullValue());
    }

    @Test
    public void testSetServiceLocatorUrl() throws BadConfigException {
      config = new AuthConfig();

      config.setAuthServerAddress("http://testSetServiceLocatorUrl");
      assertThat(config.getAuthServerAddress(), is(equalTo("http://testSetServiceLocatorUrl")));
    }
  }
}
