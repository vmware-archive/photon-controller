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

package com.vmware.photon.controller.common.extensions.impl;

import com.vmware.photon.controller.common.extensions.ExtensionLoader;
import com.vmware.photon.controller.common.extensions.config.Implementations;
import com.vmware.photon.controller.common.extensions.exceptions.AmbiguousExtensionException;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * Test {@link Utils}.
 */
public class Utils {

  private void setUp() {
  }

  private void tearDown() {
  }

  public static Class<?> load(Implementations.ExtensionName className) {
    String classPath = Implementations.nameToPath.get(className);
    assertThat(classPath, not(is(nullValue())));
    Class<?> classImpl = null;
    try {
      classImpl = ExtensionLoader.load(classPath);
    } catch (ClassNotFoundException e) {
      assertThat(e, is(nullValue()));
    } catch (AmbiguousExtensionException e) {
      assertThat(e, is(nullValue()));
    }
    assertThat(classImpl, not(is(nullValue())));
    return classImpl;
  }
}
