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

import com.vmware.photon.controller.api.PortGroup;
import com.vmware.photon.controller.api.UsageTag;
import com.vmware.photon.controller.apife.backends.clients.ApiFeDcpRestClient;
import com.vmware.photon.controller.apife.exceptions.external.PortGroupNotFoundException;
import com.vmware.photon.controller.cloudstore.dcp.entity.PortGroupService;
import com.vmware.photon.controller.cloudstore.dcp.entity.PortGroupServiceFactory;
import com.vmware.photon.controller.common.xenon.ServiceUtils;
import com.vmware.photon.controller.common.xenon.exceptions.DocumentNotFoundException;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * portgroup service backend.
 */
public class PortGroupDcpBackend implements PortGroupBackend {

  private static final Logger logger = LoggerFactory.getLogger(PortGroupDcpBackend.class);

  private final ApiFeDcpRestClient dcpClient;

  @Inject
  public PortGroupDcpBackend(ApiFeDcpRestClient dcpClient) {
    this.dcpClient = dcpClient;
    this.dcpClient.start();
  }

  @Override
  public PortGroup toApiRepresentation(String id) throws PortGroupNotFoundException {
    return toApiRepresentation(findById(id));
  }

  @Override
  public List<PortGroup> filter(Optional<String> name, Optional<UsageTag> usageTag) {
    final ImmutableMap.Builder<String, String> termsBuilder = new ImmutableMap.Builder<>();
    if (name.isPresent()) {
      termsBuilder.put("name", name.get());
    }

    if (usageTag.isPresent()) {
      termsBuilder.put(PortGroupService.USAGE_TAGS_KEY, usageTag.get().toString());
    }

    List<PortGroup> portGroups = new ArrayList<>();
    ImmutableMap<String, String> terms = termsBuilder.build();
    logger.info("Filtering Port Groups using terms {}", terms);
    for (PortGroupService.State state : dcpClient.queryDocuments(
        PortGroupService.State.class, terms)) {
      portGroups.add(toApiRepresentation(state));
    }

    return portGroups;
  }

  private PortGroupService.State findById(String id) throws PortGroupNotFoundException {
    try {
      com.vmware.xenon.common.Operation result = dcpClient.get(PortGroupServiceFactory.SELF_LINK + "/" + id);
      return result.getBody(PortGroupService.State.class);
    } catch (DocumentNotFoundException exception) {
      throw new PortGroupNotFoundException(id);
    }
  }

  private PortGroup toApiRepresentation(PortGroupService.State state) {
    PortGroup portGroup = new PortGroup();
    portGroup.setId(ServiceUtils.getIDFromDocumentSelfLink(state.documentSelfLink));
    portGroup.setName(state.name);
    portGroup.setUsageTags(state.usageTags);

    return portGroup;
  }

}
