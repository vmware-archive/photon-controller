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

package com.vmware.photon.controller.cloudstore.dcp;

import com.vmware.photon.controller.cloudstore.CloudStoreConfig;
import com.vmware.photon.controller.cloudstore.CloudStoreConfigTest;
import com.vmware.photon.controller.cloudstore.dcp.entity.AttachedDiskServiceFactory;
import com.vmware.photon.controller.cloudstore.dcp.entity.AvailabilityZoneServiceFactory;
import com.vmware.photon.controller.cloudstore.dcp.entity.ClusterConfigurationServiceFactory;
import com.vmware.photon.controller.cloudstore.dcp.entity.ClusterServiceFactory;
import com.vmware.photon.controller.cloudstore.dcp.entity.DatastoreServiceFactory;
import com.vmware.photon.controller.cloudstore.dcp.entity.DeploymentServiceFactory;
import com.vmware.photon.controller.cloudstore.dcp.entity.DiskServiceFactory;
import com.vmware.photon.controller.cloudstore.dcp.entity.EntityLockServiceFactory;
import com.vmware.photon.controller.cloudstore.dcp.entity.FlavorServiceFactory;
import com.vmware.photon.controller.cloudstore.dcp.entity.HostServiceFactory;
import com.vmware.photon.controller.cloudstore.dcp.entity.ImageServiceFactory;
import com.vmware.photon.controller.cloudstore.dcp.entity.ImageToImageDatastoreMappingServiceFactory;
import com.vmware.photon.controller.cloudstore.dcp.entity.NetworkServiceFactory;
import com.vmware.photon.controller.cloudstore.dcp.entity.PortGroupServiceFactory;
import com.vmware.photon.controller.cloudstore.dcp.entity.ProjectServiceFactory;
import com.vmware.photon.controller.cloudstore.dcp.entity.ResourceTicketServiceFactory;
import com.vmware.photon.controller.cloudstore.dcp.entity.TaskServiceFactory;
import com.vmware.photon.controller.cloudstore.dcp.entity.TenantServiceFactory;
import com.vmware.photon.controller.cloudstore.dcp.entity.TombstoneServiceFactory;
import com.vmware.photon.controller.cloudstore.dcp.entity.VmServiceFactory;
import com.vmware.photon.controller.cloudstore.dcp.helpers.TestHelper;
import com.vmware.photon.controller.cloudstore.dcp.helpers.UpgradeHelper;
import com.vmware.photon.controller.cloudstore.dcp.task.AvailabilityZoneCleanerFactoryService;
import com.vmware.photon.controller.cloudstore.dcp.task.EntityLockCleanerFactoryService;
import com.vmware.photon.controller.cloudstore.dcp.task.TombstoneCleanerFactoryService;
import com.vmware.photon.controller.cloudstore.dcp.task.trigger.AvailabilityZoneCleanerTriggerBuilder;
import com.vmware.photon.controller.cloudstore.dcp.task.trigger.EntityLockCleanerTriggerBuilder;
import com.vmware.photon.controller.cloudstore.dcp.task.trigger.TombstoneCleanerTriggerBuilder;
import com.vmware.photon.controller.common.config.BadConfigException;
import com.vmware.photon.controller.common.config.ConfigBuilder;
import com.vmware.photon.controller.common.xenon.ServiceHostUtils;
import com.vmware.photon.controller.common.xenon.scheduler.TaskTriggerFactoryService;
import com.vmware.xenon.services.common.LuceneDocumentIndexService;
import com.vmware.xenon.services.common.RootNamespaceService;
import com.vmware.xenon.services.common.ServiceUriPaths;

import com.google.inject.Injector;
import org.apache.commons.io.FileUtils;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * This class implements tests for the {@link CloudStoreXenonHost} class.
 */
public class CloudStoreXenonHostTest {

  private static File storageDir;


