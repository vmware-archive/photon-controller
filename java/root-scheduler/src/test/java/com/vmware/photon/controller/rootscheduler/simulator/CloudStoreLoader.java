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

package com.vmware.photon.controller.rootscheduler.simulator;

import com.vmware.photon.controller.api.HostState;
import com.vmware.photon.controller.api.UsageTag;
import com.vmware.photon.controller.cloudstore.dcp.entity.DatastoreService;
import com.vmware.photon.controller.cloudstore.dcp.entity.DatastoreServiceFactory;
import com.vmware.photon.controller.cloudstore.dcp.entity.HostService;
import com.vmware.photon.controller.cloudstore.dcp.entity.HostServiceFactory;
import com.vmware.photon.controller.cloudstore.dcp.helpers.TestEnvironment;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Utils;

import org.apache.commons.math3.distribution.EnumeratedIntegerDistribution;
import org.apache.commons.math3.distribution.IntegerDistribution;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * A utility class for populating cloudstore documents.
 */
public class CloudStoreLoader {
  private static final Logger logger = LoggerFactory.getLogger(CloudStoreLoader.class);

  private static Random random = new Random();

  /**
   * Configuration of a host.
   */
  public static class HostConfiguration {
    public final int numCpus;
    public final int memoryMb;

    public HostConfiguration(int numCpus, int memoryMb) {
      this.numCpus = numCpus;
      this.memoryMb = memoryMb;
    }
  }

  /**
   * Creates host documents in cloudstore.
   *
   * @param cloudstore CloudStore test environment to create documents in.
   * @param numHosts The number of host documents to create.
   * @param hostConfigurations A map from {@link HostConfiguration} to the probability that this
   *                           host configuration is used in the deployment. The sum of all the
   *                           values of this map must be 1.
   * @param numDatastores The number of datastores.
   * @param numDatastoresDistribution Distribution for number of datastores on each host. This
   *                                  distribution is expected to generate samples in the range
   *                                  [0, numDatastores].
   * @throws Throwable
   */
  public static void loadHosts(TestEnvironment cloudstore,
                               int numHosts,
                               Map<HostConfiguration, Double> hostConfigurations,
                               int numDatastores,
                               IntegerDistribution numDatastoresDistribution) throws Throwable {
    int[] indices = new int[hostConfigurations.size()];
    HostConfiguration[] configs = new HostConfiguration[hostConfigurations.size()];
    double[] probabilities = new double[hostConfigurations.size()];
    int i = 0;
    for (Map.Entry<HostConfiguration, Double> entry: hostConfigurations.entrySet()) {
      indices[i] = i;
      configs[i] = entry.getKey();
      probabilities[i] = entry.getValue();
      i++;
    }
    EnumeratedIntegerDistribution configDistribution = new EnumeratedIntegerDistribution(indices, probabilities);
    for (i = 0; i < numHosts; i++) {
      HostService.State host = new HostService.State();
      host.hostAddress = "host" + i;
      host.state = HostState.READY;
      host.userName = "username";
      host.password = "password";
      host.reportedDatastores = new HashSet<>();
      int numDatastoresPerHost = numDatastoresDistribution.sample();
      assertThat(numDatastoresPerHost >= 0, is(true));
      assertThat(numDatastoresPerHost <= numDatastores, is(true));
      while (host.reportedDatastores.size() < numDatastoresPerHost) {
        int randomInt = random.nextInt(numDatastores);
        host.reportedDatastores.add(new UUID(0, randomInt).toString());
      }
      host.reportedNetworks = new HashSet<>();
      host.usageTags = new HashSet<>(Arrays.asList(UsageTag.CLOUD.name()));
      int configIndex = configDistribution.sample();
      host.cpuCount = configs[configIndex].numCpus;
      host.memoryMb = configs[configIndex].memoryMb;
      host.documentSelfLink = new UUID(0, i).toString();
      // TODO(mmutsuzaki) Support availability zones.
      Operation result = cloudstore.sendPostAndWait(HostServiceFactory.SELF_LINK, host);
      assertThat(result.getStatusCode(), is(200));
      logger.debug("Created a host document: {}", Utils.toJson(true, false, host));
    }
  }

  /**
   * Creates datastore documents in cloudstore.
   *
   * This method creates datastore documents with datastore IDs 00000000-0000-0000-0000-000000000000,
   * 00000000-0000-0000-0000-000000000001, and so on.
   *
   * @param numDatastores The number of datastore documents to create.
   */
  public static void loadDatastores(TestEnvironment cloudstore, int numDatastores) throws Throwable {
    for (int i = 0; i < numDatastores; i++) {
      DatastoreService.State datastore = new DatastoreService.State();
      String datastoreId = new UUID(0, i).toString();
      datastore.id = datastoreId;
      datastore.name = datastoreId;
      datastore.documentSelfLink = datastoreId;
      datastore.type = "SHARED_VMFS";
      // TODO(mmutsuzaki) Support datastore tags.
      datastore.tags = new HashSet<>();
      Operation result = cloudstore.sendPostAndWait(DatastoreServiceFactory.SELF_LINK, datastore);
      assertThat(result.getStatusCode(), is(200));
      logger.debug("Created a datastore document: {}", Utils.toJson(false, false, datastore));
    }
  }
}
