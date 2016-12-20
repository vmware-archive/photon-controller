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

package com.vmware.photon.controller.api.frontend.lib.image;

import org.testng.annotations.Test;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

import java.util.HashMap;
import java.util.Map;

/**
 * Tests {@link EsxCloudVmx}.
 */
public class EsxCloudVmxTest {

  private EsxCloudVmx createEsxCloudVmx() {
    EsxCloudVmx ecv = new EsxCloudVmx();
    for (int i = 0; i < 5; i++) {
      ecv.configuration.put("configuration" + i, "configValue" + i);
      ecv.parameters.add(new EsxCloudVmx.Property("p" + i));
    }

    return ecv;
  }

  private Map<String, String> createImageSettings() {
    Map<String, String> imageSettings = new HashMap<>();
    for (int i = 0; i < 5; i++) {
      imageSettings.put("configuration" + i, "configValue" + i);
      imageSettings.put("p" + i, null);
    }

    return imageSettings;
  }

  @Test
  public void testToImageSettings() {
    assertThat(EsxCloudVmx.toImageSettings(createEsxCloudVmx()), is(createImageSettings()));
  }

  @Test
  public void testFromImageSettings() {
    assertThat(EsxCloudVmx.fromImageSettings(createImageSettings()), is(createEsxCloudVmx()));
  }

}
