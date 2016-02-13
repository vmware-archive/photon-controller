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

package com.vmware.photon.controller.deployer.service.client;

import com.vmware.photon.controller.cloudstore.dcp.entity.HostService;
import com.vmware.photon.controller.cloudstore.dcp.entity.HostServiceFactory;
import com.vmware.photon.controller.common.logging.LoggingUtils;
import com.vmware.photon.controller.common.thrift.ServerSet;
import com.vmware.photon.controller.common.xenon.CloudStoreHelper;
import com.vmware.photon.controller.common.xenon.QueryTaskUtils;
import com.vmware.photon.controller.common.xenon.ServiceHostUtils;
import com.vmware.photon.controller.deployer.dcp.DeployerDcpServiceHost;
import com.vmware.photon.controller.deployer.gen.DeleteHostRequest;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.QueryTask;
import com.vmware.xenon.services.common.ServiceUriPaths;

import java.net.URI;
import java.util.Map;

/**
 * This class implements functionality to create DCP entities.
 */
public class HostServiceClient {

  private static final String REFERRER_PATH = "/thrift-endpoint/host-client";

  private ServerSet cloudStoreServerSet;
  private DeployerDcpServiceHost dcpHost;

  public HostServiceClient(DeployerDcpServiceHost dcpHost, ServerSet cloudStoreServerSet) {
    this.dcpHost = dcpHost;
    this.cloudStoreServerSet = cloudStoreServerSet;
  }

  /**
   * This method deletes a {@link HostService} entity.
   *
   * @param request
   * @throws Throwable
   */
  public void delete(DeleteHostRequest request) throws Throwable {

    String path = HostServiceFactory.SELF_LINK + "/" + request.getHost_id();
    CloudStoreHelper cloudStoreHelper = new CloudStoreHelper(cloudStoreServerSet);
    URI uri = cloudStoreHelper.getCloudStoreURI(null);

    Operation delete = Operation
        .createDelete(UriUtils.buildUri(uri, path))
        .setReferer(UriUtils.buildUri(dcpHost, REFERRER_PATH))
        .setBody(new ServiceDocument())
        .setContextId(LoggingUtils.getRequestId());

    ServiceHostUtils.sendRequestAndWait(dcpHost, delete, REFERRER_PATH);
  }

  /**
   * This method checks if any {@link HostService} entity exists that matches the supplied criteria.
   *
   * @param criteria
   * @return
   * @throws Throwable
   */
  public boolean match(Map<String, String> criteria, Map<String, String> exclusionCriteria) throws Throwable {

    QueryTask.Query kindClause = new QueryTask.Query()
        .setTermPropertyName(ServiceDocument.FIELD_NAME_KIND)
        .setTermMatchValue(Utils.buildKind(HostService.State.class));

    QueryTask.QuerySpecification querySpecification = new QueryTask.QuerySpecification();
    querySpecification.query.addBooleanClause(kindClause);

    // process inclusions.
    buildQuerySpecification(querySpecification, criteria, false);
    // process exclusions.
    buildQuerySpecification(querySpecification, exclusionCriteria, true);

    QueryTask queryTask = QueryTask.create(querySpecification).setDirect(true);
    CloudStoreHelper cloudStoreHelper = new CloudStoreHelper(cloudStoreServerSet);
    URI uri = cloudStoreHelper.getCloudStoreURI(null);

    Operation queryOperation = Operation
        .createPost(UriUtils.buildBroadcastRequestUri(
            UriUtils.buildUri(uri, ServiceUriPaths.CORE_LOCAL_QUERY_TASKS),
            ServiceUriPaths.DEFAULT_NODE_SELECTOR))
        .setBody(queryTask)
        .setReferer(UriUtils.buildUri(dcpHost, REFERRER_PATH));

    Operation operation = ServiceHostUtils.sendRequestAndWait(dcpHost, queryOperation, REFERRER_PATH);
    return !QueryTaskUtils.getBroadcastQueryDocumentLinks(operation).isEmpty();
  }

  private QueryTask.QuerySpecification buildQuerySpecification(
      QueryTask.QuerySpecification querySpecification,
      Map<String, String> criteria,
      Boolean exclude) {

    if (criteria != null) {
      for (Map.Entry<String, String> criterion : criteria.entrySet()) {
        String fieldName = criterion.getKey();
        String expectedValue = criterion.getValue();

        if (fieldName.equals(HostService.State.FIELD_NAME_USAGE_TAGS)) {
          fieldName = QueryTask.QuerySpecification.buildCollectionItemName(
              HostService.State.FIELD_NAME_USAGE_TAGS);
        }

        QueryTask.Query query = new QueryTask.Query()
            .setTermPropertyName(fieldName)
            .setTermMatchValue(expectedValue);

        if (exclude) {
          //this designates the criteria to be an exclusion
          query.occurance = QueryTask.Query.Occurance.MUST_NOT_OCCUR;
        }

        querySpecification.query.addBooleanClause(query);
      }
    }

    return querySpecification;
  }
}
