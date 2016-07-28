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

import com.vmware.photon.controller.api.model.Task;
import com.vmware.photon.controller.api.model.Tenant;
import com.vmware.photon.controller.apife.clients.TenantFeClient;
import com.vmware.photon.controller.apife.entities.TenantEntity;
import com.vmware.photon.controller.apife.resources.routes.TaskResourceRoutes;
import com.vmware.photon.controller.apife.resources.routes.TenantResourceRoutes;
import com.vmware.photon.controller.apife.resources.tenant.TenantResource;

import org.hamcrest.CoreMatchers;
import org.hamcrest.Matchers;
import org.mockito.Mock;
import org.testng.annotations.Test;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.when;

import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriBuilder;

import java.net.URI;

/**
 * Tests {@link com.vmware.photon.controller.apife.resources.tenant.TenantResource}.
 */
public class TenantResourceTest extends ResourceTest {

  private String tenantId = "t1";

  private String tenantRoute =
      UriBuilder.fromPath(TenantResourceRoutes.TENANT_PATH).build(tenantId).toString();

  private String taskId = "task1";

  private String taskRoutePath =
      UriBuilder.fromPath(TaskResourceRoutes.TASK_PATH).build(taskId).toString();

  @Mock
  private TenantFeClient tenantFeClient;

  @Override
  protected void setUpResources() throws Exception {
    addResource(new TenantResource(tenantFeClient));
  }

  @Test
  public void testGetTenantById() throws Exception {
    Tenant tenant = new Tenant();
    tenant.setId(tenantId);

    when(tenantFeClient.get(tenantId)).thenReturn(tenant);

    Response response = client().target(tenantRoute).request().get();
    assertThat(response.getStatus(), is(200));

    Tenant responseTenant = response.readEntity(Tenant.class);
    assertThat(responseTenant, is(tenant));
    assertThat(new URI(responseTenant.getSelfLink()).isAbsolute(), CoreMatchers.is(true));
    assertThat(responseTenant.getSelfLink().endsWith(tenantRoute), CoreMatchers.is(true));
  }

  @Test
  public void testDeleteTenant() throws Exception {
    TenantEntity tenantEntity = new TenantEntity();
    tenantEntity.setId(tenantId);

    Task task = new Task();
    task.setId(taskId);
    when(tenantFeClient.delete(tenantEntity.getId()))
        .thenReturn(task);

    Response response = client().target(tenantRoute).request().delete();
    assertThat(response.getStatus(), is(Response.Status.CREATED.getStatusCode()));

    Task responseTask = response.readEntity(Task.class);
    assertThat(responseTask, Matchers.is(task));
    assertThat(new URI(responseTask.getSelfLink()).isAbsolute(), CoreMatchers.is(true));
    assertThat(responseTask.getSelfLink().endsWith(taskRoutePath), CoreMatchers.is(true));
  }
}
