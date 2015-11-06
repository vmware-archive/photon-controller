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

import com.vmware.photon.controller.api.Datastore;
import com.vmware.photon.controller.api.Host;
import com.vmware.photon.controller.api.ResourceList;
import com.vmware.photon.controller.api.UsageTag;
import com.vmware.photon.controller.apife.clients.HostFeClient;
import com.vmware.photon.controller.apife.resources.routes.HostResourceRoutes;
import com.vmware.photon.controller.apife.resources.routes.TaskResourceRoutes;

import com.google.common.collect.ImmutableSet;

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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Tests {@link HostsResource}.
 */
public class HostsResourceTest extends ResourceTest {
  private static final Logger logger = LoggerFactory.getLogger(HostsResourceTest.class);

  private String taskId = "task1";
  private String hostId = "host1";
  private String taskRoutePath =
      UriBuilder.fromPath(TaskResourceRoutes.TASK_PATH).build(taskId).toString();
  private String hostRoutePath =
      UriBuilder.fromPath(HostResourceRoutes.HOST_PATH).build(hostId).toString();

  @Mock
  private HostFeClient hostFeClient;

  @Override
  protected void setUpResources() {
    addResource(new HostsResource(hostFeClient));
  }

  @Test
  public void testGetEmptyHostList() {
    ResourceList<Host> resourceList = new ResourceList<>(new ArrayList<Host>());
    doReturn(resourceList).when(hostFeClient).listAllHosts();

    Response clientResponse = client()
        .target(HostResourceRoutes.API)
        .request("application/json")
        .get();

    assertThat(clientResponse.getStatus(), is(200));

    ResourceList<Host> retrievedResources = clientResponse.readEntity(new GenericType<ResourceList<Host>>() {
    });
    assertThat(retrievedResources.getItems().size(), is(0));
  }

  @Test
  public void testGetHostList() throws URISyntaxException {
    Map<String, String> datastoreMetadata = new HashMap<>();
    datastoreMetadata.put("sharedImageDatastore", "true");
    datastoreMetadata.put("usedForVms", "true");
    datastoreMetadata.put("id", "datastore1");

    Datastore datastore = new Datastore();
    datastore.setId("83816c54-e2e9-4cf5-87bc-717d16942556");
    datastore.setTags(ImmutableSet.of("tag1"));
    datastore.setSelfLink("self link");

    Map<String, Datastore> datastoreMap = new HashMap<>();
    datastoreMap.put("datastore1", datastore);

    Map<String, String> hostMetadata = new HashMap<>();
    hostMetadata.put("id", "h_146_36_27");

    Host host = new Host("10.146.1.1",
        "username",
        "password",
        "availabilityZone",
        "6.0",
        new ArrayList<UsageTag>() {{
          add(UsageTag.MGMT);
        }},
        hostMetadata
    );
    host.setId(hostId);

    List<Host> hostList = new ArrayList<>();
    hostList.add(host);

    ResourceList<Host> resourceList = new ResourceList<>(hostList);
    doReturn(resourceList).when(hostFeClient).listAllHosts();

    Response clientResponse = client()
        .target(HostResourceRoutes.API)
        .request("application/json")
        .get();

    assertThat(clientResponse.getStatus(), is(200));

    ResourceList<Host> retrievedResources = clientResponse.readEntity(new GenericType<ResourceList<Host>>() {
    });
    assertThat(retrievedResources.getItems().size(), is(1));

    Host retrievedHost = retrievedResources.getItems().get(0);
    assertThat(retrievedHost, is(host));

    assertThat(new URI(retrievedHost.getSelfLink()).isAbsolute(), is(true));
    System.out.println(retrievedHost.getSelfLink());
    assertThat(retrievedHost.getSelfLink().endsWith(hostRoutePath), is(true));
  }
}
