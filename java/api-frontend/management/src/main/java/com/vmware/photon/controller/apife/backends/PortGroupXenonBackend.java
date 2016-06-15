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
import com.vmware.photon.controller.api.ResourceList;
import com.vmware.photon.controller.api.UsageTag;
import com.vmware.photon.controller.api.common.exceptions.external.ExternalException;
import com.vmware.photon.controller.api.common.exceptions.external.PageExpiredException;
import com.vmware.photon.controller.apife.backends.clients.ApiFeXenonRestClient;
import com.vmware.photon.controller.apife.exceptions.external.PortGroupNotFoundException;
import com.vmware.photon.controller.apife.utils.PaginationUtils;
import com.vmware.photon.controller.cloudstore.xenon.entity.PortGroupService;
import com.vmware.photon.controller.cloudstore.xenon.entity.PortGroupServiceFactory;
import com.vmware.photon.controller.common.xenon.ServiceUtils;
import com.vmware.photon.controller.common.xenon.exceptions.DocumentNotFoundException;
import com.vmware.xenon.common.ServiceDocumentQueryResult;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * portgroup service backend.
 */
public class PortGroupXenonBackend implements PortGroupBackend {

  private static final Logger logger = LoggerFactory.getLogger(PortGroupXenonBackend.class);

  private final ApiFeXenonRestClient xenonClient;

  @Inject
  public PortGroupXenonBackend(ApiFeXenonRestClient xenonClient) {
    this.xenonClient = xenonClient;
    this.xenonClient.start();
  }

  @Override
  public PortGroup toApiRepresentation(String id) throws PortGroupNotFoundException {
    return toApiRepresentation(findById(id));
  }

  @Override
  public ResourceList<PortGroup> filter(Optional<String> name, Optional<UsageTag> usageTag,
                                        Optional<Integer> pageSize) {
    final ImmutableMap.Builder<String, String> termsBuilder = new ImmutableMap.Builder<>();
    if (name.isPresent()) {
      termsBuilder.put("name", name.get());
    }

    if (usageTag.isPresent()) {
      termsBuilder.put(PortGroupService.USAGE_TAGS_KEY, usageTag.get().toString());
    }

    ServiceDocumentQueryResult queryResult = xenonClient.queryDocuments(PortGroupService.State.class,
        termsBuilder.build(), pageSize, true);
    return PaginationUtils.xenonQueryResultToResourceList(
        PortGroupService.State.class,
        queryResult,
        state -> toApiRepresentation(state));
  }

  @Override
  public ResourceList<PortGroup> getPortGroupsPage(String pageLink) throws ExternalException {
    ServiceDocumentQueryResult queryResult = null;
    try {
      queryResult = xenonClient.queryDocumentPage(pageLink);
    } catch (DocumentNotFoundException e) {
      throw new PageExpiredException(pageLink);
    }

    return PaginationUtils.xenonQueryResultToResourceList(
        PortGroupService.State.class,
        queryResult,
        state -> toApiRepresentation(state));
  }

  private PortGroupService.State findById(String id) throws PortGroupNotFoundException {
    try {
      com.vmware.xenon.common.Operation result = xenonClient.get(PortGroupServiceFactory.SELF_LINK + "/" + id);
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
