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

package com.vmware.photon.controller.api.frontend.filter;

import com.vmware.photon.controller.api.frontend.exceptions.external.ErrorCode;
import com.vmware.photon.controller.api.frontend.helpers.JerseyPropertiesDelegate;
import com.vmware.photon.controller.api.frontend.helpers.JerseySecurityContext;
import com.vmware.photon.controller.api.frontend.resources.routes.DeploymentResourceRoutes;
import com.vmware.photon.controller.api.frontend.resources.routes.VmResourceRoutes;
import com.vmware.photon.controller.api.model.ApiError;
import com.vmware.photon.controller.cloudstore.SystemConfig;
import com.vmware.photon.controller.common.xenon.host.PhotonControllerXenonHost;

import org.glassfish.jersey.server.ContainerRequest;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;
import static org.testng.AssertJUnit.fail;

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MultivaluedHashMap;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriBuilder;

import java.net.URI;
import java.net.URISyntaxException;

/**
 * Tests {@link PauseFilter}.
 */
public class PauseFilterTest {

  private SystemConfig systemConfig;

  private PauseFilter pauseFilter;

  private PhotonControllerXenonHost xenonHost;

  @BeforeMethod
  public void setUp() {
    xenonHost = mock(PhotonControllerXenonHost.class);
    this.systemConfig = mock(SystemConfig.class);
    this.pauseFilter = spy(new PauseFilter());

  }

  @DataProvider(name = "SuccessfulRequests")
  Object[][] getSuccessfulRequests() {
    return new Object[][]{
        {false, "POST", UriBuilder.fromPath(VmResourceRoutes.API).build().toString()},
        {true, "GET", UriBuilder.fromPath(VmResourceRoutes.API).build().toString()},
        {false, "POST",
            UriBuilder.fromPath(DeploymentResourceRoutes.DEPLOYMENT_PATH +
                DeploymentResourceRoutes.RESUME_SYSTEM_ACTION).build("d1").toString()},
    };
  }

  @Test(dataProvider = "SuccessfulRequests")
  public void testSuccess(boolean servicePaused, String httpMethod, String path) throws Throwable {
    ContainerRequest request = buildRequest(path, httpMethod, new MultivaluedHashMap<>());
    when(systemConfig.isPaused()).thenReturn(servicePaused);
    when(pauseFilter.getSystemConfig()).thenReturn(systemConfig);
    try {
      this.pauseFilter.filter(request);
    } catch (Exception e) {
      fail("filter should not have thrown exception");
    }
  }

  @DataProvider(name = "UnsuccessfulRequests")
  Object[][] getUnsuccessfulRequests() {
    return new Object[][]{
        {true, "GET", UriBuilder.fromPath(VmResourceRoutes.VM_SUBNETS_PATH).build("vmId").toString()},
        {true, "POST", UriBuilder.fromPath(VmResourceRoutes.API).build().toString()}
    };
  }

  @Test(dataProvider = "UnsuccessfulRequests")
  public void testRejection(boolean servicePaused, String httpMethod, String path) throws Throwable {
    ContainerRequest request = buildRequest(path, httpMethod, new MultivaluedHashMap<>());
    when(systemConfig.isPaused()).thenReturn(servicePaused);
    when(pauseFilter.getSystemConfig()).thenReturn(systemConfig);

    try {
      this.pauseFilter.filter(request);
      fail("filter should throw exception");
    } catch (WebApplicationException ex) {
      Response response = ex.getResponse();
      assertThat(response.getStatusInfo(), is(Response.Status.FORBIDDEN));
      ApiError error = (ApiError) response.getEntity();
      assertThat(error.getCode(), is(ErrorCode.SYSTEM_PAUSED.getCode()));
      assertThat(error.getMessage(), is("System is paused"));
    }
  }

  private ContainerRequest buildRequest(String path, String method, MultivaluedMap<String, String> headers) throws
      URISyntaxException {
    ContainerRequest containerRequest = new ContainerRequest(new URI(""), new URI(path.replaceAll("^/", "")), method,
        new JerseySecurityContext(), new JerseyPropertiesDelegate());
    for (String header : headers.keySet()) {
      containerRequest.getHeaders().put(header, headers.get(header));
    }
    return containerRequest;
  }

}
