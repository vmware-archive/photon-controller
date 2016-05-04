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

import com.vmware.photon.controller.api.PortGroup;
import com.vmware.photon.controller.api.UsageTag;
import com.vmware.photon.controller.api.common.exceptions.external.ExternalException;
import com.vmware.photon.controller.apife.clients.PortGroupFeClient;
import com.vmware.photon.controller.apife.exceptions.external.PortGroupNotFoundException;
import com.vmware.photon.controller.apife.resources.portgroup.PortGroupResource;
import com.vmware.photon.controller.apife.resources.routes.PortGroupResourceRoutes;

import org.mockito.Mock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.Test;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;

import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriBuilder;

import java.util.Arrays;

/**
 * Tests {@link com.vmware.photon.controller.apife.resources.portgroup.PortGroupResource}.
 */
public class PortGroupResourceTest extends ResourceTest {
  private static final Logger logger = LoggerFactory.getLogger(PortGroupResourceTest.class);

  private String portGroupId = "network1";
  private String portGroupRoutePath =
      UriBuilder.fromPath(PortGroupResourceRoutes.PORT_GROUP_PATH).build(portGroupId).toString();

  @Mock
  private PortGroupFeClient portGroupFeClient;

  @Override
  protected void setUpResources() throws Exception {
    addResource(new PortGroupResource(portGroupFeClient));
  }

  @Test
  public void testGetPortGroup() throws ExternalException {
    PortGroup portGroup = new PortGroup();
    portGroup.setId(portGroupId);
    portGroup.setName("VMNetwork");
    portGroup.setUsageTags(Arrays.asList(UsageTag.CLOUD));

    doReturn(portGroup).when(portGroupFeClient).get(eq(portGroupId));

    Response clientResponse = client()
        .target(portGroupRoutePath)
        .request("application/json")
        .get();
    assertThat(clientResponse.getStatus(), is(200));

    PortGroup retrievedPortGroup = clientResponse.readEntity(PortGroup.class);
    assertThat(retrievedPortGroup, is(portGroup));
  }

  @Test
  public void testGetNonExistingPortGroup() throws ExternalException {
    doThrow(new PortGroupNotFoundException(portGroupId))
        .when(portGroupFeClient)
        .get(eq(portGroupId));

    Response clientResponse = client()
        .target(portGroupRoutePath)
        .request("application/json")
        .get();
    assertThat(clientResponse.getStatus(), is(404));
  }

}
