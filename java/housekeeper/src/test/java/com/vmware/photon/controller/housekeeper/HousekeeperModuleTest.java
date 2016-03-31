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

package com.vmware.photon.controller.housekeeper;

import com.vmware.photon.controller.common.config.BadConfigException;
import com.vmware.photon.controller.housekeeper.helpers.TestHelper;

import com.google.inject.Injector;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

/**
 * Test {@link HousekeeperModule}.
 */
public class HousekeeperModuleTest {

  /**
   * Dummy test case to make Intellij recognize this as a test class.
   */
  @Test
  private void dummy() {
  }

  /**
   * Test the Guice injection functionality.
   */
  protected class InjectorTest {
    private Injector injector;

    @BeforeMethod
    public void setUp() throws BadConfigException {
      injector = TestHelper.createInjector("/config.yml");
    }

    @Test
    public void testConfig() {
      TestHelper.TestInjectedConfig test = injector.getInstance(TestHelper.TestInjectedConfig.class);
      assertThat(test.getThriftBindAddress(), is("0.0.0.0"));
      assertThat(test.getThriftPort(), is(16000));
      assertThat(test.getThriftRegistrationAddress(), is("127.0.0.1"));
      assertThat(test.getXenonBindAddress(), is("0.0.0.0"));
      assertThat(test.getXenonPort(), is(16001));
      assertThat(test.getXenonRegistrationAddress(), is("127.0.0.1"));
      assertThat(test.getXenonStoragePath(), is("/tmp/dcp/housekeeper/"));
    }

    @Test
    public void testInjectNsxClientFactory() {
      TestHelper.TestInjectedNsxClientFactory testInjectedNsxClientFactory =
          injector.getInstance(TestHelper.TestInjectedNsxClientFactory.class);
      assertThat(testInjectedNsxClientFactory.factory, notNullValue());
    }
  }
}
