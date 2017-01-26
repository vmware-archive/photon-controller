/*
 * Copyright 2017 VMware, Inc. All Rights Reserved.
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

package com.vmware.photon.controller.api.frontend.commands.steps;

import com.vmware.photon.controller.api.frontend.backends.DeploymentBackend;
import com.vmware.photon.controller.api.frontend.backends.HostBackend;
import com.vmware.photon.controller.api.frontend.backends.StepBackend;
import com.vmware.photon.controller.api.frontend.commands.tasks.TaskCommand;
import com.vmware.photon.controller.api.frontend.entities.DeploymentEntity;
import com.vmware.photon.controller.api.frontend.entities.HostEntity;
import com.vmware.photon.controller.api.frontend.entities.StepEntity;
import com.vmware.photon.controller.api.frontend.exceptions.ApiFeException;
import com.vmware.photon.controller.api.frontend.exceptions.external.HostRegisterNsxException;
import com.vmware.photon.controller.common.Constants;
import com.vmware.photon.controller.common.clients.exceptions.RpcException;
import com.vmware.photon.controller.nsxclient.NsxClient;
import com.vmware.photon.controller.nsxclient.models.FabricNode;
import com.vmware.photon.controller.nsxclient.models.FabricNodeCreateSpec;
import com.vmware.photon.controller.nsxclient.models.FabricNodeState;
import com.vmware.photon.controller.nsxclient.models.HostNodeLoginCredential;
import com.vmware.photon.controller.nsxclient.models.HostSwitch;
import com.vmware.photon.controller.nsxclient.models.PhysicalNic;
import com.vmware.photon.controller.nsxclient.models.TransportNode;
import com.vmware.photon.controller.nsxclient.models.TransportNodeCreateSpec;
import com.vmware.photon.controller.nsxclient.models.TransportNodeState;
import com.vmware.photon.controller.nsxclient.models.TransportZone;
import com.vmware.photon.controller.nsxclient.models.TransportZoneEndPoint;
import com.vmware.photon.controller.nsxclient.utils.NameUtils;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.util.concurrent.FutureCallback;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;

/**
 * StepCommand for configuring the host with NSX.
 */
public class HostConfigureNsxStepCmd extends StepCommand {

  private static final Logger logger = LoggerFactory.getLogger(HostProvisionStepCmd.class);

  public static final String DEPLOYMENT_ID_RESOURCE_KEY = "deployment-id";
  public static final String HOST_PASSWORD_RESOURCE_KEY = "host-password";

  private static final long NSX_WAIT_TIME_SEC = 5L;

  private final HostBackend hostBackend;
  private final DeploymentBackend deploymentBackend;

  public HostConfigureNsxStepCmd(
      TaskCommand taskCommand,
      StepBackend stepBackend,
      StepEntity stepEntity,
      HostBackend hostBackend,
      DeploymentBackend deploymentBackend) {
    super(taskCommand, stepBackend, stepEntity);
    this.hostBackend = hostBackend;
    this.deploymentBackend = deploymentBackend;
  }

  @Override
  protected void execute() throws ApiFeException, InterruptedException, RpcException {
    String deploymentId = getTransientResource(step, DEPLOYMENT_ID_RESOURCE_KEY);
    String hostPassword = getTransientResource(step, HOST_PASSWORD_RESOURCE_KEY);
    List<HostEntity> hostEntities = step.getTransientResourceEntities(HostEntity.KIND);
    checkState(hostEntities.size() == 1);
    HostEntity hostEntity = hostEntities.iterator().next();

    DeploymentEntity deploymentEntity = deploymentBackend.findById(deploymentId);

    logger.info("Configuring host " + hostEntity.getId() + " with NSX");
    String fabricNodeId = registerHostAsFabricNode(deploymentEntity, hostEntity, hostPassword);
    waitForFabricNodeReady(deploymentEntity, fabricNodeId);
    String hostSwitchName = getHostSwitchName(deploymentEntity);
    String transportNodeId = configureHostAsTransportNode(deploymentEntity, hostEntity, fabricNodeId, hostSwitchName);
    waitForTransportNodeReady(deploymentEntity, transportNodeId);
    logger.info("Host " + hostEntity.getAddress() + " is successfully configured with NSX");

    logger.info("Updating host " + hostEntity.getAddress() + "'s NSX configuration");
    hostBackend.updateNsxConfiguration(hostEntity, fabricNodeId, transportNodeId);
  }

