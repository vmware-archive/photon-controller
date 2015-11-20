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

package com.vmware.photon.controller.common.dcp;

import com.vmware.dcp.common.Operation;
import com.vmware.dcp.common.ServiceDocument;
import com.vmware.dcp.common.UriUtils;
import com.vmware.photon.controller.common.CloudStoreServerSet;
import com.vmware.photon.controller.common.thrift.ServerSet;

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import static com.google.common.base.Preconditions.checkNotNull;

import java.net.URI;
import java.net.URISyntaxException;

/**
 * Helper class to interact with a random cloud-store DCP host.
 */
public class CloudStoreHelper {

  private ServerSet cloudStoreServerSet;
  private URI localHostUri;

  @Inject
  public CloudStoreHelper(@CloudStoreServerSet ServerSet cloudStoreServerSet) {
    this.cloudStoreServerSet = checkNotNull(cloudStoreServerSet);
    this.localHostUri = OperationUtils.getLocalHostUri();
  }

  public CloudStoreHelper() {
    this.localHostUri = OperationUtils.getLocalHostUri();
  }

  @VisibleForTesting
  public void setServerSet(ServerSet cloudStoreServerSet) {
    this.cloudStoreServerSet = checkNotNull(cloudStoreServerSet);
    this.localHostUri = OperationUtils.getLocalHostUri();
  }

  public URI getCloudStoreURI(String path) {
    try {
      return ServiceUtils.createUriFromServerSet(cloudStoreServerSet, path);
    } catch (URISyntaxException uriSyntaxException) {
      throw new RuntimeException(uriSyntaxException);
    }
  }

  public Operation createDelete(String path) {
    return Operation
        .createDelete(getCloudStoreURI(path))
        .addPragmaDirective(Operation.PRAGMA_DIRECTIVE_NO_QUEUING)
        .setBody(new ServiceDocument())
        .setReferer(this.localHostUri);
  }

  public Operation createGet(String path) {
    return Operation
        .createGet(getCloudStoreURI(path))
        .addPragmaDirective(Operation.PRAGMA_DIRECTIVE_NO_QUEUING)
        .setReferer(this.localHostUri);
  }

  public Operation createPost(String path) {
    return Operation
        .createPost(getCloudStoreURI(path))
        .setReferer(this.localHostUri);
  }

  public Operation createBroadcastPost(String path, String selectorPath) {
    return Operation
        .createPost(UriUtils.buildBroadcastRequestUri(getCloudStoreURI(path), selectorPath))
        .setReferer(this.localHostUri);
  }

  public Operation createPatch(String path) {
    return Operation
        .createPatch(getCloudStoreURI(path))
        .addPragmaDirective(Operation.PRAGMA_DIRECTIVE_NO_QUEUING)
        .setReferer(this.localHostUri);
  }
}
