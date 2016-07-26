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

package com.vmware.photon.controller.apife.backends;

import com.vmware.photon.controller.api.model.Datastore;
import com.vmware.photon.controller.api.model.ResourceList;
import com.vmware.photon.controller.apife.backends.clients.ApiFeXenonRestClient;
import com.vmware.photon.controller.apife.exceptions.external.DatastoreNotFoundException;
import com.vmware.photon.controller.apife.exceptions.external.ExternalException;
import com.vmware.photon.controller.apife.exceptions.external.PageExpiredException;
import com.vmware.photon.controller.apife.utils.PaginationUtils;
import com.vmware.photon.controller.cloudstore.xenon.entity.DatastoreService;
import com.vmware.photon.controller.cloudstore.xenon.entity.DatastoreServiceFactory;
import com.vmware.photon.controller.common.xenon.ServiceUtils;
import com.vmware.photon.controller.common.xenon.exceptions.DocumentNotFoundException;
import com.vmware.xenon.common.ServiceDocumentQueryResult;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Inventory Datastore service backend.
 */
@Singleton
public class DatastoreXenonBackend implements DatastoreBackend{

  private static final Logger logger = LoggerFactory.getLogger(DatastoreXenonBackend.class);

  private final ApiFeXenonRestClient xenonClient;

  @Inject
  public DatastoreXenonBackend(ApiFeXenonRestClient xenonClient) {
    this.xenonClient = xenonClient;
    this.xenonClient.start();
  }

  @Override
  public Datastore toApiRepresentation(String id) throws DatastoreNotFoundException {
    return toApiRepresentation(findById(id));
  }

  @Override
  public ResourceList<Datastore> filter(Optional<String> tag, Optional<Integer> pageSize) throws ExternalException {
    final ImmutableMap.Builder<String, String> termsBuilder = new ImmutableMap.Builder<>();

    if (tag.isPresent()) {
      termsBuilder.put(DatastoreService.TAGS_KEY, tag.get().toString());
    }

    ImmutableMap<String, String> terms = termsBuilder.build();
    logger.info("Filtering Datastores using terms {}", terms);

    ServiceDocumentQueryResult queryResult = xenonClient.queryDocuments(
            DatastoreService.State.class, terms, pageSize, true);

    return PaginationUtils.xenonQueryResultToResourceList(DatastoreService.State.class, queryResult,
            state -> toApiRepresentation(state));
  }

  @Override
  public Datastore getDatastore(String id) throws DatastoreNotFoundException {
    return toApiRepresentation(findById(id));
  }

  @Override
  public ResourceList<Datastore> getDatastoresPage(String pageLink) throws PageExpiredException{
    ServiceDocumentQueryResult queryResult = null;
    try {
      queryResult = xenonClient.queryDocumentPage(pageLink);
    } catch (DocumentNotFoundException e) {
      throw new PageExpiredException(pageLink);
    }

    return PaginationUtils.xenonQueryResultToResourceList(
            DatastoreService.State.class, queryResult, state -> toApiRepresentation(state));
  }

  private DatastoreService.State findById(String id) throws DatastoreNotFoundException {
    try {
      com.vmware.xenon.common.Operation result = xenonClient.get(DatastoreServiceFactory.SELF_LINK + "/" + id);
      return result.getBody(DatastoreService.State.class);
    } catch (DocumentNotFoundException exception) {
      throw new DatastoreNotFoundException(id);
    }
  }

  private Datastore toApiRepresentation(DatastoreService.State state) {
    Datastore datastore = new Datastore();
    datastore.setId(ServiceUtils.getIDFromDocumentSelfLink(state.documentSelfLink));
    datastore.setType(state.type);
    datastore.setTags(state.tags);

    return datastore;
  }

}
