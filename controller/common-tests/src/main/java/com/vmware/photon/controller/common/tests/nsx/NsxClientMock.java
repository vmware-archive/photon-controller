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

package com.vmware.photon.controller.common.tests.nsx;

import com.vmware.photon.controller.nsxclient.NsxClient;
import com.vmware.photon.controller.nsxclient.apis.DhcpServiceApi;
import com.vmware.photon.controller.nsxclient.apis.FabricApi;
import com.vmware.photon.controller.nsxclient.apis.LogicalRouterApi;
import com.vmware.photon.controller.nsxclient.apis.LogicalSwitchApi;
import com.vmware.photon.controller.nsxclient.datatypes.NsxRouter;
import com.vmware.photon.controller.nsxclient.datatypes.NsxSwitch;
import com.vmware.photon.controller.nsxclient.models.DhcpRelayProfile;
import com.vmware.photon.controller.nsxclient.models.DhcpRelayProfileCreateSpec;
import com.vmware.photon.controller.nsxclient.models.DhcpRelayService;
import com.vmware.photon.controller.nsxclient.models.DhcpRelayServiceCreateSpec;
import com.vmware.photon.controller.nsxclient.models.FabricNode;
import com.vmware.photon.controller.nsxclient.models.FabricNodeCreateSpec;
import com.vmware.photon.controller.nsxclient.models.FabricNodeState;
import com.vmware.photon.controller.nsxclient.models.LogicalPort;
import com.vmware.photon.controller.nsxclient.models.LogicalPortCreateSpec;
import com.vmware.photon.controller.nsxclient.models.LogicalRouter;
import com.vmware.photon.controller.nsxclient.models.LogicalRouterCreateSpec;
import com.vmware.photon.controller.nsxclient.models.LogicalRouterDownLinkPort;
import com.vmware.photon.controller.nsxclient.models.LogicalRouterDownLinkPortCreateSpec;
import com.vmware.photon.controller.nsxclient.models.LogicalRouterLinkPortOnTier0;
import com.vmware.photon.controller.nsxclient.models.LogicalRouterLinkPortOnTier0CreateSpec;
import com.vmware.photon.controller.nsxclient.models.LogicalRouterLinkPortOnTier1;
import com.vmware.photon.controller.nsxclient.models.LogicalRouterLinkPortOnTier1CreateSpec;
import com.vmware.photon.controller.nsxclient.models.LogicalRouterPort;
import com.vmware.photon.controller.nsxclient.models.LogicalRouterPortListResult;
import com.vmware.photon.controller.nsxclient.models.LogicalSwitch;
import com.vmware.photon.controller.nsxclient.models.LogicalSwitchCreateSpec;
import com.vmware.photon.controller.nsxclient.models.LogicalSwitchState;
import com.vmware.photon.controller.nsxclient.models.NatRule;
import com.vmware.photon.controller.nsxclient.models.NatRuleCreateSpec;
import com.vmware.photon.controller.nsxclient.models.ResourceReference;
import com.vmware.photon.controller.nsxclient.models.RoutingAdvertisement;
import com.vmware.photon.controller.nsxclient.models.RoutingAdvertisementUpdateSpec;
import com.vmware.photon.controller.nsxclient.models.TransportNode;
import com.vmware.photon.controller.nsxclient.models.TransportNodeCreateSpec;
import com.vmware.photon.controller.nsxclient.models.TransportNodeState;
import com.vmware.photon.controller.nsxclient.models.TransportZone;
import com.vmware.photon.controller.nsxclient.models.TransportZoneCreateSpec;

import com.google.common.util.concurrent.FutureCallback;
import org.apache.commons.lang3.ArrayUtils;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

/**
 * This class implements a mock {@link NsxClientMock} object for use in testing.
 */
public class NsxClientMock extends NsxClient {
  private FabricApi mockFabricApi;
  private LogicalSwitchApi mockLogicalSwitchApi;
  private LogicalRouterApi mockLogicalRouterApi;
  private DhcpServiceApi mockDhcpServiceApi;

  private NsxClientMock(FabricApi mockFabricApi,
                        LogicalSwitchApi mockLogicalSwitchApi,
                        LogicalRouterApi mockLogicalRouterApi,
                        DhcpServiceApi mockDhcpServiceApi) {
    super("1.2.3.4", "username", "password");
    this.mockFabricApi = mockFabricApi;
    this.mockLogicalSwitchApi = mockLogicalSwitchApi;
    this.mockLogicalRouterApi = mockLogicalRouterApi;
    this.mockDhcpServiceApi = mockDhcpServiceApi;
  }

  @Override
  public FabricApi getFabricApi() {
    return mockFabricApi;
  }

