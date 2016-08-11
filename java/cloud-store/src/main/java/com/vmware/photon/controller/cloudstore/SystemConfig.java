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
package com.vmware.photon.controller.cloudstore;

import com.vmware.photon.controller.api.model.DeploymentState;
import com.vmware.photon.controller.cloudstore.xenon.entity.DeploymentService;
import com.vmware.photon.controller.common.provider.SystemConfigProvider;
import com.vmware.photon.controller.common.xenon.OperationLatch;
import com.vmware.photon.controller.common.xenon.QueryTaskUtils;
import com.vmware.photon.controller.common.xenon.host.PhotonControllerXenonHost;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.QueryTask;
import com.vmware.xenon.services.common.ServiceUriPaths;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.Collection;
import java.util.concurrent.TimeUnit;

/**
 * Manages the Photon-Controller configuration.
 */
public class SystemConfig implements SystemConfigProvider {
  private static final Logger logger = LoggerFactory.getLogger(SystemConfig.class);

  private static SystemConfig instance = null;
  private String deploymentLink;

  private PhotonControllerXenonHost xenonHost;


  private SystemConfig(PhotonControllerXenonHost serviceHost) {
    this.xenonHost = serviceHost;
  }

  public static SystemConfig getInstance() {
    return instance;
  }

  public static void destroyInstance() {
    instance = null;
  }

  public static SystemConfig createInstance(PhotonControllerXenonHost xenonHost) {
    if (instance == null) {
      instance = new SystemConfig(xenonHost);
      xenonHost.setSystemConfigProvider(instance);
    }
    return instance;
  }

  private DeploymentService.State getState() {
    if (deploymentLink != null) {
      URI serviceUri = UriUtils.buildUri(xenonHost, deploymentLink);

      Operation getOperation = Operation
          .createGet(serviceUri)
          .setUri(serviceUri)
          .setReferer(this.xenonHost.getUri());
      OperationLatch operationLatch = new OperationLatch(getOperation);
      xenonHost.sendRequest(getOperation);
      Operation completedOperation = null;
      try {
        completedOperation = operationLatch.awaitOperationCompletion(TimeUnit.SECONDS.toMicros(90));
      } catch (Throwable e) {
        logger.error("SysConfig get failed!! ", e);
        throw new RuntimeException(e);
      }

      return completedOperation.getBody(DeploymentService.State.class);
    } else {
      QueryTask.Query kindClause = new QueryTask.Query()
          .setTermPropertyName(ServiceDocument.FIELD_NAME_KIND)
          .setTermMatchValue(Utils.buildKind(DeploymentService.State.class));

      QueryTask.QuerySpecification querySpecification = new QueryTask.QuerySpecification();
      querySpecification.query = kindClause;

      Operation broadcastOp = xenonHost.getCloudStoreHelper()
          .createBroadcastPost(ServiceUriPaths.CORE_LOCAL_QUERY_TASKS, ServiceUriPaths.DEFAULT_NODE_SELECTOR)
          .setBody(QueryTask.create(querySpecification).setDirect(true));
      OperationLatch operationLatch = new OperationLatch(broadcastOp);
      xenonHost.sendRequest(broadcastOp);
      Operation completedOperation = null;
      try {
        completedOperation = operationLatch.awaitOperationCompletion(TimeUnit.SECONDS.toMicros(90));
      } catch (Throwable e) {
        logger.error("SysConfig broadcastquery failed!! ", e);
        throw new RuntimeException(e);
      }

      Collection<String> documentLinks = QueryTaskUtils.getBroadcastQueryDocumentLinks(completedOperation);
      if (documentLinks.size() == 0) {
        return null;
      }
      this.deploymentLink = documentLinks.iterator().next();
      return getState();
    }
  }

  @Override
  public boolean isPaused() {
    DeploymentService.State state = getState();
    if (state == null) {
      logger.info("Deployment document not created yet .. isPaused returns false");
      return false;
    }
    return isPaused(state);
  }

  public boolean isPaused(DeploymentService.State state) {
    if (state.state == DeploymentState.PAUSED) {
      return true;
    }
    return false;
  }

  @Override
  public boolean isBackgroundPaused()  {
    DeploymentService.State state = getState();
    if (state == null) {
      logger.info("Deployment document not created yet .. isBackgroundPaused returns true");
      return true;
    }
    if (isPaused(state)) {
      return true;
    }
    if (state.state == DeploymentState.BACKGROUND_PAUSED) {
      return true;
    }
    return false;
  }
}
