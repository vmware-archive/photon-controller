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

package com.vmware.photon.controller.dhcpagent.dhcpdrivers;

import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.isEmptyOrNullString;
import static org.testng.Assert.fail;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;

/**
 * Class implements tests for Dnsmasq driver.
 */
public class DnsmasqDriverTest {

    @BeforeClass
    public void setUpClass() {
        try {
            String command = String.format("chmod +x %s",
                    DnsmasqDriverTest.class.getResource("/dhcp_release.sh").getPath());
            Runtime.getRuntime().exec(command);
        } catch (IOException e) {
            fail(String.format("Failed with IOException: %s", e.toString()));
        }
    }

    /**
     * Dummy test case to make Intellij recognize this as a test class.
     */
    @Test(enabled = false)
    public void dummy() {
    }

    @Test
    public void testReleaseIPSuccess() {
        setupDHCPConfig("none");

        DnsmasqDriver dnsmasqDriver = new DnsmasqDriver(
                DnsmasqDriverTest.class.getResource("/dhcp_release.sh").getPath());
        DHCPDriver.Response response = dnsmasqDriver.releaseIP("VMLAN", "192.0.0.1", "01:23:45:67:89:ab");

        assertThat(response.stdError, isEmptyOrNullString());
        assertThat(response.exitCode, is(0));

     }

    @Test
    public void testReleaseIPFailure() {
        setupDHCPConfig("error");

        DnsmasqDriver dnsmasqDriver = new DnsmasqDriver(
            DnsmasqDriverTest.class.getResource("/dhcp_release.sh").getPath());
        DHCPDriver.Response response = dnsmasqDriver.releaseIP("VMLAN", "192.0.0.1", "01:23:45:67:89:ab");

        assertThat(response.exitCode, is(113));
        assertThat(response.stdError, is("error"));
    }

    private void setupDHCPConfig(String inputConfig) {
        try {
            PrintWriter writer = new PrintWriter(
                    DnsmasqDriverTest.class.getResource("/dhcpServerTestConfig").getPath());
            writer.println(inputConfig);
            writer.close();
        } catch (FileNotFoundException e) {
            fail(String.format("Failed with file not found exception: %s", e.toString()));
        }
    }
}