  @Override
  public LogicalSwitchApi getLogicalSwitchApi() {
    return mockLogicalSwitchApi;
  }

  @Override
  public LogicalRouterApi getLogicalRouterApi() {
    return mockLogicalRouterApi;
  }

  @Override
  public DhcpServiceApi getDhcpServiceApi() {
    return mockDhcpServiceApi;
  }

  @Override
  public int getCreateLogicalSwitchPollDelay() {
    return 10;
  }

  public int getDeleteLogicalSwitchPollDelay() {
    return 10;
  }

  public int getDeleteLogicalRouterPollDelay() {
    return 10;
  }

  public int getDeleteLogicalPortPollDelay() {
    return 10;
  }

  @Override
  public String getHostThumbprint(String ipAddress, int port) {
    return "hostThumbprint";
  }

  /**
   * This class implements a builder for {@link NsxClientMock} objects.
   */
  public static class Builder {
    private FabricApi mockFabricApi;
    private LogicalSwitchApi mockLogicalSwitchApi;
    private LogicalRouterApi mockLogicalRouterApi;
    private DhcpServiceApi mockDhcpServiceApi;

    /**
     * Port state used for test:checkLogicalRouterPortExistence.
     */
    private enum PortState {
      EXIST, NOTEXIST, ERROR
    }

    public Builder() {
      mockFabricApi = mock(FabricApi.class);
      mockLogicalSwitchApi = mock(LogicalSwitchApi.class);
      mockLogicalRouterApi = mock(LogicalRouterApi.class);
      mockDhcpServiceApi = mock(DhcpServiceApi.class);
    }

    public Builder registerFabricNode(boolean isSuccess,
                                      String fabricNodeId) throws Throwable {
      if (isSuccess) {
        FabricNode response = new FabricNode();
        response.setId(fabricNodeId);
        doAnswer(invocation -> {
          ((FutureCallback<FabricNode>) invocation.getArguments()[1])
              .onSuccess(response);
          return null;
        }).when(mockFabricApi).registerFabricNode(any(FabricNodeCreateSpec.class), any(FutureCallback.class));

        FabricNodeState stateResponse = new FabricNodeState();
        stateResponse.setState(com.vmware.photon.controller.nsxclient.datatypes.FabricNodeState.SUCCESS);
        doAnswer(invocation -> {
          ((FutureCallback<FabricNodeState>) invocation.getArguments()[1])
              .onSuccess(stateResponse);
          return null;
        }).when(mockFabricApi).getFabricNodeState(any(String.class), any(FutureCallback.class));
      } else {
        RuntimeException error = new RuntimeException("registerFabricNode failed");
        doAnswer(invocation -> {
          ((FutureCallback<FabricNode>) invocation.getArguments()[1])
              .onFailure(error);
          return null;
        }).when(mockFabricApi).registerFabricNode(any(FabricNodeCreateSpec.class),
            any(FutureCallback.class));
      }

      return this;
    }

    public Builder createTransportNode(boolean isSuccess,
                                       String transportNodeId) throws Throwable {
      if (isSuccess) {
        TransportNode response = new TransportNode();
        response.setId(transportNodeId);
        doAnswer(invocation -> {
          ((FutureCallback<TransportNode>) invocation.getArguments()[1])
              .onSuccess(response);
          return null;
        }).when(mockFabricApi).createTransportNode(any(TransportNodeCreateSpec.class), any(FutureCallback.class));

        TransportNodeState stateResponse = new TransportNodeState();
        stateResponse.setState(com.vmware.photon.controller.nsxclient.datatypes.TransportNodeState.SUCCESS);
        doAnswer(invocation -> {
          ((FutureCallback<TransportNodeState>) invocation.getArguments()[1])
              .onSuccess(stateResponse);
          return null;
        }).when(mockFabricApi).getTransportNodeState(any(String.class), any(FutureCallback.class));
      } else {
        RuntimeException error = new RuntimeException("createTransportNode failed");
        doAnswer(invocation -> {
          ((FutureCallback<TransportNode>) invocation.getArguments()[1])
              .onFailure(error);
          return null;
        }).when(mockFabricApi).createTransportNode(any(TransportNodeCreateSpec.class),
            any(FutureCallback.class));
      }

      return this;
    }

    public Builder createTransportZone(boolean isSuccess,
                                       String transportZoneId) throws Throwable {
      if (isSuccess) {
        TransportZone response = new TransportZone();
        response.setId(transportZoneId);
        doAnswer(invocation -> {
          ((FutureCallback<TransportZone>) invocation.getArguments()[1])
              .onSuccess(response);
          return null;
        }).when(mockFabricApi).createTransportZone(any(TransportZoneCreateSpec.class), any(FutureCallback.class));
      } else {
        RuntimeException error = new RuntimeException("createTransportZone failed");
        doAnswer(invocation -> {
          ((FutureCallback<TransportZone>) invocation.getArguments()[1])
              .onFailure(error);
          return null;
        }).when(mockFabricApi).createTransportZone(any(TransportZoneCreateSpec.class),
            any(FutureCallback.class));
      }

      return this;
    }

