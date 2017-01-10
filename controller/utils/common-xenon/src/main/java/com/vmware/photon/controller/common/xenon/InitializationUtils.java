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

package com.vmware.photon.controller.common.xenon;

import com.vmware.photon.controller.common.xenon.validation.DefaultBooleanInitializer;
import com.vmware.photon.controller.common.xenon.validation.DefaultIntegerInitializer;
import com.vmware.photon.controller.common.xenon.validation.DefaultLongInitializer;
import com.vmware.photon.controller.common.xenon.validation.DefaultStringInitializer;
import com.vmware.photon.controller.common.xenon.validation.DefaultTaskStateInitializer;
import com.vmware.photon.controller.common.xenon.validation.DefaultUuidInitializer;
import com.vmware.xenon.common.ServiceDocument;

/**
 * This class implements utilities to fill in default values.
 */
public class InitializationUtils {

  public static void initialize(ServiceDocument startState) throws RuntimeException {
    DefaultBooleanInitializer.initialize(startState);
    DefaultIntegerInitializer.initialize(startState);
    DefaultUuidInitializer.initialize(startState);
    DefaultTaskStateInitializer.initialize(startState);
    DefaultStringInitializer.initialize(startState);
    DefaultLongInitializer.initialize(startState);
  }
}
