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

package com.vmware.photon.controller.deployer.xenon;

import com.vmware.photon.controller.common.config.BadConfigException;
import com.vmware.photon.controller.common.config.ConfigBuilder;
import com.vmware.photon.controller.deployer.DeployerConfig;
import com.vmware.photon.controller.deployer.helpers.TestHelper;
import com.vmware.xenon.common.Utils;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.github.mustachejava.DefaultMustacheFactory;
import com.github.mustachejava.Mustache;
import com.github.mustachejava.MustacheFactory;
import com.google.common.collect.ImmutableMap;
import org.apache.commons.io.FileUtils;
import org.testng.annotations.Test;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Implements tests for {@link ContainersConfig}.
 */
public class ContainersConfigTest {

  private ContainersConfig containersConfig;
  private DeployerConfig deployerConfig;

  private static Map<String, Object> defaultDynamicParamters = ImmutableMap.<String, Object>builder()
      .put("REGISTRATION_ADDRESS", "99.99.99.99")
      .put("ZOOKEEPER_QUORUM", "zk1:999")
      .put("ENABLE_SYSLOG", false)
      .build();

  @Test
  public void constructsContainersConfig() throws Exception {
    deployerConfig = ConfigBuilder.build(DeployerConfig.class,
        this.getClass().getResource("/config.yml").getPath());
    TestHelper.setContainersConfig(deployerConfig);
    containersConfig = deployerConfig.getContainersConfig();
    assertThat(containersConfig.getContainerSpecs().size(), is(ContainersConfig.ContainerType.values().length));
  }

  @Test(expectedExceptions = BadConfigException.class)
  public void throwsOnMissingContainersConfig() throws Exception {
    containersConfig = ConfigBuilder.build(DeployerConfig.class,
        this.getClass().getResource("/xenonConfig_invalid.yml").getPath()).getContainersConfig();
  }

  @Test
  public void buildsValidConfgiuration() throws Exception {
    String path = this.getClass().getResource("/configurations").getPath();
    List<File> leafDirectories = findAllLeafDirectories(new File(path));
    for (File dir : leafDirectories) {
      File configYml = findFile(dir, "yml");
      if (configYml != null) {
        File releaseJson = findFile(dir, "release.json");
        Map<String, Object> dynamicParamters = new HashMap<>(defaultDynamicParamters);
        @SuppressWarnings("unchecked")
        Map<String, Object> d = Utils.fromJson(FileUtils.readFileToString(releaseJson), Map.class);
        dynamicParamters.putAll(d);
        MustacheFactory mustacheFactory = new DefaultMustacheFactory();
        Mustache mustache = mustacheFactory
            .compile(new InputStreamReader(new FileInputStream(configYml)), configYml.getName());
        StringWriter stringWriter = new StringWriter();
        mustache.execute(stringWriter, dynamicParamters).flush();
        stringWriter.flush();

        // validating that the created yml file can be read
        ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
        mapper.readValue(stringWriter.toString(), Object.class);
      }
    }
  }

  private File findFile(File dir, String suffix) {
    File[] listFiles = dir.listFiles(f -> f.getName().endsWith(suffix));
    if (listFiles == null || listFiles.length == 0) {
      return null;
    }
    return listFiles[0];
  }

  private List<File> findAllLeafDirectories(File path) {
    if (path.isFile()) {
      return Collections.emptyList();
    }

    if (path.listFiles(f -> f.isDirectory()).length == 0) {
      return Arrays.asList(path);
    }

    List<File> leafDirectories = new ArrayList<>();
    for (File dir : path.listFiles(f -> f.isDirectory())) {
      leafDirectories.addAll(findAllLeafDirectories(dir));
    }
    return leafDirectories;
  }
}