    public Builder unregisterFabricNode(boolean isSuccess) throws Throwable {
      if (isSuccess) {
        doAnswer(invocation -> {
          ((FutureCallback<Void>) invocation.getArguments()[1])
              .onSuccess(null);
          return null;
        }).when(mockFabricApi).unregisterFabricNode(any(String.class), any(FutureCallback.class));
      } else {
        RuntimeException error = new RuntimeException("unregisterFabricNode failed");
        doAnswer(invocation -> {
          ((FutureCallback<Void>) invocation.getArguments()[1])
              .onFailure(error);
          return null;
        }).when(mockFabricApi).unregisterFabricNode(any(String.class), any(FutureCallback.class));
      }

      return this;
    }

    public Builder deleteTransportNode(boolean isSuccess) throws Throwable {
      if (isSuccess) {
        doAnswer(invocation -> {
          ((FutureCallback<Void>) invocation.getArguments()[1])
              .onSuccess(null);
          return null;
        }).when(mockFabricApi).deleteTransportNode(any(String.class), any(FutureCallback.class));
      } else {
        RuntimeException error = new RuntimeException("deleteTransportNode failed");
        doAnswer(invocation -> {
          ((FutureCallback<Void>) invocation.getArguments()[1])
              .onFailure(error);
          return null;
        }).when(mockFabricApi).deleteTransportNode(any(String.class), any(FutureCallback.class));
      }

      return this;
    }

    public Builder getTransportZone(boolean isSuccess, String transportZoneId, String hostSwitchName) throws Throwable {
      if (isSuccess) {
        TransportZone transportZone = new TransportZone();
        transportZone.setId(transportZoneId);
        transportZone.setHostSwitchName(hostSwitchName);

        doAnswer(invocation -> {
          ((FutureCallback<TransportZone>) invocation.getArguments()[1]).onSuccess(transportZone);
          return null;
        }).when(mockFabricApi).getTransportZone(anyString(), any(FutureCallback.class));
      } else {
        RuntimeException error = new RuntimeException("getTransportZone failed");
        doAnswer(invocation -> {
          ((FutureCallback<TransportZone>) invocation.getArguments()[1]).onFailure(error);
          return null;
        }).when(mockFabricApi).getTransportZone(anyString(), any(FutureCallback.class));
      }

      return this;
    }

    public Builder deleteTransportZone(boolean isSuccess) throws Throwable {
      if (isSuccess) {
        doAnswer(invocation -> {
          ((FutureCallback<Void>) invocation.getArguments()[1])
              .onSuccess(null);
          return null;
        }).when(mockFabricApi).deleteTransportZone(any(String.class), any(FutureCallback.class));
      } else {
        RuntimeException error = new RuntimeException("deleteTransportZone failed");
        doAnswer(invocation -> {
          ((FutureCallback<Void>) invocation.getArguments()[1])
              .onFailure(error);
          return null;
        }).when(mockFabricApi).deleteTransportZone(any(String.class), any(FutureCallback.class));
      }

      return this;
    }

    public Builder createLogicalRouter(boolean isSuccess,
                                       String routerId) throws Throwable {
      if (isSuccess) {
        LogicalRouter response = new LogicalRouter();
        response.setId(routerId);
        doAnswer(invocation -> {
          ((FutureCallback<LogicalRouter>) invocation.getArguments()[1])
              .onSuccess(response);
          return null;
        }).when(mockLogicalRouterApi).createLogicalRouter(any(LogicalRouterCreateSpec.class),
            any(FutureCallback.class));
      } else {
        RuntimeException error = new RuntimeException("createLogicalRouter failed");
        doAnswer(invocation -> {
          ((FutureCallback<TransportZone>) invocation.getArguments()[1])
              .onFailure(error);
          return null;
        }).when(mockLogicalRouterApi).createLogicalRouter(any(LogicalRouterCreateSpec.class),
            any(FutureCallback.class));
      }

      return this;
    }

    public Builder deleteLogicalRouter(boolean isSuccess) throws Throwable {
      if (isSuccess) {
        doAnswer(invocation -> {
          ((FutureCallback<Void>) invocation.getArguments()[1])
              .onSuccess(null);
          return null;
        }).when(mockLogicalRouterApi).deleteLogicalRouter(any(String.class), any(FutureCallback.class));
      } else {
        RuntimeException error = new RuntimeException("deleteLogicalRouter failed");
        doAnswer(invocation -> {
          ((FutureCallback<Void>) invocation.getArguments()[1])
              .onFailure(error);
          return null;
        }).when(mockLogicalRouterApi).deleteLogicalRouter(any(String.class), any(FutureCallback.class));
      }

      return this;
    }

