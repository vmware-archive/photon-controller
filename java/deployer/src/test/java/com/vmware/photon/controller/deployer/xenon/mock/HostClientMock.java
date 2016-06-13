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

import com.vmware.photon.controller.common.clients.HostClient;
import com.vmware.photon.controller.common.thrift.ClientPoolFactory;
import com.vmware.photon.controller.common.thrift.ClientProxyFactory;
import com.vmware.photon.controller.host.gen.GetConfigResponse;
import com.vmware.photon.controller.host.gen.GetConfigResultCode;
import com.vmware.photon.controller.host.gen.Host;
import com.vmware.photon.controller.host.gen.HostConfig;
import com.vmware.photon.controller.host.gen.HostMode;
import com.vmware.photon.controller.host.gen.SetHostModeResponse;
import com.vmware.photon.controller.host.gen.SetHostModeResultCode;

import org.apache.thrift.TException;
import org.apache.thrift.async.AsyncMethodCallback;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * This class implements a mock {@link HostClient} object for use in testing.
 */
public class HostClientMock extends HostClient {

  private static final Logger logger = LoggerFactory.getLogger(HostClientMock.class);

  private GetConfigResultCode getConfigResultCode;
  private HostConfig hostConfig;
  private Exception getConfigFailure;
  private Exception setHostModeFailure;
  private SetHostModeResultCode setHostModeResultCode;

  private HostClientMock(Builder builder) {
    super(mock(ClientProxyFactory.class), mock(ClientPoolFactory.class));
    this.getConfigResultCode = builder.getConfigResultCode;
    this.hostConfig = builder.hostConfig;
    this.getConfigFailure = builder.getConfigFailure;
    this.setHostModeFailure = builder.setHostModeFailure;
    this.setHostModeResultCode = builder.setHostModeResultCode;
  }

  @Override
  public void getHostConfig(AsyncMethodCallback<Host.AsyncClient.get_host_config_call> handler) {

    logger.info("Host get config complete invocation");

    if (null != getConfigFailure) {
      handler.onError(getConfigFailure);

    } else if (null != getConfigResultCode) {
      Host.AsyncClient.get_host_config_call getHostConfigCall = mock(Host.AsyncClient.get_host_config_call.class);
      GetConfigResponse getConfigResponse = new GetConfigResponse();
      getConfigResponse.setResult(getConfigResultCode);
      getConfigResponse.setHostConfig(hostConfig);

      try {
        when(getHostConfigCall.getResult()).thenReturn(getConfigResponse);
      } catch (TException e) {
        throw new RuntimeException("Failed to mock getHostConfigCall.getResult");
      }

      handler.onComplete(getHostConfigCall);

    } else {
      throw new IllegalStateException("No result or failure specified for getHostConfig");
    }
  }

  @Override
  public void setHostMode(HostMode hostMode, AsyncMethodCallback<Host.AsyncClient.set_host_mode_call> callback) {
    if (this.setHostModeFailure != null) {
      callback.onError(this.setHostModeFailure);
    } else if (this.setHostModeResultCode != null){
      Host.AsyncClient.set_host_mode_call call = mock(Host.AsyncClient.set_host_mode_call.class);
      SetHostModeResponse response = new SetHostModeResponse();
      response.setResult(this.setHostModeResultCode);
      try {
        when(call.getResult()).thenReturn(response);
      } catch (TException e) {
        throw new RuntimeException("Failed to mock setHostMode.getResult");
      }
      callback.onComplete(call);
    } else {
      throw new IllegalStateException("No result or failure specified for setHostMode");
    }
  }

  /**
   * This class implements a builder for {@link HostClientMock} objects.
   */
  public static class Builder {

    private GetConfigResultCode getConfigResultCode;
    private HostConfig hostConfig;
    private Exception getConfigFailure;
    private Exception setHostModeFailure;
    private SetHostModeResultCode setHostModeResultCode;

    public Builder getConfigResultCode(GetConfigResultCode getConfigResultCode) {
      this.getConfigResultCode = getConfigResultCode;
      return this;
    }

    public Builder hostConfig(HostConfig hostConfig) {
      this.hostConfig = hostConfig;
      return this;
    }

    public Builder getConfigFailure(Exception getConfigFailure) {
      this.getConfigFailure = getConfigFailure;
      return this;
    }

    public Builder setHostModeFailure(Exception exception) {
      this.setHostModeFailure = exception;
      return this;
    }

    public Builder setHostModeResultCode(SetHostModeResultCode setHostModeResultCode) {
      this.setHostModeResultCode = setHostModeResultCode;
      return this;
    }

    public HostClientMock build() {
      return new HostClientMock(this);
    }
  }
}
