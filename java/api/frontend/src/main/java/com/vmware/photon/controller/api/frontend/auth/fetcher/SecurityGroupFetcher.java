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

import java.util.Set;

/**
 * Interface for the implemented for object retrieving security groups needed
 * to authorize a particular request.
 */
public interface SecurityGroupFetcher {

  /**
   * Security group indicating that access is granted to everyone.
   */
  public static final String EVERYONE = "EVERYONE";

  /**
   * Method implementing the security group retrieval strategy.
   *
   * @param authorizationObject
   * @return
   */
  Set<String> fetchSecurityGroups(TransactionAuthorizationObject authorizationObject);
}
