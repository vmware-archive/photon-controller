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

package com.vmware.photon.controller.clustermanager.servicedocuments;

import com.vmware.photon.controller.common.xenon.validation.Immutable;
import com.vmware.photon.controller.common.xenon.validation.NotNull;

import java.util.Map;

/**
 * This class defines a template used to create files with the specified parameters.
 */
public class FileTemplate {

  /**
   * This value represents the absolute path of the template file.
   */
  @NotNull
  @Immutable
  public String filePath;

  /**
   * This value represents a collection of parameters that are used to create the file from the template.
   */
  @NotNull
  @Immutable
  public Map<String, String> parameters;
}