  @Override
  protected void cleanup() {
  }

  private static <T> T getTransientResource(StepEntity step, String key) {
    T value = (T) step.getTransientResource(key);
    checkNotNull(value, key + " is not defined in TransientResource");
    return value;
  }

  private String registerHostAsFabricNode(
      DeploymentEntity deploymentEntity,
      HostEntity hostEntity,
      String hostPassword) throws HostRegisterNsxException, InterruptedException {

    logger.info("Registering host " + hostEntity.getAddress() + " as fabric node");
    FabricNode fabricNode = callNsxApi(deploymentEntity,
        (nsxClient, callback) -> {
          try {
            HostNodeLoginCredential hostNodeLoginCredential = new HostNodeLoginCredential();
            hostNodeLoginCredential.setUsername(hostEntity.getUsername());
            hostNodeLoginCredential.setPassword(hostPassword);
            hostNodeLoginCredential.setThumbprint(nsxClient.getHostThumbprint(hostEntity.getUsername(),
                Constants.ESXI_PORT));

            FabricNodeCreateSpec request = new FabricNodeCreateSpec();
            request.setDisplayName(NameUtils.getFabricNodeName(hostEntity.getAddress()));
            request.setDescription(NameUtils.getFabricNodeDescription(hostEntity.getAddress()));
            request.setIpAddresses(Collections.singletonList(hostEntity.getAddress()));
            request.setOsType("ESXI");
            request.setResourceType("HostNode");
            request.setHostCredential(hostNodeLoginCredential);

            ObjectMapper om = new ObjectMapper();
            om.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
            String payload = om.writeValueAsString(request);
            logger.info("Sending FabricNodeCreateSpec to NSX: " + payload);

            nsxClient.getFabricApi().registerFabricNode(request, callback);
            return null;
          } catch (Throwable t) {
            return t;
          }
        });
    logger.info("Host is successfully registered as fabric node " + fabricNode.getId());

    return fabricNode.getId();
  }

  private void waitForFabricNodeReady(
      DeploymentEntity deploymentEntity,
      String fabricNodeId) throws HostRegisterNsxException, InterruptedException {

    logger.info("Waiting for fabric node " + fabricNodeId + " to become READY");
    while (true) {
      FabricNodeState fabricNodeState = callNsxApi(deploymentEntity,
          (nsxClient, callback) -> {
            try {
              nsxClient.getFabricApi().getFabricNodeState(fabricNodeId, callback);
              return null;
            } catch (Throwable t) {
              return t;
            }
          });

      switch (fabricNodeState.getState()) {
        case SUCCESS:
          logger.info("Fabric node " + fabricNodeId + " successfully becomes READY");
          return;
        case PENDING:
        case IN_PROGRESS:
          Thread.sleep(NSX_WAIT_TIME_SEC * 1000);
          break;
        case FAILED:
        case PARTIAL_SUCCESS:
        case ORPHANED:
          throw new HostRegisterNsxException("Fabric node " + fabricNodeId + " failed to become READY");
      }
    }
  }

  private String getHostSwitchName(
      DeploymentEntity deploymentEntity) throws HostRegisterNsxException, InterruptedException {

    logger.info("Getting host switch name of transport zone " + deploymentEntity.getNetworkZoneId());
    TransportZone transportZone = callNsxApi(deploymentEntity,
        (nsxClient, callback) -> {
          try {
            nsxClient.getFabricApi().getTransportZone(deploymentEntity.getNetworkZoneId(), callback);
            return null;
          } catch (Throwable t) {
            return t;
          }
        });
    logger.info("Successfully gets the host switch name");

    return transportZone.getHostSwitchName();
  }