  private static final String configFilePath = "/config.yml";
  /**
   * Maximum time to wait for all factories to become available.
   */
  private static final long SERVICES_STARTUP_TIMEOUT = TimeUnit.SECONDS.toMillis(30);
  private Injector injector;
  private CloudStoreXenonHost host;
  private String[] serviceSelfLinks = new String[]{
      FlavorServiceFactory.SELF_LINK,
      ImageServiceFactory.SELF_LINK,
      ImageToImageDatastoreMappingServiceFactory.SELF_LINK,
      HostServiceFactory.SELF_LINK,
      NetworkServiceFactory.SELF_LINK,
      DatastoreServiceFactory.SELF_LINK,
      DeploymentServiceFactory.SELF_LINK,
      PortGroupServiceFactory.SELF_LINK,
      TaskServiceFactory.SELF_LINK,
      EntityLockServiceFactory.SELF_LINK,
      ProjectServiceFactory.SELF_LINK,
      TenantServiceFactory.SELF_LINK,
      ResourceTicketServiceFactory.SELF_LINK,
      StatusService.SELF_LINK,
      VmServiceFactory.SELF_LINK,
      DiskServiceFactory.SELF_LINK,
      AttachedDiskServiceFactory.SELF_LINK,
      TombstoneServiceFactory.SELF_LINK,
      ClusterServiceFactory.SELF_LINK,
      ClusterConfigurationServiceFactory.SELF_LINK,
      AvailabilityZoneServiceFactory.SELF_LINK,

      // triggers
      TaskTriggerFactoryService.SELF_LINK,
      TaskTriggerFactoryService.SELF_LINK + EntityLockCleanerTriggerBuilder.TRIGGER_SELF_LINK,
      TaskTriggerFactoryService.SELF_LINK + TombstoneCleanerTriggerBuilder.TRIGGER_SELF_LINK,
      TaskTriggerFactoryService.SELF_LINK + AvailabilityZoneCleanerTriggerBuilder.TRIGGER_SELF_LINK,

      // tasks
      EntityLockCleanerFactoryService.SELF_LINK,
      TombstoneCleanerFactoryService.SELF_LINK,
      AvailabilityZoneCleanerFactoryService.SELF_LINK,

      // discovery
      RootNamespaceService.SELF_LINK,
  };

  /**
   * Dummy test case to make Intellij recognize this as a test class.
   */
  @Test
  private void dummy() {
  }

  /**
   * Tests for the constructors.
   */
  public class InitializationTest {

    @BeforeClass
    public void setUpClass() throws IOException, BadConfigException {
      CloudStoreConfig config = ConfigBuilder.build(CloudStoreConfig.class,
          CloudStoreConfigTest.class.getResource(configFilePath).getPath());
      storageDir = new File(config.getStoragePath());
      FileUtils.deleteDirectory(storageDir);
    }

    @BeforeMethod
    public void setUp() throws Exception {
      injector = TestHelper.createInjector(configFilePath);
    }

    @AfterMethod
    public void tearDown() throws Exception {
      FileUtils.deleteDirectory(storageDir);
    }

    @Test
    public void testStoragePathExists() throws IOException {
      // make sure folder exists
      storageDir.mkdirs();

      CloudStoreXenonHost host = injector.getInstance(CloudStoreXenonHost.class);
      assertThat(storageDir.exists(), is(true));
      assertThat(host, is(notNullValue()));
    }

    @Test
    public void testStoragePathDoesNotExist() throws Exception {
      // make sure folder does not exist
      FileUtils.deleteDirectory(storageDir);

      CloudStoreXenonHost host = injector.getInstance(CloudStoreXenonHost.class);
      assertThat(storageDir.exists(), is(true));
      assertThat(host, is(notNullValue()));
    }

    @Test
    public void testParams() {
      CloudStoreXenonHost host = injector.getInstance(CloudStoreXenonHost.class);
      assertThat(host.getPort(), is(19000));
      Path storagePath = Paths.get(storageDir.getPath()).resolve(Integer.toString(19000));
      assertThat(host.getStorageSandbox().getPath(), is(storagePath.toString()));
    }
  }

  /**
   * Tests for the start method.
   */
  public class StartTest {

    @BeforeClass
    private void setUpClass() throws IOException {
      FileUtils.deleteDirectory(storageDir);
    }

    @BeforeMethod
    private void setUp() throws Throwable {
      injector = TestHelper.createInjector(configFilePath);
      host = injector.getInstance(CloudStoreXenonHost.class);
    }

    @AfterMethod
    private void tearDown() throws Throwable {
      if (host != null) {
        host.stop();
      }
      FileUtils.deleteDirectory(storageDir);
    }

