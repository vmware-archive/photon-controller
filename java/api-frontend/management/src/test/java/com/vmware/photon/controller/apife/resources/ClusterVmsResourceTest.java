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
import com.vmware.photon.controller.apife.clients.ClusterFeClient;
import com.vmware.photon.controller.apife.resources.routes.ClusterResourceRoutes;
import com.vmware.photon.controller.apife.resources.routes.VmResourceRoutes;

import org.mockito.Mock;
import org.testng.annotations.Test;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.powermock.api.mockito.PowerMockito.when;

import javax.ws.rs.core.GenericType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriBuilder;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

/**
 * Tests {@link ClusterVmsResource}.
 */
public class ClusterVmsResourceTest extends ResourceTest {

  private final String clusterId = "clusterId1";

  private final String clusterVmsRoute =
      UriBuilder.fromPath(ClusterResourceRoutes.CLUSTER_VMS_PATH).build(clusterId).toString();

  @Mock
  private ClusterFeClient clusterFeClient;

  @Override
  protected void setUpResources() throws Exception {
    addResource(new ClusterVmsResource(clusterFeClient));
  }

  @Test
  public void testGet() throws Throwable {
    ArrayList<Vm> vms = new ArrayList<>();
    for (int i = 0; i < 5; i++) {
      Vm vm = new Vm();
      vm.setId("vm" + i);
      vms.add(vm);
    }

    when(clusterFeClient.findVms(clusterId)).thenReturn(new ResourceList<>(vms));

    Response response = client().target(clusterVmsRoute).request().get();
    assertThat(response.getStatus(), is(Response.Status.OK.getStatusCode()));

    List<Vm> result = response.readEntity(new GenericType<ResourceList<Vm>>(){}).getItems();
    assertThat(result.size(), is(vms.size()));

    for (int i = 0; i < result.size(); i++) {
      assertThat(new URI(result.get(i).getSelfLink()).isAbsolute(), is(true));
      assertThat(result.get(i), is(vms.get(i)));

      String vmRoutePath = UriBuilder.fromPath(VmResourceRoutes.VM_PATH).build(vms.get(i).getId()).toString();
      assertThat(result.get(i).getSelfLink().endsWith(vmRoutePath), is(true));
    }
  }
}