    public Builder checkLogicalRouterExistence(boolean isSuccess) throws Throwable {
      if (isSuccess) {
        doAnswer(invocation -> {
          ((FutureCallback<Boolean>) invocation.getArguments()[1])
              .onSuccess(true);
          return null;
        }).doAnswer(invocation -> {
          ((FutureCallback<Boolean>) invocation.getArguments()[1])
              .onSuccess(false);
          return null;
        }).when(mockLogicalRouterApi).checkLogicalRouterExistence(any(String.class), any(FutureCallback.class));
      } else {
        RuntimeException error = new RuntimeException("checkLogicalRouterExistence failed");
        doAnswer(invocation -> {
          ((FutureCallback<Boolean>) invocation.getArguments()[1])
              .onFailure(error);
          return null;
        }).when(mockLogicalRouterApi).checkLogicalRouterExistence(any(String.class), any(FutureCallback.class));
      }

      return this;
    }

    public Builder createLogicalSwitch(boolean isSuccess,
                                       String logicalSwitchId) throws Throwable {
      if (isSuccess) {
        LogicalSwitch response = new LogicalSwitch();
        response.setId(logicalSwitchId);
        doAnswer(invocation -> {
          ((FutureCallback<LogicalSwitch>) invocation.getArguments()[1])
              .onSuccess(response);
          return null;
        }).when(mockLogicalSwitchApi).createLogicalSwitch(any(LogicalSwitchCreateSpec.class),
            any(FutureCallback.class));
      } else {
        RuntimeException error = new RuntimeException("createLogicalSwitch failed");
        doAnswer(invocation -> {
          ((FutureCallback<LogicalSwitch>) invocation.getArguments()[1])
              .onFailure(error);
          return null;
        }).when(mockLogicalSwitchApi).createLogicalSwitch(any(LogicalSwitchCreateSpec.class),
            any(FutureCallback.class));
      }

      return this;
    }

    public Builder getLogicalSwitchState(boolean isSuccess,
                                         String logicalSwitchId) throws Throwable {
      if (isSuccess) {
        LogicalSwitchState inProgressState = new LogicalSwitchState();
        inProgressState.setState(NsxSwitch.State.IN_PROGRESS);
        inProgressState.setId(logicalSwitchId);

        LogicalSwitchState successState = new LogicalSwitchState();
        successState.setState(NsxSwitch.State.SUCCESS);
        successState.setId(logicalSwitchId);

        doAnswer(invocation -> {
          ((FutureCallback<LogicalSwitchState>) invocation.getArguments()[1])
              .onSuccess(inProgressState);
          return null;
        }).doAnswer(invocation -> {
          ((FutureCallback<LogicalSwitchState>) invocation.getArguments()[1])
              .onSuccess(successState);
          return null;
        }).when(mockLogicalSwitchApi).getLogicalSwitchState(any(String.class),
            any(FutureCallback.class));
      } else {
        RuntimeException error = new RuntimeException("getLogicalSwitchState failed");
        doAnswer(invocation -> {
          ((FutureCallback<LogicalSwitchState>) invocation.getArguments()[1])
              .onFailure(error);
          return null;
        }).when(mockLogicalSwitchApi).getLogicalSwitchState(any(String.class),
            any(FutureCallback.class));
      }

      return this;
    }

    public Builder deleteLogicalSwitch(boolean isSuccess) throws Throwable {
      if (isSuccess) {
        doAnswer(invocation -> {
          ((FutureCallback<Void>) invocation.getArguments()[1])
              .onSuccess(null);
          return null;
        }).when(mockLogicalSwitchApi).deleteLogicalSwitch(any(String.class), any(FutureCallback.class));
      } else {
        RuntimeException error = new RuntimeException("deleteLogicalSwitch failed");
        doAnswer(invocation -> {
          ((FutureCallback<Void>) invocation.getArguments()[1])
              .onFailure(error);
          return null;
        }).when(mockLogicalSwitchApi).deleteLogicalSwitch(any(String.class), any(FutureCallback.class));
      }

      return this;
    }

