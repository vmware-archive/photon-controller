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

package com.vmware.photon.controller.deployer.xenon.mock;

import com.vmware.photon.controller.agent.gen.AgentControl;
import com.vmware.photon.controller.agent.gen.AgentStatusCode;
import com.vmware.photon.controller.agent.gen.AgentStatusResponse;
import com.vmware.photon.controller.agent.gen.ProvisionResponse;
import com.vmware.photon.controller.agent.gen.ProvisionResultCode;
import com.vmware.photon.controller.agent.gen.UpdateConfigResponse;
import com.vmware.photon.controller.agent.gen.UpdateConfigResultCode;
import com.vmware.photon.controller.agent.gen.UpgradeResponse;
import com.vmware.photon.controller.agent.gen.UpgradeResultCode;
import com.vmware.photon.controller.common.clients.AgentControlClient;
import com.vmware.photon.controller.common.thrift.ClientPoolFactory;
import com.vmware.photon.controller.common.thrift.ClientProxyFactory;
import com.vmware.photon.controller.stats.plugin.gen.StatsPluginConfig;

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
  private UpdateConfigResultCode updateConfigResultCode;
  private Exception updateConfigFailure;
  private UpgradeResultCode upgradeResultCode;
  private Exception upgradeFailure;
  private AgentStatusCode agentStatusCode;
  private Exception getAgentStatusFailure;

  private AgentControlClientMock(Builder builder) {
    super(mock(ClientProxyFactory.class), mock(ClientPoolFactory.class));
    this.provisionResultCode = builder.provisionResultCode;
    this.provisionFailure = builder.provisionFailure;
    this.upgradeResultCode = builder.upgradeResultCode;
    this.upgradeFailure = builder.upgradeFailure;
    this.updateConfigResultCode = builder.updateConfigResultCode;
    this.updateConfigFailure = builder.updateConfigFailure;
    this.agentStatusCode = builder.agentStatusCode;
    this.getAgentStatusFailure = builder.getAgentStatusFailure;
  }

  @Override
  public void provision(
      List<String> datastores,
      Set<String> imageDatastoreNames,
      boolean usedForVMs,
      String hostAddress,
      int hostPort,
      double memoryOvercommit,
      StatsPluginConfig statsPluginConfig,
      boolean managementOnly,
      String ntpEndpoint,
      String hostId,
      String deploymentId,
      boolean authEnabled,
      AsyncMethodCallback<AgentControl.AsyncSSLClient.provision_call> handler) {

    logger.info("Host provision complete invocation");

    if (null != provisionFailure) {
      handler.onError(provisionFailure);

    } else if (null != provisionResultCode) {
      AgentControl.AsyncSSLClient.provision_call provisionCall = mock(AgentControl.AsyncSSLClient.provision_call.class);
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

  @Override
  public void updateConfig(
      Set<String> imageDataStores,
      boolean usedForVMs,
      AsyncMethodCallback<AgentControl.AsyncSSLClient.update_config_call> handler) {

    logger.info("Host update config complete invocation");

    if (null != updateConfigFailure) {
      handler.onError(updateConfigFailure);

    } else if (null != updateConfigResultCode) {
      AgentControl.AsyncSSLClient.update_config_call updateConfigCall =
          mock(AgentControl.AsyncSSLClient.update_config_call.class);
      UpdateConfigResponse updateConfigResponse = new UpdateConfigResponse();
      updateConfigResponse.setResult(updateConfigResultCode);

      try {
        when(updateConfigCall.getResult()).thenReturn(updateConfigResponse);
      } catch (TException e) {
        throw new RuntimeException("Failed to mock updateConfigCall.getResult");
      }

      handler.onComplete(updateConfigCall);

    } else {
      throw new IllegalStateException("No result or failure specified for update config");
    }
  }

  @Override
  public void upgrade(AsyncMethodCallback<AgentControl.AsyncSSLClient.upgrade_call> handler) {

    logger.info("Host upgrade complete invocation");

    if (null != upgradeFailure) {
      handler.onError(upgradeFailure);

    } else if (null != upgradeResultCode) {
      AgentControl.AsyncSSLClient.upgrade_call upgradeCall = mock(AgentControl.AsyncSSLClient.upgrade_call.class);
      UpgradeResponse upgradeResponse = new UpgradeResponse();
      upgradeResponse.setResult(upgradeResultCode);

      try {
        when(upgradeCall.getResult()).thenReturn(upgradeResponse);
      } catch (TException e) {
        throw new RuntimeException("Failed to mock upgradeCall.getResult");
      }

      handler.onComplete(upgradeCall);

    } else {
      throw new IllegalStateException("No result or failure specified for upgrade");
    }
  }

  @Override
  public void getAgentStatus(AsyncMethodCallback<AgentControl.AsyncSSLClient.get_agent_status_call> handler) {

    logger.info("Agent get status complete invocation");

    if (null != getAgentStatusFailure) {
      handler.onError(getAgentStatusFailure);

    } else if (null != agentStatusCode) {
      AgentControl.AsyncSSLClient.get_agent_status_call getAgentStatusCall =
          mock(AgentControl.AsyncSSLClient.get_agent_status_call.class);
      AgentStatusResponse agentStatusResponse = new AgentStatusResponse();
      agentStatusResponse.setStatus(agentStatusCode);
      agentStatusResponse.setStatusIsSet(true);

      try {
        when(getAgentStatusCall.getResult()).thenReturn(agentStatusResponse);
      } catch (TException e) {
        throw new RuntimeException("Failed to mock getAgentStatusCall.getResult");
      }

      handler.onComplete(getAgentStatusCall);

    } else {
      throw new IllegalStateException("No result or failure specified for getAgentStatus");
    }
  }


  /**
   * This class implements a builder for {@link AgentControlClientMock} objects.
   */
  public static class Builder {

    private ProvisionResultCode provisionResultCode;
    private Exception provisionFailure;
    private UpdateConfigResultCode updateConfigResultCode;
    private Exception updateConfigFailure;
    private UpgradeResultCode upgradeResultCode;
    private Exception upgradeFailure;
    private AgentStatusCode agentStatusCode;
    private Exception getAgentStatusFailure;

    public Builder provisionResultCode(ProvisionResultCode provisionResultCode) {
      this.provisionResultCode = provisionResultCode;
      return this;
    }

    public Builder provisionFailure(Exception provisionFailure) {
      this.provisionFailure = provisionFailure;
      return this;
    }

    public Builder updateConfigResultCode(UpdateConfigResultCode updateConfigResultCode) {
      this.updateConfigResultCode = updateConfigResultCode;
      return this;
    }

    public Builder updateConfigFailure(Exception updateConfigFailure) {
      this.updateConfigFailure = updateConfigFailure;
      return this;
    }

    public Builder upgradeResultCode(UpgradeResultCode upgradeResultCode) {
      this.upgradeResultCode = upgradeResultCode;
      return this;
    }

    public Builder upgradeFailure(Exception upgradeFailure) {
      this.upgradeFailure = upgradeFailure;
      return this;
    }

    public Builder agentStatusCode(AgentStatusCode agentStatusCode) {
      this.agentStatusCode = agentStatusCode;
      return this;
    }

    public Builder getAgentStatusFailure(Exception getAgentStatusFailure) {
      this.getAgentStatusFailure = getAgentStatusFailure;
      return this;
    }

    public AgentControlClientMock build() {
      return new AgentControlClientMock(this);
    }
  }
}
