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

package com.vmware.photon.controller.model.helpers;

import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Abstract base class that creates a DCP ServiceHost
 * running all the model DCP services for unit-tests.
 */
public abstract class BaseModelTest {

  private static final int HOST_PORT = 0;
  private static final Logger logger = LoggerFactory.getLogger(BaseModelTest.class);

  protected TestHost host;
  private Path sandboxDirectory;

  protected abstract Class[] getFactoryServices();

  @BeforeClass
  public void setUpClass() throws Throwable {
    if (host == null) {
      sandboxDirectory = Files.createTempDirectory(null);
      host = new TestHost(HOST_PORT, sandboxDirectory, getFactoryServices());
      host.start();
    }
  }

  @AfterClass
  public void tearDownClass() throws Throwable {
    if (host != null) {
      host.stop();
      host = null;
    }
    File sandbox = new File(sandboxDirectory.toUri());
    if (sandbox.exists()) {
      try {
        FileUtils.forceDelete(sandbox);
      } catch (FileNotFoundException | IllegalArgumentException ex) {
        logger.debug("Sandbox file was not found");
      } catch (IOException ex) {
        FileUtils.forceDeleteOnExit(sandbox);
      }
    }
  }
}
