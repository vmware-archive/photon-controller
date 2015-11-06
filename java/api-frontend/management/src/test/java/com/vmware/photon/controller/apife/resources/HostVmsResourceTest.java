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

package com.vmware.photon.controller.apife.resources;

import com.vmware.photon.controller.api.ResourceList;
import com.vmware.photon.controller.api.Vm;
import com.vmware.photon.controller.api.common.exceptions.external.ExternalException;
import com.vmware.photon.controller.apife.clients.HostFeClient;
import com.vmware.photon.controller.apife.resources.routes.HostResourceRoutes;
import com.vmware.photon.controller.apife.resources.routes.VmResourceRoutes;

import org.mockito.Mock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.Test;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.mockito.Mockito.doReturn;

import javax.ws.rs.core.GenericType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriBuilder;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;


/**
 * Tests {@link HostVmsResource}.
 */
public class HostVmsResourceTest extends ResourceTest {
  private static final Logger logger = LoggerFactory.getLogger(HostVmsResource.class);

  private static final String hostId = "host123";

  private String vmsRoute =
      UriBuilder.fromPath(HostResourceRoutes.HOST_VMS_PATH).build(hostId).toString();

  @Mock
  private HostFeClient hostFeClient;

  @Override
  protected void setUpResources() {
    addResource(new HostVmsResource(hostFeClient));
  }

  @Test
  public void testListVmsOnHost() throws ExternalException, URISyntaxException {
    Vm vm1 = new Vm();
    vm1.setId("vm1");
    vm1.setName("vm1name");

    Vm vm2 = new Vm();
    vm2.setId("vm2");
    vm2.setName("vm1name");

    List<Vm> vmList = new ArrayList<Vm>();
    vmList.add(vm1);
    vmList.add(vm2);
    ResourceList<Vm> resourceList = new ResourceList<>(new ArrayList<>(vmList));
    doReturn(resourceList).when(hostFeClient).listAllVms(hostId);

    Response clientResponse = client()
        .target(vmsRoute)
        .request("application/json")
        .get();

    assertThat(clientResponse.getStatus(), is(200));

    ResourceList<Vm> retrievedResources = clientResponse.readEntity(new GenericType<ResourceList<Vm>>() {
    });
    assertThat(retrievedResources.getItems().size(), is(2));
    List<Vm> retrievedVmList = retrievedResources.getItems();
    for (int i = 0; i < retrievedVmList.size(); i++) {
      Vm retrievedVm = retrievedVmList.get(i);
      assertThat(retrievedVm, is(vmList.get(i)));

      String vmRoutePath = UriBuilder.fromPath(VmResourceRoutes.VM_PATH).build(vmList.get(i).getId()).toString();
      assertThat(new URI(retrievedVm.getSelfLink()).isAbsolute(), is(true));
      assertThat(retrievedVm.getSelfLink().endsWith(vmRoutePath), is(true));
    }
  }
}
