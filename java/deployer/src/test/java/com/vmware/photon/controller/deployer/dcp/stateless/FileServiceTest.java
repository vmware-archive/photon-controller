/*
 * Copyright 2016 VMware, Inc. All Rights Reserved.
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

package com.vmware.photon.controller.deployer.dcp.stateless;

import com.vmware.photon.controller.common.config.ConfigBuilder;
import com.vmware.photon.controller.common.xenon.exceptions.XenonRuntimeException;
import com.vmware.photon.controller.deployer.DeployerConfig;
import com.vmware.photon.controller.deployer.helpers.xenon.TestEnvironment;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.UriUtils;

import org.apache.commons.io.FileUtils;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

import java.io.File;
import java.util.UUID;

/**
 * This class implements test for the {@link FileService} class.
 */
public class FileServiceTest {
  private static final String TEST_FILE_CONTENT = "This is the correct\nFilecontent";
  private static final String TEST_FILE_NAME = "test.file";
  private static final String NON_EXISTING_TEST_FILE_NAME = TEST_FILE_NAME + "-not";
  private final File storageDirectory = new File("/tmp/deployAgent" + UUID.randomUUID().toString().substring(0, 6));
  private final File scriptDirectory = new File(storageDirectory, "scripts");
  private final File scriptLogDirectory = new File(storageDirectory, "logs");

  private TestEnvironment machine;
  private DeployerConfig deployerConfig;
  private File vibDirectory;

  @BeforeClass
  public void setUpClass() throws Throwable {
    FileUtils.deleteDirectory(storageDirectory);
  }

  @BeforeMethod
  public void setUpTest() throws Throwable {
    deployerConfig = ConfigBuilder.build(DeployerConfig.class, this.getClass().getResource("/config.yml").getPath());
    vibDirectory = new File(deployerConfig.getDeployerContext().getVibDirectory());
    FileUtils.deleteDirectory(vibDirectory);
    vibDirectory.mkdirs();
    scriptDirectory.mkdirs();
    scriptLogDirectory.mkdirs();
    FileUtils.writeStringToFile(new File(vibDirectory, TEST_FILE_NAME), TEST_FILE_CONTENT);
    machine = new TestEnvironment.Builder()
        .hostCount(1)
        .deployerContext(deployerConfig.getDeployerContext())
        .build();
  }

  @AfterMethod
  public void tearDownTest() throws Throwable {
    if (null != machine) {
      machine.stop();
      machine = null;
    }

    FileUtils.deleteDirectory(storageDirectory);
    FileUtils.deleteDirectory(vibDirectory);
  }

  @Test
  public void successDownloadingFile() throws Throwable {
    Operation get = Operation
        .createGet(UriUtils.buildUri(machine.getHosts()[0], FileService.SELF_LINK + "/" + TEST_FILE_NAME));

    Operation response = machine.sendRequestAndWait(get, machine.getHosts()[0]);

    String body = response.getBody(String.class);

    assertThat(body.trim(), is(TEST_FILE_CONTENT));
  }

  @Test(expectedExceptions = {XenonRuntimeException.class},
      expectedExceptionsMessageRegExp = ".*" + NON_EXISTING_TEST_FILE_NAME + ".*(No such file or directory).*")
  public void failedDownloadingNonExistingFile() throws Throwable {
    Operation get = Operation
        .createGet(UriUtils
            .buildUri(machine.getHosts()[0], FileService.SELF_LINK + "/" + NON_EXISTING_TEST_FILE_NAME));
    machine.sendRequestAndWait(get, machine.getHosts()[0]);
  }

}
