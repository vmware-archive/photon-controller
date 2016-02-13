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

package com.vmware.photon.controller.housekeeper.service;

import com.vmware.photon.controller.common.logging.LoggingUtils;
import com.vmware.photon.controller.common.manifest.BuildInfo;
import com.vmware.photon.controller.common.thrift.ServerSet;
import com.vmware.photon.controller.common.xenon.ServiceHostUtils;
import com.vmware.photon.controller.common.zookeeper.ServiceNodeEventHandler;
import com.vmware.photon.controller.housekeeper.HousekeeperServerSet;
import com.vmware.photon.controller.housekeeper.dcp.DcpConfig;
import com.vmware.photon.controller.housekeeper.dcp.HousekeeperDcpServiceHost;
import com.vmware.photon.controller.housekeeper.gen.Housekeeper;
import com.vmware.photon.controller.housekeeper.gen.ReplicateImageRequest;
import com.vmware.photon.controller.housekeeper.gen.ReplicateImageResponse;
import com.vmware.photon.controller.housekeeper.gen.ReplicateImageStatusRequest;
import com.vmware.photon.controller.housekeeper.gen.ReplicateImageStatusResponse;
import com.vmware.photon.controller.status.gen.Status;
import com.vmware.photon.controller.status.gen.StatusType;
import com.vmware.photon.controller.tracing.gen.TracingInfo;

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.apache.thrift.TException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.util.Set;
import java.util.UUID;

/**
 * HousekeeperService implements all methods required by Housekeeper thrift service definition.
 */
@Singleton
public class HousekeeperService implements Housekeeper.Iface, ServiceNodeEventHandler, ServerSet.ChangeListener {

  private static final Logger logger = LoggerFactory.getLogger(HousekeeperService.class);

  private final ServerSet serverSet;
  private final HousekeeperDcpServiceHost dcpHost;
  private final DcpConfig dcpConfig;
  private final BuildInfo buildInfo;

  @Inject
  public HousekeeperService(
      @HousekeeperServerSet ServerSet serverSet,
      HousekeeperDcpServiceHost host,
      DcpConfig dcpConfig,
      BuildInfo buildInfo) {
    this.serverSet = serverSet;
    this.serverSet.addChangeListener(this);
    this.dcpHost = host;
    this.dcpConfig = dcpConfig;
    this.buildInfo = buildInfo;
  }

  @Override
  public Status get_status() throws TException {
    if (dcpHost.isReady()) {
      Status status = new Status(StatusType.READY);
      status.setBuild_info(buildInfo.toString());
      return status;
    }
    return new Status(StatusType.INITIALIZING);
  }

  @Override
  public ReplicateImageResponse replicate_image(ReplicateImageRequest request) throws TException {
    setRequestId(request.getTracing_info());
    return buildReplicator().replicateImage(request);
  }

  @Override
  public ReplicateImageStatusResponse replicate_image_status(ReplicateImageStatusRequest request) throws TException {
    setRequestId(request.getTracing_info());
    return buildReplicator().getImageReplicationStatus(request);
  }

  @Override
  public void onJoin() {
    logger.info("HousekeeperService joined.");
  }

  @Override
  public void onLeave() {
    logger.info("HousekeeperService left.");
  }

  @VisibleForTesting
  protected Set<InetSocketAddress> getServers() {
    return serverSet.getServers();
  }

  @VisibleForTesting
  protected ImageReplicator buildReplicator() {
    return new ImageReplicator(dcpHost, dcpConfig.getImageCopyBatchSize());
  }

  @Override
  public void onServerAdded(InetSocketAddress address) {
    String host = address.getHostString();
    String currentHost = dcpHost.getUri().getHost();
    if (host.equals(currentHost)) {
      logger.info("Skip adding self {}", host);
      return;
    }

    logger.info("joining {} to {}", host, currentHost);
    ServiceHostUtils.joinNodeGroup(dcpHost, host);
  }

  @Override
  public void onServerRemoved(InetSocketAddress address) {
  }

  private void setRequestId(TracingInfo tracingInfo) {
    String requestId = null;
    if (tracingInfo != null) {
      requestId = tracingInfo.getRequest_id();
    }

    if (requestId == null || requestId.isEmpty()) {
      requestId = UUID.randomUUID().toString();
      logger.warn(String.format("There is no request id passed to Housekeeper. A new requestId %s is created.",
          requestId));
    }

    LoggingUtils.setRequestId(requestId);
  }
}
