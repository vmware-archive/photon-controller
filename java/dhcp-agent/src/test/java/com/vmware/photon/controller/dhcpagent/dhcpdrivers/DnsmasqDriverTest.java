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
import static org.hamcrest.Matchers.not;
import static org.testng.Assert.fail;

import java.io.IOException;

/**
 * Class implements tests for Dnsmasq driver.
 */
public class DnsmasqDriverTest {

    private DnsmasqDriver dnsmasqDriver;
    private static final String successScript = "/scripts/success.sh";
    private static final String failureScript = "/scripts/failure.sh";

    @BeforeClass
    public void setUpClass() {
        try {
            String command = String.format("chmod +x %s",
                    DnsmasqDriverTest.class.getResource(successScript).getPath());
            Runtime.getRuntime().exec(command);
            command = String.format("chmod +x %s",
                    DnsmasqDriverTest.class.getResource(failureScript).getPath());
            Runtime.getRuntime().exec(command);
        } catch (IOException e) {
            fail(String.format("Failed with IOException: %s", e.toString()));
        }
    }

    public void setUpDriver(String scriptPath, String leaseFilePath) {
        dnsmasqDriver = new DnsmasqDriver(
                leaseFilePath,
                "/usr/local/bin/dhcp_release",
                DnsmasqDriverTest.class.getResource(scriptPath).getPath(),
                DnsmasqDriverTest.class.getResource(scriptPath).getPath());
    }

    /**
     * Dummy test case to make Intellij recognize this as a test class.
     */
    @Test(enabled = false)
    public void dummy() {
    }

    @Test
    public void testReleaseIPSuccess() {
        setUpDriver(successScript, DnsmasqDriverTest.class.getResource("/dnsmasq.leases").getPath());

        DHCPDriver.Response response = dnsmasqDriver.releaseIP("VMLAN", "01:23:45:67:89:ab");

        assertThat(response.stdError, isEmptyOrNullString());
        assertThat(response.exitCode, is(0));

     }

    @Test
    public void testReleaseIPFailure() {
        setUpDriver(failureScript, DnsmasqDriverTest.class.getResource("/dnsmasq.leases").getPath());

        DHCPDriver.Response response = dnsmasqDriver.releaseIP("VMLAN", "01:23:45:67:89:ab");

        assertThat(response.exitCode, is(113));
        assertThat(response.stdError, is("error"));
    }

    @Test
    public void testDHCPStatusSuccess() {
        setUpDriver(successScript, DnsmasqDriverTest.class.getResource("/dnsmasq.leases").getPath());

        boolean status = dnsmasqDriver.isRunning();
        assertThat(status, is(true));
    }

    @Test
    public void testDHCPStatusFailure() {
        setUpDriver(failureScript, DnsmasqDriverTest.class.getResource("/dnsmasq.leases").getPath());

        boolean status = dnsmasqDriver.isRunning();
        assertThat(status, is(false));
    }

    @Test
    public void testFindIPSuccess() {
        try {
            setUpDriver(successScript, DnsmasqDriverTest.class.getResource("/dnsmasq.leases").getPath());
            String ipAddress = dnsmasqDriver.findIP("08:00:27:d8:7d:8e");
            assertThat(ipAddress, not(isEmptyOrNullString()));
            assertThat(ipAddress, is("192.168.0.2"));
        } catch (Throwable e) {
            fail(String.format("Failed with exception: %s", e.toString()));
        }
    }

    @Test
    public void testFindIPFailureWithIPNotFound() {
        try {
            setUpDriver(successScript, DnsmasqDriverTest.class.getResource("/dnsmasq.leases").getPath());
            String ipAddress = dnsmasqDriver.findIP("08:00:27:d8:7d:8d");
            assertThat(ipAddress, isEmptyOrNullString());
        } catch (Throwable e) {
            fail(String.format("Failed with exception: %s", e.toString()));
        }
    }

    @Test
    public void testFindIPFailureWithLeaseFileNotFound() {
        try {
            setUpDriver(successScript, "/var/lib/misc/dnsmasq.leases");

            dnsmasqDriver.findIP("08:00:27:d8:7d:8e");
            fail("Failed to get file not found exception.");
        } catch (Throwable e) {
        }
    }
}
