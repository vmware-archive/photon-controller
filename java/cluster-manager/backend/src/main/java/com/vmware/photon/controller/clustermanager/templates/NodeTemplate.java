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

package com.vmware.photon.controller.clustermanager.templates;

import com.vmware.photon.controller.clustermanager.servicedocuments.FileTemplate;

import java.util.Map;

/**
 * Defines a template that is used for provisioning a node in the cluster.
 */
public interface NodeTemplate {

  /**
   * The name that will be used for the Node VM.
   *
   * @param properties          Property bag used to define the parameters of the user-data template
   * @return
   */
  String getVmName(Map<String, String> properties);

  /**
   * Creates the cloud-config's user-data file template.
   *
   * @param scriptDirectory     Location of the Scripts directory.
   * @param properties          Property bag used to define the parameters of the user-data template
   * @return
   */
  FileTemplate createUserDataTemplate(String scriptDirectory, Map<String, String> properties);

  /**
   * Creates the cloud-config's meta-data file template.
   *
   * @param scriptDirectory     Location of the Scripts directory.
   * @param properties          Property bag used to define the parameters of the meta-data template
   * @return
   */
  FileTemplate createMetaDataTemplate(String scriptDirectory, Map<String, String> properties);
}
