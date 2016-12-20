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

package com.vmware.photon.controller.api.model;

import com.vmware.photon.controller.api.model.helpers.JsonHelpers;

import org.hamcrest.MatcherAssert;
import org.testng.annotations.Test;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static uk.co.datumedge.hamcrest.json.SameJSONAs.sameJSONAs;

/**
 * Tests {@link NetworkConnection}.
 */
public class NetworkConnectionTest {

  private NetworkConnection createNetworkConnection() {
    NetworkConnection networkConnection = new NetworkConnection();
    networkConnection.setIpAddress("127.0.0.1");
    networkConnection.setIsConnected(NetworkConnection.Connected.True);
    networkConnection.setMacAddress("00:50:56:02:00:2f");
    networkConnection.setNetmask("255.255.255.0");
    networkConnection.setNetwork("PG1");
    return networkConnection;
  }

  /**
   * Dummy test case to make Intellij recognize this as a test class.
   */
  @Test(enabled = false)
  private void dummy() {
  }

  @Test
  public void testEquals() throws Exception {
    NetworkConnection ip1 = new NetworkConnection("00:50:56:02:00:2f");
    NetworkConnection ip2 = new NetworkConnection("00:50:56:02:00:4f");
    NetworkConnection ip3 = new NetworkConnection("00:50:56:02:00:2f");

    assertThat(ip1.equals(ip2), is(false));
    assertThat(ip1.equals(ip3), is(true));
  }

  /**
   * Tests {@link SystemStatus#toString()}.
   */
  public class ToStringTest {

    @Test
    public void testCorrectString() {
      String expectedString = "NetworkConnection{macAddress=00:50:56:02:00:2f, ipAddress=127.0.0.1," +
          " isConnected=True, netmask=255.255.255.0, network=PG1}";
      NetworkConnection networkConnection = createNetworkConnection();
      assertThat(networkConnection.toString(), is(expectedString));
    }
  }

  /**
   * Tests JSON serialization.
   */
  public class SerializationTest {

    private static final String JSON_FILE = "fixtures/network-connection.json";

    @Test
    public void testSerialization() throws Exception {
      NetworkConnection networkConnection = new NetworkConnection("00:50:56:02:00:30");
      networkConnection.setNetwork("public");
      networkConnection.setIpAddress("10.146.30.120");
      networkConnection.setIsConnected(NetworkConnection.Connected.True);
      networkConnection.setNetmask("255.255.255.128");

      String json = JsonHelpers.jsonFixture(JSON_FILE);

      assertThat(JsonHelpers.fromJson(json, NetworkConnection.class), is(networkConnection));
      MatcherAssert.assertThat(JsonHelpers.asJson(networkConnection), sameJSONAs(json).allowingAnyArrayOrdering());
    }

  }
}
