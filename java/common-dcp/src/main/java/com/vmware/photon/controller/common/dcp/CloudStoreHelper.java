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
import com.vmware.dcp.common.Service;
import com.vmware.dcp.common.ServiceDocument;
import com.vmware.dcp.common.UriUtils;
import com.vmware.dcp.services.common.QueryTask;
import com.vmware.dcp.services.common.ServiceUriPaths;
import com.vmware.photon.controller.common.CloudStoreServerSet;
import com.vmware.photon.controller.common.thrift.ServerSet;

import com.google.inject.Inject;
import static com.google.common.base.Preconditions.checkNotNull;

import java.net.URI;
import java.net.URISyntaxException;

/**
 * Helper class to interact with a random cloud-store DCP host.
 */
public class CloudStoreHelper {

  private ServerSet cloudStoreServerSet;
  private URI localHostAddress;

  @Inject
  public CloudStoreHelper(@CloudStoreServerSet ServerSet cloudStoreServerSet) {
    this.cloudStoreServerSet = checkNotNull(cloudStoreServerSet);
    this.localHostAddress = OperationUtils.getLocalAddress();
  }

  public CloudStoreHelper() {
    this.localHostAddress = OperationUtils.getLocalAddress();
  }

  public void setServerSet(ServerSet cloudStoreServerSet) {
    this.cloudStoreServerSet = checkNotNull(cloudStoreServerSet);
    this.localHostAddress = OperationUtils.getLocalAddress();
  }

  public URI getCloudStoreURI(String path) {
    try {
      return ServiceUtils.createUriFromServerSet(cloudStoreServerSet, path);
    } catch (URISyntaxException uriSyntaxException) {
      throw new RuntimeException(uriSyntaxException);
    }
  }

  public Operation createGet(String path) {
    return Operation
        .createGet(getCloudStoreURI(path))
        .addPragmaDirective(Operation.PRAGMA_DIRECTIVE_NO_QUEUING)
        .setReferer(this.localHostAddress);
  }

  public Operation createPost(String path) {
    return Operation.createPost(getCloudStoreURI(path));
  }

  public Operation createBroadcastPost(String path, String selectorPath) {
    return Operation
        .createPost(UriUtils.buildBroadcastRequestUri(getCloudStoreURI(path), selectorPath))
        .setReferer(this.localHostAddress);
  }

  public Operation createPatch(String path) {
    return Operation
        .createPatch(getCloudStoreURI(path))
        .addPragmaDirective(Operation.PRAGMA_DIRECTIVE_NO_QUEUING)
        .setReferer(this.localHostAddress);
  }
  
  public void queryEntities(Service service, QueryTask.QuerySpecification querySpecification,
                            Operation.CompletionHandler completionHandler) {
    URI uri = getCloudStoreURI(null);
    queryEntities(uri, service, querySpecification, completionHandler);
  }

  public void queryEntities(URI uri, Service service, QueryTask.QuerySpecification querySpecification,
                            Operation.CompletionHandler completionHandler) {
    service.sendRequest(Operation
        .createPost(UriUtils.buildBroadcastRequestUri(
            UriUtils.buildUri(uri, ServiceUriPaths.CORE_LOCAL_QUERY_TASKS),
            ServiceUriPaths.DEFAULT_NODE_SELECTOR))
        .setReferer(this.localHostAddress)
        .setBody(QueryTask
            .create(querySpecification)
            .setDirect(true))
        .setCompletion(completionHandler));
  }

  public void deleteEntity(Service service, String documentLink, Operation.CompletionHandler completionHandler) {
    URI uri = getCloudStoreURI(null);
    Operation deleteOperation =
        Operation.createDelete(UriUtils.buildUri(uri, documentLink))
            .setBody(new ServiceDocument())
            .setReferer(this.localHostAddress)
            .addPragmaDirective(Operation.PRAGMA_DIRECTIVE_NO_QUEUING)
            .setCompletion(completionHandler);
    service.sendRequest(deleteOperation);
  }

  public void patchEntity(Service service, String documentLink, Object body, Operation.CompletionHandler
      completionHandler) {
    URI uri = getCloudStoreURI(null);
    patchEntity(uri, service, documentLink, body, completionHandler);
  }

  public void patchEntity(URI uri, Service service, String documentLink, Object body, Operation
      .CompletionHandler
      completionHandler) {
    Operation patchOperation = Operation
        .createPatch(UriUtils.buildUri(uri, documentLink))
        .setReferer(this.localHostAddress)
        .setBody(body)
        .setCompletion(completionHandler)
        .addPragmaDirective(Operation.PRAGMA_DIRECTIVE_NO_QUEUING);
    service.sendRequest(patchOperation);
  }

  public void postEntity(Service service, String documentLink, Object body,
                         Operation.CompletionHandler completionHandler) {

    URI uri = getCloudStoreURI(null);
    Operation postOperation = Operation
        .createPost(UriUtils.buildUri(uri, documentLink))
        .setReferer(this.localHostAddress)
        .setBody(body)
        .setCompletion(completionHandler);
    service.sendRequest(postOperation);
  }

}
