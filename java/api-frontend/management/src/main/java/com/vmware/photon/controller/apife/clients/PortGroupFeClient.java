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

package com.vmware.photon.controller.apife.clients;

import com.vmware.photon.controller.api.common.exceptions.external.ExternalException;
import com.vmware.photon.controller.api.model.PortGroup;
import com.vmware.photon.controller.api.model.ResourceList;
import com.vmware.photon.controller.api.model.UsageTag;
import com.vmware.photon.controller.apife.backends.PortGroupBackend;

import com.google.common.base.Optional;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * Frontend client for PortGroup used by {@link PortGroupResource}.
 */
@Singleton
public class PortGroupFeClient {
  private static final Logger logger = LoggerFactory.getLogger(PortGroupFeClient.class);

  private final PortGroupBackend portGroupBackend;

  @Inject
  public PortGroupFeClient(PortGroupBackend portGroupBackend) {
    this.portGroupBackend = portGroupBackend;
  }

  public ResourceList<PortGroup> find(Optional<String> name, Optional<UsageTag> usageTag, Optional<Integer> pageSize) {
    logger.info("find port groups of name {} and usageTag {}", name.orNull(), usageTag.orNull());
    return portGroupBackend.filter(name, usageTag, pageSize);
  }

  public PortGroup get(String id) throws ExternalException {
    return portGroupBackend.toApiRepresentation(id);
  }

  public ResourceList<PortGroup> getPortGroupsPage(String pageLink) throws ExternalException {
    return portGroupBackend.getPortGroupsPage(pageLink);
  }
}