    @Test
    public void testStart() throws Throwable {
      host.start();

      try {
        ServiceHostUtils.waitForServiceAvailability(host, SERVICES_STARTUP_TIMEOUT, serviceSelfLinks.clone());
      } catch (TimeoutException e) {
        // we swallow up this exception so that down below we get a better message of what
        // service failed to start.
      }

      assertThat(host.checkServiceAvailable(ServiceUriPaths.DEFAULT_NODE_GROUP), is(true));
      assertThat(host.checkServiceAvailable(LuceneDocumentIndexService.SELF_LINK), is(true));
      assertThat(host.checkServiceAvailable(ServiceUriPaths.CORE_QUERY_TASKS), is(true));

      for (String selfLink : serviceSelfLinks) {
        assertThat(
            String.format("Failed to start service: %s", selfLink),
            host.checkServiceAvailable(selfLink),
            is(true));
      }

      assertThat(host.getClient().getConnectionLimitPerHost(),
          is(CloudStoreXenonHost.DEFAULT_CONNECTION_LIMIT_PER_HOST));
    }
  }

  /**
   * Tests for the isReady method.
   */
  public class IsReadyTest {

    @BeforeMethod
    private void setUp() throws Throwable {
      injector = TestHelper.createInjector(configFilePath);

      host = injector.getInstance(CloudStoreXenonHost.class);
      host.start();
      ServiceHostUtils.waitForServiceAvailability(host, SERVICES_STARTUP_TIMEOUT, serviceSelfLinks.clone());
    }

    @AfterMethod
    private void tearDown() throws Throwable {
      if (host != null) {
        host.stop();
      }
      FileUtils.deleteDirectory(storageDir);
    }

    @Test
    public void testAllReady() {
      assertThat(host.isReady(), is(true));
    }

  }

  /**
   * Tests to see if any breaking changes are done since the last benchmark.
   */
  public class UpgradeReadyTest {
    private final String safelyDeletedFieldsName = "SafelyDeletedFields";

    // Enable when you want to generate a benchmark file
    @Test(enabled = false)
    public void generateBenchmarkFile() throws Throwable {
      UpgradeHelper.generateBenchmarkFile();
    }

    @Test
    public void checkCurrentStateAgainstBenchmark() throws Throwable {
      Map<String, HashMap> previousState = UpgradeHelper.parseBenchmarkState();
      Map<String, HashMap> currentState = UpgradeHelper.populateCurrentState();

      for (Map.Entry<String, HashMap> prevState : previousState.entrySet()) {
        if (prevState.getKey().equals(safelyDeletedFieldsName)) {
          // Ignore this since it is not an actual entity class
          continue;
        }

        // Verify that the entity is here
        if (!currentState.containsKey(prevState.getKey())) {
          System.out.println("This entity is not found in the current system " + prevState.getKey());
        }
        assertThat(currentState.containsKey(prevState.getKey()), is(true));

        HashMap<String, String> currentFields = currentState.get(prevState.getKey());
        HashMap<String, String> prevFields = prevState.getValue();

        for (Map.Entry<String, String> prevField : prevFields.entrySet()) {
          // Verify that field is here not deleted, not renamed
          if (!currentFields.containsKey(prevField.getKey())) {
            // Check if it is safe to delete
            if (isSafeToDelete(previousState.get(safelyDeletedFieldsName), prevState.getKey(), prevField.getKey())) {
              continue;
            } else {
              System.out.println("This field " + prevField.getKey() +
                  " is not found in the current system on this entity " + prevState.getKey());
              assertThat(currentFields.containsKey(prevField.getKey()), is(true));
            }
          }

          String prevType = prevField.getValue();
          String currentType = currentFields.get(prevField.getKey());
          if (!prevType.equals(currentType) && !prevType.replace("esxcloud", "photon.controller").equals(currentType)) {
            // Now check if it is assignable
            Class prevFieldType = null;
            try {
              prevFieldType = Class.forName(prevType);
            } catch (ClassNotFoundException ex) {
              // May be its namespace is renamed
              prevFieldType = Class.forName(prevType.replace("esxcloud", "photon.controller"));
            }
            Class currentFieldType = Class.forName(currentType);

            System.out.println("This field " + prevField.getKey() + " has different type " + prevType
                + " in the current system " + currentType +
                "on this entity" + prevState.getKey());
            assertThat(currentFieldType.isAssignableFrom(prevFieldType), is(true));
          }
        }
      }
    }

    private boolean isSafeToDelete(HashMap<String, String> safeToDeleteFields, String entityName, String fieldName) {
      if (safeToDeleteFields == null) {
        return false;
      }

      if (!safeToDeleteFields.containsKey(entityName)) {
        return false;
      }

      String[] fields = safeToDeleteFields.get(entityName).split(",");
      for (String field : fields) {
        if (field.trim().equals(fieldName)) {
          return true;
        }
      }
      return false;
    }
  }
}
