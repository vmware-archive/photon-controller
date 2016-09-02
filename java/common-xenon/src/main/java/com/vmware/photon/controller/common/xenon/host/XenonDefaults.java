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

package com.vmware.photon.controller.common.xenon.host;

/**
 * This class implements basic configuration state for a Xenon host.
 */
public class XenonDefaults {
  /**
   * Default Xenon port.
   */
  public static final Integer PORT = 19000;

  /**
   * Default bind address for Xenon.
   */
  public static final String BIND_ADDRESS = "0.0.0.0";

  /**
   * Default storage path for Xenon.
   */
  public static final String STORAGE_PATH = "/etc/esxcloud/cloud-store/sandbox_19000";

  /**
   * Default key file.
   */
  public static final  String KEY_FILE = "/etc/keys/machine.privkey";

  /**
   * Default certificate file.
   */
  public static final  String CERTIFICATE_FILE = "/etc/keys/machine.crt";

}
