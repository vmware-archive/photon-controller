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
import com.vmware.photon.controller.api.Task;
import com.vmware.photon.controller.api.Tenant;
import com.vmware.photon.controller.api.TenantCreateSpec;
import com.vmware.photon.controller.api.common.exceptions.external.ExternalException;
import com.vmware.photon.controller.apife.clients.TenantFeClient;
import com.vmware.photon.controller.apife.resources.routes.TaskResourceRoutes;
import com.vmware.photon.controller.apife.resources.routes.TenantResourceRoutes;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import org.hamcrest.CoreMatchers;
import org.hamcrest.Matchers;
import org.mockito.Mock;
import org.testng.annotations.Test;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.when;

import javax.ws.rs.client.Entity;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.GenericType;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriBuilder;

import java.net.URI;
import java.util.List;

/**
 * Tests {@link TenantResource}.
 */
public class TenantsResourceTest extends ResourceTest {

  private String taskId = "task1";

  private String taskRoutePath =
      UriBuilder.fromPath(TaskResourceRoutes.TASK_PATH).build(taskId).toString();

  @Mock
  private TenantFeClient tenantFeClient;

  private TenantCreateSpec spec;

  @Override
  public void setUpResources() throws Exception {
    spec = new TenantCreateSpec();
    spec.setName("spec");

    addResource(new TenantsResource(tenantFeClient));
  }

  @Test
  public void testSuccessfulCreateTenant() throws Exception {
    Task task = new Task();
    task.setId(taskId);
    when(tenantFeClient.create(spec)).thenReturn(task);

    Response response = createTenant();
    assertThat(response.getStatus(), is(201));

    Task responseTask = response.readEntity(Task.class);
    assertThat(responseTask, Matchers.is(task));
    assertThat(new URI(responseTask.getSelfLink()).isAbsolute(), CoreMatchers.is(true));
    assertThat(responseTask.getSelfLink().endsWith(taskRoutePath), CoreMatchers.is(true));
  }

  @Test
  public void testFailedCreateTenant() throws Exception {
    when(tenantFeClient.create(spec)).thenThrow(new ExternalException("failed"));
    assertThat(createTenant().getStatus(), is(500));
  }

  @Test
  public void testInvalidTenant() throws Exception {
    spec.setName(" bad name ");
    assertThat(createTenant().getStatus(), is(400));
  }

  @Test
  public void createInvalidJsonTenant() {
    Response r = client()
        .target(TenantResourceRoutes.API)
        .request()
        .post(Entity.entity("{ \"name\":\"thename\",\"foo\"}", MediaType.APPLICATION_JSON_TYPE));
    assertThat(r.getStatus(), is(400));
  }

  @Test
  public void testFindAllTenants() throws Exception {
    Tenant t1 = new Tenant();
    t1.setId("t1");
    t1.setName("t1");

    Tenant t2 = new Tenant();
    t2.setId("t2");
    t2.setName("t2");

    when(tenantFeClient.find(Optional.<String>absent())).thenReturn(new ResourceList<>(ImmutableList.of(t1, t2)));
    Response response = getTenants(Optional.<String>absent());
    assertThat(response.getStatus(), is(200));

    List<Tenant> tenants = response.readEntity(
        new GenericType<ResourceList<Tenant>>() {
        }
    ).getItems();

    assertThat(tenants.size(), is(2));
    assertThat(tenants.get(0), is(t1));
    assertThat(t1.getSelfLink().endsWith(
        UriBuilder.fromPath(TenantResourceRoutes.TENANT_PATH).build("t1").toString()), is(true));
    assertThat(tenants.get(1), is(t2));
    assertThat(t2.getSelfLink().endsWith(
        UriBuilder.fromPath(TenantResourceRoutes.TENANT_PATH).build("t2").toString()), is(true));

    for (Tenant t : tenants) {
      assertThat(new URI(t.getSelfLink()).isAbsolute(), CoreMatchers.is(true));
    }
  }

  @Test
  public void testFindTenantsByName() throws Exception {
    Tenant t1 = new Tenant();
    t1.setId("t1");
    t1.setName("t1");

    when(tenantFeClient.find(Optional.of("t1"))).thenReturn(new ResourceList<>(ImmutableList.of(t1)));
    Response response = getTenants(Optional.of("t1"));
    assertThat(response.getStatus(), is(200));

    List<Tenant> tenants = response.readEntity(
        new GenericType<ResourceList<Tenant>>() {
        }
    ).getItems();
    assertThat(tenants.size(), is(1));
    assertThat(tenants.get(0), is(t1));
    assertThat(t1.getSelfLink().endsWith(
        UriBuilder.fromPath(TenantResourceRoutes.TENANT_PATH).build("t1").toString()), is(true));
    assertThat(new URI(t1.getSelfLink()).isAbsolute(), CoreMatchers.is(true));
  }

  private Response createTenant() {
    return client()
        .target(TenantResourceRoutes.API)
        .request()
        .post(Entity.entity(spec, MediaType.APPLICATION_JSON_TYPE));
  }

  private Response getTenants(Optional<String> name) {
    WebTarget resource = client().target(TenantResourceRoutes.API);
    if (name.isPresent()) {
      resource = resource.queryParam("name", name.get());
    }

    return resource.request().get();
  }
}
