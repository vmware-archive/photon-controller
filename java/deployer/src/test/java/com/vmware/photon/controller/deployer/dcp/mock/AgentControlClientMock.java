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

package com.vmware.photon.controller.deployer.dcp.mock;

import com.vmware.photon.controller.agent.gen.AgentControl;
import com.vmware.photon.controller.agent.gen.ProvisionResponse;
import com.vmware.photon.controller.agent.gen.ProvisionResultCode;
import com.vmware.photon.controller.common.clients.AgentControlClient;
import com.vmware.photon.controller.common.thrift.ClientPoolFactory;
import com.vmware.photon.controller.common.thrift.ClientProxyFactory;

import org.apache.thrift.TException;
import org.apache.thrift.async.AsyncMethodCallback;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Set;

/**
 * This class implements a mock {@link AgentControlClient} object for use in testing.
 */
public class AgentControlClientMock extends AgentControlClient {

  private static final Logger logger = LoggerFactory.getLogger(AgentControlClientMock.class);

  private ProvisionResultCode provisionResultCode;
  private Exception provisionFailure;

  private AgentControlClientMock(Builder builder) {
    super(mock(ClientProxyFactory.class), mock(ClientPoolFactory.class));
    this.provisionResultCode = builder.provisionResultCode;
    this.provisionFailure = builder.provisionFailure;
  }

  @Override
  public void provision(
      String availabilityZone,
      List<String> datastores,
      Set<String> imageDatastoreNames,
      boolean usedForVMs,
      List<String> networks,
      String hostAddress,
      int hostPort,
      List<String> chairmanServerList,
      double memoryOvercommit,
      String loggingEndpoint,
      String logLevel,
      String statsStoreEndpoint,
      String usageTags,
      String ntpEndpoint,
      String hostId,
      String deploymentId,
      AsyncMethodCallback<AgentControl.AsyncClient.provision_call> handler) {

    logger.info("Host provision complete invocation");

    if (null != provisionFailure) {
      handler.onError(provisionFailure);

    } else if (null != provisionResultCode) {
      AgentControl.AsyncClient.provision_call provisionCall = mock(AgentControl.AsyncClient.provision_call.class);
      ProvisionResponse provisionResponse = new ProvisionResponse();
      provisionResponse.setResult(provisionResultCode);

      try {
        when(provisionCall.getResult()).thenReturn(provisionResponse);
      } catch (TException e) {
        throw new RuntimeException("Failed to mock provisionCall.getResult");
      }

      handler.onComplete(provisionCall);

    } else {
      throw new IllegalStateException("No result or failure specified for provision");
    }
  }

  /**
   * This class implements a builder for {@link AgentControlClientMock} objects.
   */
  public static class Builder {

    private ProvisionResultCode provisionResultCode;
    private Exception provisionFailure;

    public Builder provisionResultCode(ProvisionResultCode provisionResultCode) {
      this.provisionResultCode = provisionResultCode;
      return this;
    }

    public Builder provisionFailure(Exception provisionFailure) {
      this.provisionFailure = provisionFailure;
      return this;
    }

    public AgentControlClientMock build() {
      return new AgentControlClientMock(this);
    }
  }
}
