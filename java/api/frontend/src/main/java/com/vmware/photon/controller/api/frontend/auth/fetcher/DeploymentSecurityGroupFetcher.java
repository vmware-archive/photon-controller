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

package com.vmware.photon.controller.api.frontend.auth.fetcher;

import com.vmware.photon.controller.api.frontend.auth.TransactionAuthorizationObject;
import com.vmware.photon.controller.api.frontend.backends.DeploymentBackend;
import com.vmware.photon.controller.api.frontend.exceptions.external.DeploymentNotFoundException;
import com.vmware.photon.controller.api.model.Deployment;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import static com.google.common.base.Preconditions.checkArgument;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Implementation of object that retrieves the security groups for a deployment authorization
 * object.
 */
@Singleton
public class DeploymentSecurityGroupFetcher implements SecurityGroupFetcher {

  /**
   * Logger.
   */
  private static final Logger logger = LoggerFactory.getLogger(DeploymentSecurityGroupFetcher.class);


  /**
   * Storage access object for deployment resources.
   */
  private DeploymentBackend backend;

  @Inject
  public DeploymentSecurityGroupFetcher(DeploymentBackend deploymentBackend) {
    this.backend = deploymentBackend;
  }

  @Override
  public Set<String> fetchSecurityGroups(TransactionAuthorizationObject authorizationObject) {
    checkArgument(authorizationObject.getKind() == TransactionAuthorizationObject.Kind.DEPLOYMENT,
        "authorizationObject must be of 'kind' DEPLOYMENT.");
    checkArgument(authorizationObject.getStrategy() == TransactionAuthorizationObject.Strategy.SELF,
        "authorizationObject must have 'strategy' SELF.");

    Set<String> securityGroups = new HashSet<>();
    try {
      Deployment deployment = this.getDeploymentResource(authorizationObject.getId());
      if (null != deployment && null != deployment.getAuth()) {
        securityGroups.addAll(deployment.getAuth().getSecurityGroups());
      }
    } catch (DeploymentNotFoundException ex) {
      logger.warn("invalid deployment. (id={})", authorizationObject.getId(), ex);
    } catch (Exception ex) {
      logger.warn("unexpected exception processing deployment. (id={})", authorizationObject.getId(), ex);
    }

    return securityGroups;
  }


  private Deployment getDeploymentResource(String id) throws DeploymentNotFoundException {
    if (null != id && !id.isEmpty()) {
      // we seem to have a deployment id
      return backend.toApiRepresentation(id);
    }

    List<Deployment> deploymentList = backend.getAll();
    if (null == deploymentList || 1 > deploymentList.size()) {
      // there is no deployment object
      logger.warn("no deployment object present. returning 'null'.");
      return null;
    } else if (1 < deploymentList.size()) {
      throw new IllegalStateException("More than one deployment resources found.");
    }

    return deploymentList.get(0);
  }
}
