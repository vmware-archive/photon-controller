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

package com.vmware.photon.controller.apife.config;

import com.vmware.photon.controller.api.model.Component;

import java.util.Collection;
import java.util.EnumSet;
import java.util.Set;

/**
 * Configures which statuses to check when calling /v1/status endpoint.
 * When not configured, default to show status of all components.
 */
public class StatusConfig {

  private static final Set<Component> DEFAULT = EnumSet.allOf(Component.class);

  private Set<Component> components = DEFAULT;

  public Set<Component> getComponents() {
    return components;
  }

  public void setComponents(Collection<String> components) {

    if (components == null || components.isEmpty()) {
      this.components = DEFAULT;
    } else {
      this.components = Component.fromStrings(components);
    }

  }
}
