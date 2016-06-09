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

import com.vmware.photon.controller.apife.backends.clients.ApiFeXenonRestClient;
import com.vmware.photon.controller.apife.entities.TombstoneEntity;
import com.vmware.photon.controller.cloudstore.xenon.entity.TombstoneService;
import com.vmware.photon.controller.cloudstore.xenon.entity.TombstoneServiceFactory;
import com.vmware.photon.controller.common.xenon.ServiceUtils;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Utils;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * TombstoneBackend is responsible for writing the tombstone entries for an object.
 */
public class TombstoneDcpBackend implements TombstoneBackend {

  private static final Logger logger = LoggerFactory.getLogger(TombstoneDcpBackend.class);

  private final ApiFeXenonRestClient dcpClient;

  @Inject
  public TombstoneDcpBackend(ApiFeXenonRestClient dcpClient) {
    this.dcpClient = dcpClient;
    this.dcpClient.start();
  }

  @Override
  public TombstoneEntity create(String entityKind, String entityId) {
    TombstoneService.State state = new TombstoneService.State();
    state.entityId = entityId;
    state.entityKind = entityKind;
    state.tombstoneTime = System.currentTimeMillis();

    Operation result = dcpClient.post(TombstoneServiceFactory.SELF_LINK, state);
    return toEntity(result.getBody(TombstoneService.State.class));
  }

  @Override
  public List<TombstoneEntity> getStaleTombstones() {
    return ImmutableList.of();
  }

  @Override
  public void delete(TombstoneEntity tombstone) {
    logger.debug("Deleting tombstone {}", tombstone);
    dcpClient.delete(TombstoneServiceFactory.SELF_LINK + "/" + tombstone.getId(), new TombstoneService.State());
    logger.debug("Deleted tombstone {}", tombstone);
  }

  @Override
  public TombstoneEntity getByEntityId(String id) {
    final ImmutableMap.Builder<String, String> termsBuilder = new ImmutableMap.Builder<>();
    termsBuilder.put("entityId", id);

    List<TombstoneService.State> tombstoneDocs =
        dcpClient.queryDocuments(TombstoneService.State.class, termsBuilder.build());
    switch (tombstoneDocs.size()) {
      case 0:
        return null;

      case 1:
        return toEntity(tombstoneDocs.get(0));

      default:
        logger.error("Entity ({}) has more than one tombstone ({}).", id, Utils.toJson(false, false, tombstoneDocs));
        throw new RuntimeException("Multiple tombstones for entity: " + id);
    }
  }

  private TombstoneEntity toEntity(TombstoneService.State state) {
    TombstoneEntity entity = new TombstoneEntity();
    entity.setId(ServiceUtils.getIDFromDocumentSelfLink(state.documentSelfLink));
    entity.setEntityId(state.entityId);
    entity.setEntityKind(state.entityKind);
    entity.setTombstoneTime(state.tombstoneTime);

    return entity;
  }
}