    public Builder checkLogicalSwitchExistence(boolean isSuccess) throws Throwable {
      if (isSuccess) {
        doAnswer(invocation -> {
          ((FutureCallback<Boolean>) invocation.getArguments()[1]).onSuccess(true);
          return null;
        }).doAnswer(invocation -> {
          ((FutureCallback<Boolean>) invocation.getArguments()[1]).onSuccess(false);
          return null;
        }).when(mockLogicalSwitchApi).checkLogicalSwitchExistence(anyString(), any(FutureCallback.class));
      } else {
        RuntimeException e = new RuntimeException("checkLogicalSwitchExistence failed");
        doAnswer(invocation -> {
          ((FutureCallback<Boolean>) invocation.getArguments()[1]).onFailure(e);
          return null;
        }).when(mockLogicalSwitchApi).checkLogicalSwitchExistence(anyString(), any(FutureCallback.class));
      }

      return this;
    }

    public Builder createLogicalPort(boolean isSuccess,
                                     String logicalPortId) throws Throwable {
      if (isSuccess) {
        LogicalPort logicalPort = new LogicalPort();
        logicalPort.setId(logicalPortId);

        doAnswer(invocation -> {
          ((FutureCallback<LogicalPort>) invocation.getArguments()[1])
              .onSuccess(logicalPort);
          return null;
        }).when(mockLogicalSwitchApi).createLogicalPort(any(LogicalPortCreateSpec.class),
            any(FutureCallback.class));
      } else {
        RuntimeException error = new RuntimeException("createLogicalPort failed");
        doAnswer(invocation -> {
          ((FutureCallback<LogicalPort>) invocation.getArguments()[1])
              .onFailure(error);
          return null;
        }).when(mockLogicalSwitchApi).createLogicalPort(any(LogicalPortCreateSpec.class),
            any(FutureCallback.class));
      }

      return this;
    }

    public Builder createLogicalRouterDownLinkPort(boolean isSuccess,
                                                   String logicalRouterPortId) throws Throwable {

      if (isSuccess) {
        LogicalRouterDownLinkPort logicalRouterDownLinkPort = new LogicalRouterDownLinkPort();
        logicalRouterDownLinkPort.setId(logicalRouterPortId);

        doAnswer(invocation -> {
          ((FutureCallback<LogicalRouterDownLinkPort>) invocation.getArguments()[1])
              .onSuccess(logicalRouterDownLinkPort);
          return null;
        }).when(mockLogicalRouterApi).createLogicalRouterDownLinkPort(any(LogicalRouterDownLinkPortCreateSpec.class),
            any(FutureCallback.class));
      } else {
        RuntimeException error = new RuntimeException("createLogicalRouterDownLinkPort failed");
        doAnswer(invocation -> {
          ((FutureCallback<LogicalRouterDownLinkPort>) invocation.getArguments()[1])
              .onFailure(error);
          return null;
        }).when(mockLogicalRouterApi).createLogicalRouterDownLinkPort(any(LogicalRouterDownLinkPortCreateSpec.class),
            any(FutureCallback.class));
      }

      return this;
    }

    public Builder createLogicalLinkPortOnTier0Router(boolean isSuccess,
                                                      String logicalRouterPortId) throws Throwable {

      if (isSuccess) {
        LogicalRouterLinkPortOnTier0 logicalRouterLinkPortOnTier0 = new LogicalRouterLinkPortOnTier0();
        logicalRouterLinkPortOnTier0.setId(logicalRouterPortId);

        doAnswer(invocation -> {
          ((FutureCallback<LogicalRouterLinkPortOnTier0>) invocation.getArguments()[1])
              .onSuccess(logicalRouterLinkPortOnTier0);
          return null;
        }).when(mockLogicalRouterApi).createLogicalRouterLinkPortTier0(
            any(LogicalRouterLinkPortOnTier0CreateSpec.class), any(FutureCallback.class));
      } else {
        RuntimeException error = new RuntimeException("createLogicalLinkPortOnTier0Router failed");
        doAnswer(invocation -> {
          ((FutureCallback<LogicalRouterLinkPortOnTier0>) invocation.getArguments()[1])
              .onFailure(error);
          return null;
        }).when(mockLogicalRouterApi).createLogicalRouterLinkPortTier0(
            any(LogicalRouterLinkPortOnTier0CreateSpec.class), any(FutureCallback.class));
      }

      return this;
    }

