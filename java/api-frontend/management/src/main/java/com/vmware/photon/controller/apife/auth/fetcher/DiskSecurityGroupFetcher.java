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

package com.vmware.photon.controller.apife.auth.fetcher;

import com.vmware.photon.controller.api.model.PersistentDisk;
import com.vmware.photon.controller.apife.auth.TransactionAuthorizationObject;
import com.vmware.photon.controller.apife.backends.DiskBackend;
import com.vmware.photon.controller.apife.exceptions.external.DiskNotFoundException;

import com.google.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import static com.google.common.base.Preconditions.checkArgument;

import java.util.HashSet;
import java.util.Set;

/**
 * Implementation of object that retrieves the security groups for a disk authorization
 * object.
 */
public class DiskSecurityGroupFetcher implements SecurityGroupFetcher {
  /**
   * Logger.
   */
  private static final Logger logger = LoggerFactory.getLogger(DiskSecurityGroupFetcher.class);

  /**
   * Storage access object for disk resources.
   */
  DiskBackend backend;

  /**
   * Fetcher used to retrieve the security groups for the parent project.
   */
  SecurityGroupFetcher projectFetcher;

  @Inject
  public DiskSecurityGroupFetcher(DiskBackend backend,
                                  @Project SecurityGroupFetcher projectFetcher) {
    this.backend = backend;
    this.projectFetcher = projectFetcher;
  }

  @Override
  public Set<String> fetchSecurityGroups(TransactionAuthorizationObject authorizationObject) {
    checkArgument(authorizationObject.getKind() == TransactionAuthorizationObject.Kind.DISK,
        "authorizationObject must be of 'kind' DISK.");
    checkArgument(authorizationObject.getStrategy() == TransactionAuthorizationObject.Strategy.PARENT,
        "authorizationObject must have 'strategy' PARENT.");

    Set<String> securityGroups = new HashSet<>();
    try {
      PersistentDisk disk = backend.toApiRepresentation(authorizationObject.getId());
      securityGroups = projectFetcher.fetchSecurityGroups(
          new TransactionAuthorizationObject(
              TransactionAuthorizationObject.Kind.PROJECT,
              TransactionAuthorizationObject.Strategy.SELF,
              disk.getProjectId()));

    } catch (DiskNotFoundException ex) {
      logger.warn("invalid disk id {}", authorizationObject.getId());
    } catch (Exception ex) {
      logger.warn("unexpected exception processing disk {}", authorizationObject.getId());
    }

    return securityGroups;
  }
}