  private String configureHostAsTransportNode(
      DeploymentEntity deploymentEntity,
      HostEntity hostEntity,
      String fabricNodeId,
      String hostSwitchName) throws HostRegisterNsxException, InterruptedException {

    logger.info("Configuring host " + hostEntity.getAddress() + " as transport node");
    TransportNode transportNode = callNsxApi(deploymentEntity,
        (nsxClient, callback) -> {
          try {

            HostSwitch hostSwitch = new HostSwitch();
            hostSwitch.setName(hostSwitchName);
            hostSwitch.setStaticIpPoolId(deploymentEntity.getNetworkEdgeIpPoolId());
            List<PhysicalNic> hostUplinkPnics = new ArrayList<>();
            PhysicalNic hostUplinkPnic = new PhysicalNic();
            hostUplinkPnic.setDeviceName(deploymentEntity.getNetworkHostUplinkPnic());
            hostUplinkPnic.setUplinkName("uplink-1");
            hostUplinkPnics.add(hostUplinkPnic);
            hostSwitch.setPhysicalNics(hostUplinkPnics);

            TransportNodeCreateSpec request = new TransportNodeCreateSpec();
            request.setDisplayName(NameUtils.getTransportNodeName(hostEntity.getAddress()));
            request.setDescription(NameUtils.getTransportNodeDescription(hostEntity.getAddress()));
            request.setNodeId(fabricNodeId);
            request.setHostSwitches(Collections.singletonList(hostSwitch));

            TransportZoneEndPoint transportZoneEndPoint = new TransportZoneEndPoint();
            transportZoneEndPoint.setTransportZoneId(deploymentEntity.getNetworkZoneId());
            request.setTransportZoneEndPoints(Collections.singletonList(transportZoneEndPoint));

            ObjectMapper om = new ObjectMapper();
            om.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
            String payload = om.writeValueAsString(request);
            logger.info("Sending TransportNodeCreateSpec to NSX: " + payload);

            nsxClient.getFabricApi().createTransportNode(request, callback);
            return null;
          } catch (Throwable t) {
            return t;
          }
        });
    logger.info("Host " + hostEntity.getAddress() + " is successfully configured as transport node");

    return transportNode.getId();
  }

  private void waitForTransportNodeReady(
      DeploymentEntity deploymentEntity,
      String transportNodeId) throws HostRegisterNsxException, InterruptedException {

    logger.info("Waiting for transport node " + transportNodeId + " to become READY");
    while (true) {
      TransportNodeState transportNodeState = callNsxApi(deploymentEntity,
          (nsxClient, callback) -> {
            try {
              nsxClient.getFabricApi().getTransportNodeState(transportNodeId, callback);
              return null;
            } catch (Throwable t) {
              return t;
            }
          });

      switch (transportNodeState.getState()) {
        case SUCCESS:
          logger.info("Transport node " + transportNodeId + " successfully becomes READY");
          return;
        case PENDING:
        case IN_PROGRESS:
          Thread.sleep(NSX_WAIT_TIME_SEC * 1000);
          break;
        case FAILED:
        case PARTIAL_SUCCESS:
        case ORPHANED:
          throw new HostRegisterNsxException("Transport node " + transportNodeId + " failed to become READY");
      }
    }
  }

  private <T> T callNsxApi(
      DeploymentEntity deploymentEntity,
      BiFunction<NsxClient, FutureCallback<T>, Throwable> handler)
      throws HostRegisterNsxException, InterruptedException {

    NsxClient nsxClient = taskCommand.getNsxClientFactory().create(
        deploymentEntity.getNetworkManagerAddress(),
        deploymentEntity.getNetworkManagerUsername(),
        deploymentEntity.getNetworkManagerPassword());

    CountDownLatch latch = new CountDownLatch(1);
    final List<T> objectList = new ArrayList<>();
    final List<Throwable> failureList = new ArrayList<>();
    FutureCallback<T> callback = new FutureCallback<T>() {
      @Override
      public void onSuccess(T o) {
        objectList.add(o);
        latch.countDown();
      }

      @Override
      public void onFailure(Throwable throwable) {
        failureList.add(throwable);
        latch.countDown();
      }
    };

    Throwable t = handler.apply(nsxClient, callback);
    if (t != null) {
      failureList.add(t);
    }

    latch.await(NSX_WAIT_TIME_SEC, TimeUnit.SECONDS);

    if (!failureList.isEmpty()) {
      StringBuilder sb = new StringBuilder();
      int failureIndex = 0;
      for (Throwable failure : failureList) {
        sb.append("Failure " + failureIndex);
        sb.append(failure.toString());
      }

      logger.info("Failed to call NSX API: " + sb.toString());
      throw new HostRegisterNsxException(sb.toString());
    }

    return objectList.iterator().next();
  }
}