    public Builder createLogicalLinkPortOnTier1Router(boolean isSuccess,
                                                      String logicalRouterPortId) throws Throwable {

      if (isSuccess) {
        LogicalRouterLinkPortOnTier1 logicalRouterLinkPortOnTier1 = new LogicalRouterLinkPortOnTier1();
        logicalRouterLinkPortOnTier1.setId(logicalRouterPortId);

        doAnswer(invocation -> {
          ((FutureCallback<LogicalRouterLinkPortOnTier1>) invocation.getArguments()[1])
              .onSuccess(logicalRouterLinkPortOnTier1);
          return null;
        }).when(mockLogicalRouterApi).createLogicalRouterLinkPortTier1(
            any(LogicalRouterLinkPortOnTier1CreateSpec.class), any(FutureCallback.class));
      } else {
        RuntimeException error = new RuntimeException("createLogicalLinkPortOnTier1Router failed");
        doAnswer(invocation -> {
          ((FutureCallback<LogicalRouterLinkPortOnTier1>) invocation.getArguments()[1])
              .onFailure(error);
          return null;
        }).when(mockLogicalRouterApi).createLogicalRouterLinkPortTier1(
            any(LogicalRouterLinkPortOnTier1CreateSpec.class), any(FutureCallback.class));
      }

      return this;
    }

    public Builder listLogicalRouterPorts(boolean isSuccess) throws Throwable {
      if (isSuccess) {
        LogicalRouterPortListResult logicalRouterPortListResult = createLogicalRouterPortListResult();

        doAnswer(invocation -> {
          ((FutureCallback<LogicalRouterPortListResult>) invocation.getArguments()[1])
              .onSuccess(logicalRouterPortListResult);
          return null;
        }).when(mockLogicalRouterApi).listLogicalRouterPorts(any(String.class), any(FutureCallback.class));
      } else {
        RuntimeException error = new RuntimeException("listLogicalRouterPorts failed");
        doAnswer(invocation -> {
          ((FutureCallback<LogicalRouterPortListResult>) invocation.getArguments()[1])
              .onFailure(error);
          return null;
        }).when(mockLogicalRouterApi).listLogicalRouterPorts(any(String.class), any(FutureCallback.class));
      }

      return this;
    }

    public Builder deleteLogicalRouterPort(boolean... states) throws Throwable {
      Boolean[] statesBoxed = ArrayUtils.toObject(states);
      final Queue<Boolean> statesList = new LinkedList<>(Arrays.asList(statesBoxed));

      doAnswer(invocation -> {
        if (!statesList.isEmpty()) {
          if (statesList.size() == 1) {
            statesList.add(statesList.peek());
          }
          if (statesList.poll()) {
            ((FutureCallback<Void>) invocation.getArguments()[1]).onSuccess(null);
          } else {
            RuntimeException error = new RuntimeException("deleteLogicalRouterPort failed");
            ((FutureCallback<Void>) invocation.getArguments()[1]).onFailure(error);
          }
        }
        return null;
      }).when(mockLogicalRouterApi).deleteLogicalRouterPort(any(String.class), any(FutureCallback.class));

      return this;
    }

    public Builder deleteLogicalPort(boolean isSuccess) throws Throwable {

      if (isSuccess) {
        doAnswer(invocation -> {
          ((FutureCallback<Void>) invocation.getArguments()[1])
              .onSuccess(null);
          return null;
        }).when(mockLogicalSwitchApi).deleteLogicalPort(any(String.class), any(FutureCallback.class));
      } else {
        RuntimeException error = new RuntimeException("deleteLogicalPort failed");
        doAnswer(invocation -> {
          ((FutureCallback<Void>) invocation.getArguments()[1])
              .onFailure(error);
          return null;
        }).when(mockLogicalSwitchApi).deleteLogicalPort(any(String.class), any(FutureCallback.class));
      }

      return this;
    }

    public Builder deleteLogicalPort(boolean isSuccess, boolean forceDetach) throws Throwable {

      if (isSuccess) {
        doAnswer(invocation -> {
          ((FutureCallback<Void>) invocation.getArguments()[1])
              .onSuccess(null);
          return null;
        }).when(mockLogicalSwitchApi).deleteLogicalPort(any(String.class), any(FutureCallback.class), eq(forceDetach));
      } else {
        RuntimeException error = new RuntimeException("deleteLogicalPort failed");
        doAnswer(invocation -> {
          ((FutureCallback<Void>) invocation.getArguments()[1])
              .onFailure(error);
          return null;
        }).when(mockLogicalSwitchApi).deleteLogicalPort(any(String.class), any(FutureCallback.class), eq(forceDetach));
      }

      return this;
    }

    public Builder checkLogicalRouterPortExistence(boolean... states) throws Throwable {
      final Queue<PortState> statesList = new LinkedList<>();
      for (boolean isSuccess : states) {
        if (isSuccess) {
          statesList.add(PortState.EXIST);
          statesList.add(PortState.NOTEXIST);
        } else {
          statesList.add(PortState.ERROR);
        }
      }

      doAnswer(invocation -> {
        if (!statesList.isEmpty()) {
          if (statesList.size() == 1) {
            statesList.add(statesList.peek());
          }
          switch (statesList.poll()) {
            case EXIST:
              ((FutureCallback<Boolean>) invocation.getArguments()[1]).onSuccess(true);
              break;

            case NOTEXIST:
              ((FutureCallback<Boolean>) invocation.getArguments()[1]).onSuccess(false);
              break;

            case ERROR:
              RuntimeException e = new RuntimeException("checkLogicalRouterPortExistence failed");
              ((FutureCallback<Boolean>) invocation.getArguments()[1]).onFailure(e);
          }
        }
        return null;
      }).when(mockLogicalRouterApi).checkLogicalRouterPortExistence(anyString(), any(FutureCallback.class));

      return this;
    }

