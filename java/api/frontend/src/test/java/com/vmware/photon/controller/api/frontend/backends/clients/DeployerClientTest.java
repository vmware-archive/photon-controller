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

package com.vmware.photon.controller.api.frontend.backends.clients;

import com.vmware.photon.controller.api.frontend.entities.HostEntity;
import com.vmware.photon.controller.api.frontend.exceptions.external.SpecInvalidException;
import com.vmware.photon.controller.api.frontend.lib.UsageTagHelper;
import com.vmware.photon.controller.api.model.UsageTag;
import com.vmware.photon.controller.cloudstore.xenon.entity.HostService;
import com.vmware.photon.controller.deployer.xenon.task.ValidateHostTaskService;
import com.vmware.xenon.common.Operation;

import org.mockito.Mock;
import org.powermock.modules.testng.PowerMockTestCase;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.isNotNull;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;

import java.util.ArrayList;

/**
 * Tests {@link DeployerClient}.
 */
public class DeployerClientTest {

  @Test
  private void dummy() {
  }

  /**
   * Tests for the createHost method.
   */
  public static class CreateHostTest extends PowerMockTestCase {
    @Mock
    private PhotonControllerXenonRestClient photonControllerXenonRestClient;
    @Mock
    private ApiFeXenonRestClient apiFeXenonRestClient;
    private DeployerClient deployerClient;

    @BeforeMethod
    public void setUp() throws Throwable {
      Operation operation = new Operation();
      ValidateHostTaskService.State task = new ValidateHostTaskService.State();
      operation.setBody(task);

      when(photonControllerXenonRestClient.post(any(String.class), any(ValidateHostTaskService.State.class)))
          .thenReturn(operation);

      when(apiFeXenonRestClient.post(any(String.class), any(HostService.State.class)))
          .thenReturn(null);

      deployerClient = new DeployerClient(photonControllerXenonRestClient, apiFeXenonRestClient);
    }

    private static HostEntity buildCreateSpec() {
      HostEntity host = new HostEntity();
      host.setAddress("1.1.1.1");
      host.setUsername("user1");
      host.setPassword("pwd");
      host.setId("hostId");
      host.setUsageTags(UsageTagHelper.serialize(new ArrayList<UsageTag>() {{
        add(UsageTag.MGMT);
      }}));
      return host;
    }

    @Test
    public void testCreateHost() throws SpecInvalidException {
      HostEntity host = buildCreateSpec();
      ValidateHostTaskService.State createTask = deployerClient.createHost(host);

      assertEquals(createTask.documentSelfLink, isNotNull());
    }
  }
}
