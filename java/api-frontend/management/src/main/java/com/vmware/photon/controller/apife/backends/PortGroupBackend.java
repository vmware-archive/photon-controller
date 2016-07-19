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

import com.vmware.photon.controller.api.common.exceptions.external.ExternalException;
import com.vmware.photon.controller.api.model.PortGroup;
import com.vmware.photon.controller.api.model.ResourceList;
import com.vmware.photon.controller.api.model.UsageTag;
import com.vmware.photon.controller.apife.exceptions.external.PortGroupNotFoundException;

import com.google.common.base.Optional;

/**
 * Perform port group related operations.
 */
public interface PortGroupBackend {

  ResourceList<PortGroup> filter(Optional<String> name, Optional<UsageTag> usageTag, Optional<Integer> pageSize);

  PortGroup toApiRepresentation(String id) throws PortGroupNotFoundException;

  ResourceList<PortGroup> getPortGroupsPage(String pageLink) throws ExternalException;
}
