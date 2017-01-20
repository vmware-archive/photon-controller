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

package com.vmware.photon.controller.api.frontend.resources;

import com.vmware.photon.controller.api.frontend.clients.ServiceFeClient;
import com.vmware.photon.controller.api.frontend.exceptions.external.ExternalException;
import com.vmware.photon.controller.api.frontend.resources.routes.ServiceResourceRoutes;
import com.vmware.photon.controller.api.frontend.resources.routes.TaskResourceRoutes;
import com.vmware.photon.controller.api.frontend.resources.service.ServiceResource;
import com.vmware.photon.controller.api.model.Service;
import com.vmware.photon.controller.api.model.ServiceState;
import com.vmware.photon.controller.api.model.ServiceType;
import com.vmware.photon.controller.api.model.Task;
import com.vmware.photon.controller.servicesmanager.servicedocuments.ServicesManagerConstants;

import com.google.common.collect.ImmutableMap;
import org.mockito.Mock;
import org.testng.annotations.Test;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.powermock.api.mockito.PowerMockito.when;

import javax.ws.rs.client.Entity;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriBuilder;

import java.net.URI;

/**
 * Tests {@link com.vmware.photon.controller.api.frontend.resources.service.ServiceResource}.
 */
public class ServiceResourceTest extends ResourceTest {

  private final String serviceId = "serviceId1";
  private final String serviceName = "serviceName1";
  private final String taskId = "taskId1";
  private final String projectId = "projectId1";

  private final String serviceRoute =
      UriBuilder.fromPath(ServiceResourceRoutes.SERVICES_PATH).build(serviceId).toString();
  private final String serviceTriggerMaintenanceRoute =
      UriBuilder.fromPath(ServiceResourceRoutes.SERVICES_PATH + ServiceResourceRoutes.SERVICES_TRIGGER_MAINTENANCE_PATH)
          .build(serviceId).toString();
  private final String taskRoute =
      UriBuilder.fromPath(TaskResourceRoutes.TASK_PATH).build(taskId).toString();

  @Mock
  private ServiceFeClient serviceFeClient;

  @Override
  protected void setUpResources() throws Exception {
    addResource(new ServiceResource(serviceFeClient));
  }

  private Service createService() {
    Service c = new Service();
    c.setId(serviceId);
    c.setType(ServiceType.KUBERNETES);
    c.setState(ServiceState.READY);
    c.setName(serviceName);
    c.setProjectId(projectId);
    c.setWorkerCount(3);
    c.setExtendedProperties(ImmutableMap.of(
        ServicesManagerConstants.EXTENDED_PROPERTY_CONTAINER_NETWORK, "10.1.0.0/16"));
    return c;
  }

  @Test
  public void testGetServiceById() throws ExternalException {
    Service c1 = createService();

    when(serviceFeClient.get(serviceId)).thenReturn(c1);

    Response response = client().target(serviceRoute).request().get();
    assertThat(response.getStatus(), is(Response.Status.OK.getStatusCode()));

    Service c2 = response.readEntity(Service.class);
    assertThat(c2.toString(), is(c1.toString()));
    assertThat(c2, is(c1));
  }

  @Test
  public void testDeleteServiceById() throws Exception {
    Task t1 = new Task();
    t1.setId(taskId);
    when(serviceFeClient.delete(serviceId)).thenReturn(t1);

    Response response = client().target(serviceRoute).request().delete();

    assertThat(response.getStatus(), is(Response.Status.CREATED.getStatusCode()));

    Task t2 = response.readEntity(Task.class);
    assertThat(t2, is(t1));
    assertThat(new URI(t2.getSelfLink()).isAbsolute(), is(true));
    assertThat(t2.getSelfLink().endsWith(taskRoute), is(true));
  }

  @Test
  public void testTriggerMaintenanceService() throws Exception {
    Task task = new Task();
    task.setId(taskId);
    when(serviceFeClient.triggerMaintenance(serviceId)).thenReturn(task);
    Response response = client()
        .target(serviceTriggerMaintenanceRoute)
        .request()
        .post(Entity.entity(null, MediaType.APPLICATION_JSON_TYPE));

    assertThat(response.getStatus(), is(Response.Status.OK.getStatusCode()));
  }
}
