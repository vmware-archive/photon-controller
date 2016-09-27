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

package com.vmware.photon.controller.dhcpagent;

import com.vmware.photon.controller.common.config.BadConfigException;
import com.vmware.photon.controller.dhcpagent.dhcpdrivers.Constants;
import com.vmware.photon.controller.dhcpagent.dhcpdrivers.DnsmasqDriver;
import com.vmware.photon.controller.dhcpagent.xenon.helpers.TestHelper;

import com.google.inject.Injector;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

/**
 * Test {@link DHCPAgentModule}.
 */
public class DHCPAgentModuleTest {
  private static final String successScript = "/scripts/success.sh";

  /**
   * Dummy test case to make Intellij recognize this as a test class.
   */
  @Test(enabled = false)
  private void dummy() {
  }

  /**
   * Test the Guice injection functionality.
   */
  protected class InjectorTest {
    private Injector injector;

    @BeforeMethod
    public void setUp() throws BadConfigException {
      injector = TestHelper.createInjector("/config.yml", new DnsmasqDriver(
              DHCPAgentModuleTest.class.getResource("/dnsmasq.leases").getPath(),
              Constants.DHCP_RELEASE_PATH,
              DHCPAgentModuleTest.class.getResource(successScript).getPath(),
              DHCPAgentModuleTest.class.getResource(successScript).getPath(),
              Constants.DNSMASQ_HOST_DIR_PATH,
              Constants.DNSMASQ_OPTION_DIR_PATH,
              Constants.DNSMASQ_PID_PATH,
              DHCPAgentModuleTest.class.getResource(successScript).getPath()));
    }

    @Test
    public void testConfig() {
      TestHelper.TestInjectedConfig test = injector.getInstance(TestHelper.TestInjectedConfig.class);
      assertThat(test.getBind(), is("0.0.0.0"));
      assertThat(test.getRegistrationAddress(), is("127.0.0.1"));
      assertThat(test.getPort(), is(17000));
      assertThat(test.getPath(), is("/tmp/xenon/dhcp-agent/"));
    }
  }
}
