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

import com.vmware.photon.controller.api.Datastore;
import com.vmware.photon.controller.apife.backends.clients.ApiFeDcpRestClient;
import com.vmware.photon.controller.apife.exceptions.external.DatastoreNotFoundException;
import com.vmware.photon.controller.cloudstore.dcp.entity.DatastoreService;
import com.vmware.photon.controller.cloudstore.dcp.entity.DatastoreServiceFactory;
import com.vmware.photon.controller.common.dcp.ServiceUtils;
import com.vmware.photon.controller.common.dcp.exceptions.DocumentNotFoundException;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Inventory Datastore service backend.
 */
@Singleton
public class DatastoreDcpBackend implements DatastoreBackend{

  private static final Logger logger = LoggerFactory.getLogger(DatastoreDcpBackend.class);

  private final ApiFeDcpRestClient dcpClient;

  @Inject
  public DatastoreDcpBackend(ApiFeDcpRestClient dcpClient) {
    this.dcpClient = dcpClient;
    this.dcpClient.start();
  }

  @Override
  public Datastore toApiRepresentation(String id) throws DatastoreNotFoundException {
    return toApiRepresentation(findById(id));
  }

  @Override
  public List<Datastore> filter(Optional<String> tag) {
    final ImmutableMap.Builder<String, String> termsBuilder = new ImmutableMap.Builder<>();

    if (tag.isPresent()) {
      termsBuilder.put(DatastoreService.TAGS_KEY, tag.get().toString());
    }

    List<Datastore> datastores = new ArrayList<>();
    ImmutableMap<String, String> terms = termsBuilder.build();
    logger.info("Filtering Datastores using terms {}", terms);
    for (DatastoreService.State state : dcpClient.queryDocuments(
        DatastoreService.State.class, terms)) {
      datastores.add(toApiRepresentation(state));
    }

    return datastores;
  }

  @Override
  public Datastore getDatastore(String id) throws DatastoreNotFoundException {
    return toApiRepresentation(findById(id));
  }

  private DatastoreService.State findById(String id) throws DatastoreNotFoundException {
    try {
      com.vmware.dcp.common.Operation result = dcpClient.getAndWait(DatastoreServiceFactory.SELF_LINK + "/" + id);
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
