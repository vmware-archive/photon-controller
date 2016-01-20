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
import com.vmware.photon.controller.api.ResourceList;
import com.vmware.photon.controller.api.common.exceptions.external.ExternalException;
import com.vmware.photon.controller.api.common.exceptions.external.PageExpiredException;
import com.vmware.photon.controller.apife.exceptions.external.DatastoreNotFoundException;

import com.google.common.base.Optional;

/**
 * Inventory Datastore service backend.
 */
public interface DatastoreBackend {

  Datastore toApiRepresentation(String id) throws DatastoreNotFoundException;

  Datastore getDatastore(String id) throws DatastoreNotFoundException;

  ResourceList<Datastore> filter(Optional<String> tag, Optional<Integer> pageSize) throws ExternalException;

  ResourceList<Datastore> getDatastoresPage(String pageLink) throws PageExpiredException;
}
