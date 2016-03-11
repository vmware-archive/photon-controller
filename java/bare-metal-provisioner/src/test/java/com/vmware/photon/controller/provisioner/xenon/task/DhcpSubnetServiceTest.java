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

package com.vmware.photon.controller.provisioner.xenon.task;

import com.vmware.photon.controller.common.xenon.BasicServiceHost;
import com.vmware.photon.controller.provisioner.xenon.entity.DhcpConfigurationService;
import com.vmware.photon.controller.provisioner.xenon.entity.DhcpConfigurationServiceFactory;
import com.vmware.photon.controller.provisioner.xenon.entity.DhcpSubnetService;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.UriUtils;

import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

/**
 * This class implements tests for the {@link DhcpSubnetService} class.
 */
public class DhcpSubnetServiceTest {
  private BasicServiceHost testHost;

  private String subnetAddr = "15.147.36.0/23";
  private String xenonHostAddr = "10.146.37.95";
  private int xenonPort = 21000;

  @BeforeMethod
  public void setUpTest() throws Throwable {
    testHost = BasicServiceHost.create(BasicServiceHost.BIND_ADDRESS, BasicServiceHost.BIND_PORT,
        null, BasicServiceHost.SERVICE_URI, 10, 10);
  }

  @AfterMethod
  public void tearDownTest() throws Throwable {
    if (testHost != null) {
      BasicServiceHost.destroy(testHost);
    }
  }

  /**
   * Dummy test case to make Intellij recognize this as a test class.
   */
  @Test
  private void dummy() {
  }

  private void createSubnet() throws Throwable {
    try {
      Operation configDelete = Operation
          .createDelete(UriUtils.buildUri(xenonHostAddr, xenonPort,
              "/photon/bmp/dhcp-subnets/1" , null));
      testHost.sendRequestAndWait(configDelete);
    } catch (Throwable t) {

    }

    DhcpSubnetService.DhcpSubnetState startState = new DhcpSubnetService.DhcpSubnetState();
    startState.id = "1";
    startState.subnetAddress = subnetAddr;
    startState.ranges = new DhcpSubnetService.DhcpSubnetState.Range[1];
    DhcpSubnetService.DhcpSubnetState.Range r = new DhcpSubnetService.DhcpSubnetState.Range();
    r.low = "15.147.37.100";
    r.high = "15.147.37.220";
    startState.ranges[0] = r;

    Operation post = Operation
        .createPost(UriUtils.buildUri(xenonHostAddr, xenonPort, "/photon/bmp/dhcp-subnets", null))
        .addPragmaDirective(Operation.PRAGMA_DIRECTIVE_FORCE_INDEX_UPDATE);
    post.setBody(startState);
    testHost.sendRequestAndWait(post);
  }

  private void createHostConfig() throws Throwable {
    String subnetString = "/" + subnetAddr;
    try {
      Operation configDelete = Operation
          .createDelete(UriUtils.buildUri(xenonHostAddr, xenonPort,
              "/photon/bmp/dhcp-host-configuration" + subnetString, null));
      testHost.sendRequestAndWait(configDelete);
    } catch (Throwable t) {

    }

//    DiskService.DiskState diskState = new DiskService.DiskState();
//    diskState.sourceImageReference = URI.create("http://15.147.37.61:70/isoserverimage.tar");
//    diskState.type = DiskService.DiskType.NETWORK;
//    diskState.id = UUID.randomUUID().toString();
//    diskState.name = "isoserverimage";

//    Operation diskOp = Operation
//        .createPost(UriUtils.buildUri(xenonHostAddr, xenonPort, "/photon/bmp/disk-service", null))
//        .addPragmaDirective(Operation.PRAGMA_DIRECTIVE_FORCE_INDEX_UPDATE);
//    diskOp.setBody(diskState);
//    testHost.sendRequestAndWait(diskOp);

//    ComputeService.ComputeState computeState = new ComputeService.ComputeState();
//    computeState.id = UUID.randomUUID().toString();
//
//    Operation computeOp = Operation
//        .createPost(UriUtils.buildUri(xenonHostAddr, xenonPort, "/photon/bmp/compute-service", null))
//        .addPragmaDirective(Operation.PRAGMA_DIRECTIVE_FORCE_INDEX_UPDATE);
//    computeOp.setBody(computeState);
//    testHost.sendRequestAndWait(computeOp);

    DhcpConfigurationService.State configurationServiceState = new DhcpConfigurationService.State();
    configurationServiceState.documentSelfLink = DhcpConfigurationServiceFactory.SELF_LINK + subnetString;
    configurationServiceState.isEnabled = true;
    configurationServiceState.leaseDurationTimeMicros = 12055;

    configurationServiceState.nameServerAddresses = new String[] {"10.118.98.1", "10.118.98.2"};
    configurationServiceState.routerAddresses = new String[] {"10.146.37.253"};
    configurationServiceState.computeStateReference = UriUtils.buildUri("15.147.37.61", xenonPort,
        "/photon/bmp/compute-service/e369b2e2-49a4-4c02-9020-98f3dc42ddc7", null).toString();
    configurationServiceState.diskStateReference = UriUtils.buildUri("15.147.37.61", xenonPort,
        "/photon/bmp/disk-service/eda5489b-2551-4ca8-97f6-ec983caa6f1b", null).toString();

    Operation configPost = Operation
        .createPost(UriUtils.buildUri(xenonHostAddr, xenonPort, "/photon/bmp/dhcp-host-configuration", null))
        .addPragmaDirective(Operation.PRAGMA_DIRECTIVE_FORCE_INDEX_UPDATE);
    configPost.setBody(configurationServiceState);
    testHost.sendRequestAndWait(configPost);

  }

  @Test
  public void createTestSubnet() throws Throwable {

   // createSubnet();


    createHostConfig();
  }
}
