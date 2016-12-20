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

import com.google.inject.Singleton;
import static com.google.common.base.Preconditions.checkArgument;

import java.util.HashSet;
import java.util.Set;

/**
 * Implements the security group fetcher that will handle TransactionAuthorizationObjects of kind NONE.
 */
@Singleton
public class NoneSecurityGroupFetcher implements SecurityGroupFetcher {

  @Override
  public Set<String> fetchSecurityGroups(TransactionAuthorizationObject authorizationObject) {
    checkArgument(authorizationObject.getKind() == TransactionAuthorizationObject.Kind.NONE,
        "authorizationObject must be of 'kind' NONE.");

    Set<String> groups = new HashSet<>();
    groups.add(EVERYONE);

    return groups;
  }
}
