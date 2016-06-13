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

package com.vmware.photon.controller.deployer.xenon;

import com.vmware.photon.controller.host.gen.HostConfig;
import com.vmware.photon.controller.resource.gen.Datastore;
import com.vmware.photon.controller.resource.gen.DatastoreType;
import com.vmware.photon.controller.resource.gen.Network;

import java.util.ArrayList;
import java.util.List;

/**
 * This class implements helper routines for creating testing host configs.
 */
public class HostConfigTestUtils {

  public static String getDatastoreFieldValue(String fieldName, int number) {
    return String.format("datastore_%s_%d", fieldName, number);
  }

  public static Datastore createDatastore(int number) {
    Datastore datastore = new Datastore();
    datastore.setName(getDatastoreFieldValue("name", number));
    datastore.setId(getDatastoreFieldValue("id", number));
    datastore.setType(DatastoreType.EXT3);
    return datastore;
  }

  public static String getNetworkFieldValue(String fieldName, int number) {
    return String.format("network_%s_%d", fieldName, number);
  }

  public static Network createNetwork(int number) {
    Network network = new Network();
    network.setId(getNetworkFieldValue("id", number));
    return network;
  }

  public static HostConfig createHostConfig(
      int numberOfDatastores,
      int numberOfNetworks) {
    HostConfig hostConfig = new HostConfig();
    List<Datastore> datastores = new ArrayList<>();
    List<Network> networks = new ArrayList<>();

    for (int i = 0; i < numberOfDatastores; ++i) {
      datastores.add(createDatastore(i));
    }

    for (int i = 0; i < numberOfNetworks; ++i) {
      networks.add(createNetwork(i));
    }

    hostConfig.setDatastores(datastores);
    hostConfig.setNetworks(networks);

    return hostConfig;
  }
}
