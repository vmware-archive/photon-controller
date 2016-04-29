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

package com.vmware.photon.controller.common.xenon;

import com.vmware.photon.controller.common.CloudStoreServerSet;
import com.vmware.photon.controller.common.thrift.ServerSet;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.UriUtils;

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import static com.google.common.base.Preconditions.checkNotNull;

import java.net.URI;
import java.net.URISyntaxException;

/**
 * Helper class to interact with a Cloudstore host.
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

  @VisibleForTesting
  protected ServerSet getCloudStoreServerSet() {
    return this.cloudStoreServerSet;
  }

  @VisibleForTesting
  protected URI getLocalHostUri() {
    return this.localHostUri;
  }

  public URI getCloudStoreURI(String path) {
    try {
      return ServiceUtils.createUriFromServerSet(cloudStoreServerSet, path);
    } catch (URISyntaxException uriSyntaxException) {
      throw new RuntimeException(uriSyntaxException);
    }
  }

  /**
   * This selects the local Cloudstore, if it's currently in the server set.
   * If it's not, it selects a random Cloudstore host.
   *
   * Note that this is different from getLocalHostUri(): that will always return
   * a local URI, but it may not be available if the local Cloudstore isn't running.
   */
  public URI selectLocalCloudStoreIfAvailable(String path) {
    try {
      return ServiceUtils.selectLocalServer(cloudStoreServerSet, path);
    } catch (URISyntaxException uriSyntaxException) {
      throw new RuntimeException(uriSyntaxException);
    }
  }

  public Operation createDelete(String path) {
    return Operation
        .createDelete(getCloudStoreURI(path))
        .setBody(new ServiceDocument())
        .setReferer(this.localHostUri);
  }

  public Operation createLocalDelete(String path) {
    return Operation
        .createDelete(selectLocalCloudStoreIfAvailable(path))
        .setBody(new ServiceDocument())
        .setReferer(this.localHostUri);
  }

  public Operation createGet(String path) {
    return Operation
        .createGet(getCloudStoreURI(path))
        .setReferer(this.localHostUri);
  }

  public Operation createLocalGet(String path) {
    return Operation
        .createGet(selectLocalCloudStoreIfAvailable(path))
        .setReferer(this.localHostUri);
  }

  public Operation createPost(String path) {
    return Operation
        .createPost(getCloudStoreURI(path))
        .setReferer(this.localHostUri);
  }

  public Operation createLocalPost(String path) {
    return Operation
        .createPost(selectLocalCloudStoreIfAvailable(path))
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
        .setReferer(this.localHostUri);
  }

  public Operation createLocalPatch(String path) {
    return Operation
        .createPatch(selectLocalCloudStoreIfAvailable(path))
        .setReferer(this.localHostUri);
  }
}
