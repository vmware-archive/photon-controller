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
import com.vmware.photon.controller.api.model.ApiError;
import com.vmware.photon.controller.api.model.ResourceList;
import com.vmware.photon.controller.api.model.Task;
import com.vmware.photon.controller.apife.clients.TenantFeClient;
import com.vmware.photon.controller.apife.resources.routes.TaskResourceRoutes;
import com.vmware.photon.controller.apife.resources.routes.TenantResourceRoutes;
import com.vmware.photon.controller.apife.resources.tenant.TenantSecurityGroupsResource;

import org.mockito.Mock;
import org.testng.annotations.Test;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Mockito.when;

import javax.ws.rs.client.Entity;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriBuilder;

import java.net.URI;
import java.util.Arrays;
import java.util.List;

/**
 * Tests {@link com.vmware.photon.controller.apife.resources.tenant.TenantSecurityGroupsResource}.
 */
public class TenantSecurityGroupsResourceTest extends ResourceTest {

  private String taskId = "task1";
  private String taskRoutePath = UriBuilder.fromPath(TaskResourceRoutes.TASK_PATH).build(taskId).toString();

  private String tenantId = "tenantId";
  private String tenantRoutePath = UriBuilder.fromPath(TenantResourceRoutes.TENANT_SET_SECURITY_GROUPS_PATH)
      .build(tenantId).toString();

  @Mock
  private TenantFeClient tenantFeClient;

  @Override
  public void setUpResources() {
    addResource(new TenantSecurityGroupsResource(tenantFeClient));
  }

  @Test
  public void testSetSecurityGroupsSuccess() throws Exception {
    Task task = new Task();
    task.setId(taskId);

    List<String> securityGroups = Arrays.asList(new String[]{"tenant\\adminGroup1", "tenant\\adminGroup2"});
    when(tenantFeClient.setSecurityGroups(tenantId, securityGroups)).thenReturn(task);

    Response response = client()
        .target(tenantRoutePath)
        .request()
        .post(Entity.entity(new ResourceList<>(securityGroups), MediaType.APPLICATION_JSON_TYPE));
    assertThat(response.getStatus(), is(200));

    Task responseTask = response.readEntity(Task.class);
    assertThat(responseTask, is(task));
    assertThat(new URI(responseTask.getSelfLink()).isAbsolute(), is(true));
    assertThat(responseTask.getSelfLink().endsWith(taskRoutePath), is(true));
  }

  @Test
  public void testSetSecurityGroupsFail() throws Exception {
    List<String> securityGroups = Arrays.asList(new String[]{"tenant\\adminGroup1", "tenant\\adminGroup2"});
    when(tenantFeClient.setSecurityGroups(tenantId, securityGroups))
        .thenThrow(new ExternalException("Failed to change security groups"));

    Response response = client()
        .target(tenantRoutePath)
        .request()
        .post(Entity.entity(new ResourceList<>(securityGroups), MediaType.APPLICATION_JSON_TYPE));
    assertThat(response.getStatus(), is(500));

    ApiError errors = response.readEntity(ApiError.class);
    assertThat(errors.getCode(), is("InternalError"));
    assertThat(errors.getMessage(), is("Failed to change security groups"));
  }

  @Test
  public void testSetSecurityGroupsFailWithInvalidSecurityGroup() throws Exception {
    List<String> securityGroups = Arrays.asList(new String[]{"adminGroup1", "adminGroup2"});
    when(tenantFeClient.setSecurityGroups(tenantId, securityGroups))
        .thenThrow(new ExternalException("Failed to change security groups"));

    Response response = client()
        .target(tenantRoutePath)
        .request()
        .post(Entity.entity(new ResourceList<>(securityGroups), MediaType.APPLICATION_JSON_TYPE));
    assertThat(response.getStatus(), is(400));

    ApiError errors = response.readEntity(ApiError.class);
    assertThat(errors.getCode(), is("InvalidSecurityGroupFormat"));
    assertThat(errors.getMessage(), containsString("The security group format should match domain\\group"));
  }
}
