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

import com.vmware.photon.controller.api.common.exceptions.external.ExternalException;
import com.vmware.photon.controller.api.model.Datastore;
import com.vmware.photon.controller.api.model.Host;
import com.vmware.photon.controller.api.model.HostSetAvailabilityZoneOperation;
import com.vmware.photon.controller.api.model.Task;
import com.vmware.photon.controller.api.model.UsageTag;
import com.vmware.photon.controller.apife.clients.HostFeClient;
import com.vmware.photon.controller.apife.exceptions.external.HostNotFoundException;
import com.vmware.photon.controller.apife.resources.host.HostResource;
import com.vmware.photon.controller.apife.resources.routes.HostResourceRoutes;
import com.vmware.photon.controller.apife.resources.routes.TaskResourceRoutes;

import com.google.common.collect.ImmutableSet;
import org.mockito.Mock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.Test;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;

import javax.ws.rs.client.Entity;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriBuilder;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/**
 * Tests {@link com.vmware.photon.controller.apife.resources.host.HostResource}.
 */
public class HostResourceTest extends ResourceTest {
  private static final Logger logger = LoggerFactory.getLogger(HostResourceTest.class);

  private String taskId = "task1";
  private String hostId = "host1";
  private String availabilityZoneId = "zone1";
  private String taskRoutePath =
      UriBuilder.fromPath(TaskResourceRoutes.TASK_PATH).build(taskId).toString();
  private String hostRoutePath =
      UriBuilder.fromPath(HostResourceRoutes.HOST_PATH).build(hostId).toString();
private String hostSetAvailabilityZoneRoute =
    UriBuilder.fromPath(HostResourceRoutes.HOST_PATH)
        .path(HostResourceRoutes.HOST_SET_AVAILABILITY_ZONE_ACTION)
        .build(hostId, availabilityZoneId)
        .toString();

  @Mock
  private HostFeClient hostFeClient;

  @Override
  protected void setUpResources() throws Exception {
    addResource(new HostResource(hostFeClient));
  }

  @Test
  public void testGetHost() throws ExternalException {

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

    doReturn(host).when(hostFeClient).getHost(eq(hostId));

    Response clientResponse = client()
        .target(hostRoutePath)
        .request("application/json")
        .get();
    assertThat(clientResponse.getStatus(), is(200));

    Host retrievedHost = clientResponse.readEntity(Host.class);
    assertThat(retrievedHost, is(host));
  }

  @Test
  public void testGetNonExistingHost() throws ExternalException {
    doThrow(new HostNotFoundException(hostId))
        .when(hostFeClient)
        .getHost(eq(hostId));

    Response clientResponse = client()
        .target(hostRoutePath)
        .request("application/json")
        .get();
    assertThat(clientResponse.getStatus(), is(404));
  }

  @Test
  public void testDeleteHost() throws ExternalException, URISyntaxException {
    Task task = new Task();
    task.setId(taskId);
    doReturn(task).when(hostFeClient).deleteHost(eq(hostId));

    Response clientResponse = client()
        .target(hostRoutePath)
        .request("application/json")
        .delete();
    assertThat(clientResponse.getStatus(), is(201));

    Task responseTask = clientResponse.readEntity(Task.class);
    assertThat(responseTask, is(task));
    assertThat(new URI(responseTask.getSelfLink()).isAbsolute(), is(true));
    assertThat(responseTask.getSelfLink().endsWith(taskRoutePath), is(true));
  }

  @Test
  public void testDeleteNonExistingHost() throws ExternalException {
    doThrow(new HostNotFoundException(hostId))
        .when(hostFeClient)
        .deleteHost(eq(hostId));

    Response clientResponse = client()
        .target(hostRoutePath)
        .request("application/json")
        .delete();
    assertThat(clientResponse.getStatus(), is(404));
  }


  @Test
  public void testSetHostAvailabilityZone() throws Throwable {
    HostSetAvailabilityZoneOperation op = new HostSetAvailabilityZoneOperation();
    op.setAvailabilityZoneId(availabilityZoneId);

    Task task = new Task();
    task.setId(taskId);
    doReturn(task).when(hostFeClient).setAvailabilityZone(eq(hostId), eq(op));

    Response response = client()
        .target(hostSetAvailabilityZoneRoute)
        .request()
        .post(Entity.entity(op, MediaType.APPLICATION_JSON_TYPE));

    assertThat(response.getStatus(), is(Response.Status.CREATED.getStatusCode()));

    Task responseTask = response.readEntity(Task.class);
    assertThat(responseTask, is(task));
    assertThat(new URI(responseTask.getSelfLink()).isAbsolute(), is(true));
    assertThat(responseTask.getSelfLink().endsWith(taskRoutePath), is(true));
  }
}
