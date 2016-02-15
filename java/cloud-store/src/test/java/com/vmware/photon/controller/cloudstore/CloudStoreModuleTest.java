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

package com.vmware.photon.controller.cloudstore;

import com.vmware.photon.controller.cloudstore.xenon.helpers.TestHelper;
import com.vmware.photon.controller.common.CloudStoreServerSet;
import com.vmware.photon.controller.common.config.BadConfigException;
import com.vmware.photon.controller.common.thrift.ServerSet;
import com.vmware.photon.controller.common.zookeeper.ZookeeperServerSet;

import com.google.inject.Injector;
import com.google.inject.Key;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

/**
 * Test {@link CloudStoreModule}.
 */
public class CloudStoreModuleTest {

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
      assertThat(test.getBind(), is("localhost"));
      assertThat(test.getRegistrationAddress(), is("localhost"));
      assertThat(test.getPort(), is(19000));
      assertThat(test.getPath(), is("/tmp/dcp/cloud-store/"));
    }

    @Test
    public void testCloudStoreServerSet() {
      ServerSet serverSet = injector.getInstance(Key.get(ServerSet.class, CloudStoreServerSet.class));
      assertThat(serverSet instanceof ZookeeperServerSet, is(true));
    }
  }
}