    public Builder checkLogicalSwitchPortExistence(boolean isSuccess) throws Throwable {
      if (isSuccess) {
        doAnswer(invocation -> {
          ((FutureCallback<Boolean>) invocation.getArguments()[1]).onSuccess(true);
          return null;
        }).doAnswer(invocation -> {
          ((FutureCallback<Boolean>) invocation.getArguments()[1]).onSuccess(false);
          return null;
        }).when(mockLogicalSwitchApi).checkLogicalSwitchPortExistence(anyString(), any(FutureCallback.class));
      } else {
        RuntimeException e = new RuntimeException("checkLogicalSwitchPortExistence failed");
        doAnswer(invocation -> {
          ((FutureCallback<Boolean>) invocation.getArguments()[1]).onFailure(e);
           return null;
        }).when(mockLogicalSwitchApi).checkLogicalSwitchPortExistence(anyString(), any(FutureCallback.class));
      }

      return this;
    }

    public Builder createDhcpRelayProfile(boolean isSuccess,
                                          String dhcpRelayProfileId) throws Throwable {
      if (isSuccess) {
        DhcpRelayProfile response = new DhcpRelayProfile();
        response.setId(dhcpRelayProfileId);

        doAnswer(invocation -> {
          ((FutureCallback<DhcpRelayProfile>) invocation.getArguments()[1])
              .onSuccess(response);
          return null;
        }).when(mockDhcpServiceApi).createDhcpRelayProfile(any(DhcpRelayProfileCreateSpec.class),
            any(FutureCallback.class));
      } else {
        RuntimeException error = new RuntimeException("createDhcpRelayProfile failed");
        doAnswer(invocation -> {
          ((FutureCallback<DhcpRelayProfile>) invocation.getArguments()[1])
              .onFailure(error);
          return null;
        }).when(mockDhcpServiceApi).createDhcpRelayProfile(any(DhcpRelayProfileCreateSpec.class),
            any(FutureCallback.class));
      }

      return this;
    }

    public Builder createDhcpRelayService(boolean isSuccess,
                                          String dhcpRelayServiceId) throws Throwable {
      if (isSuccess) {
        DhcpRelayService response = new DhcpRelayService();
        response.setId(dhcpRelayServiceId);

        doAnswer(invocation -> {
          ((FutureCallback<DhcpRelayService>) invocation.getArguments()[1])
              .onSuccess(response);
          return null;
        }).when(mockDhcpServiceApi).createDhcpRelayService(any(DhcpRelayServiceCreateSpec.class),
            any(FutureCallback.class));
      } else {
        RuntimeException error = new RuntimeException("createDhcpRelayService failed");
        doAnswer(invocation -> {
          ((FutureCallback<DhcpRelayService>) invocation.getArguments()[1])
              .onFailure(error);
          return null;
        }).when(mockDhcpServiceApi).createDhcpRelayService(any(DhcpRelayServiceCreateSpec.class),
            any(FutureCallback.class));
      }

      return this;
    }

    public Builder createNatRule(boolean isSuccess,
                                 String natRuleId) throws Throwable {
      if (isSuccess) {
        NatRule response = new NatRule();
        response.setId(natRuleId);

        doAnswer(invocation -> {
          ((FutureCallback<NatRule>) invocation.getArguments()[2])
              .onSuccess(response);
          return null;
        }).when(mockLogicalRouterApi).createNatRule(anyString(), any(NatRuleCreateSpec.class),
            any(FutureCallback.class));
      } else {
        RuntimeException error = new RuntimeException("createNatRule failed");
        doAnswer(invocation -> {
          ((FutureCallback<NatRule>) invocation.getArguments()[2])
              .onFailure(error);
          return null;
        }).when(mockLogicalRouterApi).createNatRule(anyString(), any(NatRuleCreateSpec.class),
            any(FutureCallback.class));
      }

      return this;
    }

