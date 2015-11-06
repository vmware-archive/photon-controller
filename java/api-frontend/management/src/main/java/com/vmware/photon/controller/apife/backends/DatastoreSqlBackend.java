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
import com.vmware.photon.controller.api.common.db.Transactional;
import com.vmware.photon.controller.apife.db.dao.DatastoreDao;
import com.vmware.photon.controller.apife.exceptions.external.DatastoreNotFoundException;

import com.google.common.base.Optional;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Inventory Datastore service backend.
 */
@Singleton
public class DatastoreSqlBackend implements DatastoreBackend{

  private static final Logger logger = LoggerFactory.getLogger(DatastoreSqlBackend.class);

  private final DatastoreDao datastoreDao;

  @Inject
  public DatastoreSqlBackend(DatastoreDao datastoreDao) {
    this.datastoreDao = datastoreDao;
  }

  @Transactional
  @Override
  public Datastore getDatastore(String id) throws DatastoreNotFoundException {
    logger.error("DatastoreSqlBackend does not implement getDatastore method");
    throw new RuntimeException("DatastoreSqlBackend does not implement getDatastore method");
  }

  @Override
  public List<Datastore> filter(Optional<String> tag) {
    logger.error("DatastoreSqlBackend does not implement getDatastore method");
    throw new RuntimeException("DatastoreSqlBackend does not implement getDatastore method");
  }

  @Override
  @Transactional
  public Datastore toApiRepresentation(String id) throws DatastoreNotFoundException {
    logger.error("DatastoreSqlBackend does not implement getDatastore method");
    throw new RuntimeException("DatastoreSqlBackend does not implement getDatastore method");
  }
}