    public Builder deleteNatRule(boolean isSuccess) throws Throwable {

      if (isSuccess) {
        doAnswer(invocation -> {
          ((FutureCallback<Void>) invocation.getArguments()[2])
              .onSuccess(null);
          return null;
        }).when(mockLogicalRouterApi).deleteNatRule(anyString(), anyString(), any(FutureCallback.class));
      } else {
        RuntimeException error = new RuntimeException("deleteNatRule failed");
        doAnswer(invocation -> {
          ((FutureCallback<Void>) invocation.getArguments()[2])
              .onFailure(error);
          return null;
        }).when(mockLogicalRouterApi).deleteNatRule(anyString(), anyString(), any(FutureCallback.class));
      }

      return this;
    }

    public Builder getRoutingAdvertisement(boolean isSuccess) throws Throwable {
      if (isSuccess) {
        RoutingAdvertisement routingAdvertisement = new RoutingAdvertisement();
        routingAdvertisement.setRevision(1);

        doAnswer(invocation -> {
          ((FutureCallback<RoutingAdvertisement>) invocation.getArguments()[1])
              .onSuccess(routingAdvertisement);
          return null;
        }).when(mockLogicalRouterApi).getRoutingAdvertisement(anyString(), any(FutureCallback.class));
      } else {
        RuntimeException error = new RuntimeException("getRoutingAdvertisement failed");
        doAnswer(invocation -> {
          ((FutureCallback<RoutingAdvertisement>) invocation.getArguments()[1])
              .onFailure(error);
          return null;
        }).when(mockLogicalRouterApi).getRoutingAdvertisement(anyString(), any(FutureCallback.class));
      }

      return this;
    }

    public Builder configureRoutingAdvertisement(boolean isSuccess) throws Throwable {
      if (isSuccess) {
        RoutingAdvertisement routingAdvertisement = new RoutingAdvertisement();

        doAnswer(invocation -> {
          ((FutureCallback<RoutingAdvertisement>) invocation.getArguments()[2])
              .onSuccess(routingAdvertisement);
          return null;
        }).when(mockLogicalRouterApi).configureRoutingAdvertisement(anyString(),
            any(RoutingAdvertisementUpdateSpec.class),
            any(FutureCallback.class));
      } else {
        RuntimeException error = new RuntimeException("configureRoutingAdvertisement failed");
        doAnswer(invocation -> {
          ((FutureCallback<RoutingAdvertisement>) invocation.getArguments()[2])
              .onFailure(error);
          return null;
        }).when(mockLogicalRouterApi).configureRoutingAdvertisement(anyString(),
            any(RoutingAdvertisementUpdateSpec.class),
            any(FutureCallback.class));
      }

      return this;
    }

    public NsxClientMock build() {
      return new NsxClientMock(mockFabricApi, mockLogicalSwitchApi, mockLogicalRouterApi, mockDhcpServiceApi);
    }
  }

  public static LogicalRouterPortListResult createLogicalRouterPortListResult() {
    ResourceReference linkedLogicalRouterPortId = new ResourceReference();
    linkedLogicalRouterPortId.setTargetType(NsxRouter.PortType.LINK_PORT_ON_TIER0.getValue());
    linkedLogicalRouterPortId.setTargetId("logical_link_port_on_tier0_router");

    LogicalRouterPort logicalRouterToRouterPort = new LogicalRouterPort();
    logicalRouterToRouterPort.setLogicalRouterId("tier1_router_id");
    logicalRouterToRouterPort.setId("logical_link_port_on_tier1_router_id");
    logicalRouterToRouterPort.setResourceType(NsxRouter.PortType.LINK_PORT_ON_TIER1);
    logicalRouterToRouterPort.setLinkedLogicalRouterPortId(linkedLogicalRouterPortId);

    ResourceReference linkedLogicalSwitchPortId = new ResourceReference();
    linkedLogicalSwitchPortId.setTargetType("LogicalPort");
    linkedLogicalSwitchPortId.setTargetId("logical_port_on_switch_id");

    LogicalRouterPort logicalRouterToSwitchPort = new LogicalRouterPort();
    logicalRouterToSwitchPort.setLogicalRouterId("tier1_router_id");
    logicalRouterToSwitchPort.setId("logical_down_link_port_on_tier1_router_id");
    logicalRouterToSwitchPort.setResourceType(NsxRouter.PortType.DOWN_LINK_PORT);
    logicalRouterToSwitchPort.setLinkedLogicalSwitchPortId(linkedLogicalSwitchPortId);

    List<LogicalRouterPort> logicalRouterPorts = new ArrayList<>();
    logicalRouterPorts.add(logicalRouterToRouterPort);
    logicalRouterPorts.add(logicalRouterToSwitchPort);

    LogicalRouterPortListResult logicalRouterPortListResult = new LogicalRouterPortListResult();
    logicalRouterPortListResult.setResultCount(2);
    logicalRouterPortListResult.setLogicalRouterPorts(logicalRouterPorts);
    return logicalRouterPortListResult;
  }
}
