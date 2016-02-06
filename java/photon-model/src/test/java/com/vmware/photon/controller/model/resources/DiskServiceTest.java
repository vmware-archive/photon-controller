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

package com.vmware.photon.controller.model.resources;

import com.vmware.photon.controller.model.ModelServices;
import com.vmware.photon.controller.model.helpers.BaseModelTest;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.ServiceDocumentDescription;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.QueryTask;
import com.vmware.xenon.services.common.TenantFactoryService;

import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.testng.Assert.assertNotNull;

import java.net.URI;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.UUID;

/**
 * This class implements tests for the {@link DiskService} class.
 */
public class DiskServiceTest {

  private DiskService.DiskState buildValidStartState() throws Throwable {
    DiskService.DiskState disk = new DiskService.DiskState();

    disk.id = UUID.randomUUID().toString();
    disk.type = DiskService.DiskType.HDD;
    disk.name = "friendly-name";
    disk.capacityMBytes = 100L;

    return disk;
  }

  @Test
  private void dummy() {
  }

  /**
   * This class implements tests for the constructor.
   */
  public class ConstructorTest {

    private DiskService diskService;

    @BeforeMethod
    public void setUpTest() {
      diskService = new DiskService();
    }

    @Test
    public void testServiceOptions() {

      EnumSet<Service.ServiceOption> expected = EnumSet.of(
          Service.ServiceOption.CONCURRENT_GET_HANDLING,
          Service.ServiceOption.OWNER_SELECTION,
          Service.ServiceOption.PERSISTENCE,
          Service.ServiceOption.REPLICATION);

      assertThat(diskService.getOptions(), is(expected));
    }
  }

  /**
   * This class implements tests for the handleStart method.
   */
  public class HandleStartTest extends BaseModelTest {
    @Override
    protected Class[] getFactoryServices() {
      return ModelServices.FACTORIES;
    }

    @Test
    public void testValidStartState() throws Throwable {
      DiskService.DiskState startState = buildValidStartState();
      DiskService.DiskState returnState = host.postServiceSynchronously(
          DiskFactoryService.SELF_LINK,
          startState,
          DiskService.DiskState.class);

      assertNotNull(returnState);
      assertThat(returnState.id, is(startState.id));
      assertThat(returnState.name, is(startState.name));
      assertThat(returnState.type, is(startState.type));
      assertThat(returnState.capacityMBytes, is(startState.capacityMBytes));
    }

    @Test
    public void testMissingId() throws Throwable {
      DiskService.DiskState startState = buildValidStartState();
      startState.id = null;

      DiskService.DiskState returnState = host.postServiceSynchronously(
          DiskFactoryService.SELF_LINK,
          startState,
          DiskService.DiskState.class);

      assertNotNull(returnState);
      assertNotNull(returnState.id);
    }

    @Test
    public void testMissingName() throws Throwable {
      DiskService.DiskState startState = buildValidStartState();
      startState.name = null;

      host.postServiceSynchronously(
          DiskFactoryService.SELF_LINK,
          startState,
          DiskService.DiskState.class,
          IllegalArgumentException.class);
    }

    @Test
    public void testMissingType() throws Throwable {
      DiskService.DiskState startState = buildValidStartState();
      startState.type = null;

      host.postServiceSynchronously(
          DiskFactoryService.SELF_LINK,
          startState,
          DiskService.DiskState.class,
          IllegalArgumentException.class);
    }

    @Test(dataProvider = "capacityMBytes")
    public void testCapacityLessThanOneMB(Long capacityMBytes) throws Throwable {
      DiskService.DiskState startState = buildValidStartState();
      startState.capacityMBytes = capacityMBytes;
      startState.sourceImageReference = new URI("http://sourceImageReference");
      startState.customizationServiceReference = new URI("http://customizationServiceReference");

      DiskService.DiskState returnState = host.postServiceSynchronously(
          DiskFactoryService.SELF_LINK,
          startState,
          DiskService.DiskState.class);

      assertNotNull(returnState);
    }

    @DataProvider(name = "capacityMBytes")
    public Object[][] getCapacityMBytes() {
      return new Object[][]{
          {1L},
          {0L}
      };
    }

    @Test
    public void testMissingTwoReferencesWhenCapacityLessThanOneMB()
        throws Throwable {
      DiskService.DiskState startState = buildValidStartState();
      startState.capacityMBytes = 0;
      startState.sourceImageReference = null;
      startState.customizationServiceReference = null;

      host.postServiceSynchronously(
          DiskFactoryService.SELF_LINK,
          startState,
          DiskService.DiskState.class,
          IllegalArgumentException.class);
    }

    @Test
    public void testMissingStatus() throws Throwable {
      DiskService.DiskState startState = buildValidStartState();
      startState.status = null;

      DiskService.DiskState returnState = host.postServiceSynchronously(
          DiskFactoryService.SELF_LINK,
          startState,
          DiskService.DiskState.class);

      assertNotNull(returnState);
      assertThat(returnState.status, is(DiskService.DiskStatus.DETACHED));
    }

    @Test(dataProvider = "fileEntryPaths")
    public void testMissingPathInFileEntry(String path) throws Throwable {
      DiskService.DiskState startState = buildValidStartState();
      startState.bootConfig = new DiskService.DiskState.BootConfig();
      startState.bootConfig.files = new DiskService.DiskState.BootConfig.FileEntry[1];
      startState.bootConfig.files[0] = new DiskService.DiskState.BootConfig.FileEntry();
      startState.bootConfig.files[0].path = path;

      host.postServiceSynchronously(
          DiskFactoryService.SELF_LINK,
          startState,
          DiskService.DiskState.class,
          IllegalArgumentException.class);
    }

    @DataProvider(name = "fileEntryPaths")
    public Object[][] getFileEntryPaths() {
      return new Object[][]{
          {null},
          {""}
      };
    }
  }

  /**
   * This class implements tests for the handlePatch method.
   */
  public class HandlePatchTest extends BaseModelTest {
    @Override
    protected Class[] getFactoryServices() {
      return ModelServices.FACTORIES;
    }

    @Test(dataProvider = "patchZoneId")
    public void testPatchZoneId(String patchValue, String expectedValue) throws Throwable {
      DiskService.DiskState startState = buildValidStartState();
      startState.zoneId = "startZoneId";

      DiskService.DiskState returnState = host.postServiceSynchronously(
          DiskFactoryService.SELF_LINK,
          startState,
          DiskService.DiskState.class);

      DiskService.DiskState patchState = new DiskService.DiskState();
      patchState.zoneId = patchValue;

      host.patchServiceSynchronously(
          returnState.documentSelfLink,
          patchState);
      returnState = host.getServiceSynchronously(
          returnState.documentSelfLink,
          DiskService.DiskState.class);
      assertThat(returnState.zoneId, is(expectedValue));
    }

    @DataProvider(name = "patchZoneId")
    public Object[][] getPatchZoneId() {
      return new Object[][]{
          {null, "startZoneId"},
          {"startZoneId", "startZoneId"},
          {"patchZoneId", "patchZoneId"}
      };
    }

    @Test(dataProvider = "patchName")
    public void testPatchName(String patchValue, String expectedValue) throws Throwable {
      DiskService.DiskState startState = buildValidStartState();
      startState.name = "startName";

      DiskService.DiskState returnState = host.postServiceSynchronously(
          DiskFactoryService.SELF_LINK,
          startState,
          DiskService.DiskState.class);

      DiskService.DiskState patchState = new DiskService.DiskState();
      patchState.name = patchValue;

      host.patchServiceSynchronously(
          returnState.documentSelfLink,
          patchState);
      returnState = host.getServiceSynchronously(
          returnState.documentSelfLink,
          DiskService.DiskState.class);
      assertThat(returnState.name, is(expectedValue));
    }

    @DataProvider(name = "patchName")
    public Object[][] getPatchName() {
      return new Object[][]{
          {null, "startName"},
          {"startName", "startName"},
          {"patchName", "patchName"}
      };
    }

    @Test(dataProvider = "patchStatus")
    public void testPatchStatus(DiskService.DiskStatus patchValue, DiskService.DiskStatus expectedValue)
        throws Throwable {
      DiskService.DiskState startState = buildValidStartState();
      startState.status = DiskService.DiskStatus.DETACHED;

      DiskService.DiskState returnState = host.postServiceSynchronously(
          DiskFactoryService.SELF_LINK,
          startState,
          DiskService.DiskState.class);

      DiskService.DiskState patchState = new DiskService.DiskState();
      patchState.status = patchValue;

      host.patchServiceSynchronously(
          returnState.documentSelfLink,
          patchState);
      returnState = host.getServiceSynchronously(
          returnState.documentSelfLink,
          DiskService.DiskState.class);
      assertThat(returnState.status, is(expectedValue));
    }

    @DataProvider(name = "patchStatus")
    public Object[][] getPatchStatus() {
      return new Object[][]{
          {null, DiskService.DiskStatus.DETACHED},
          {DiskService.DiskStatus.DETACHED, DiskService.DiskStatus.DETACHED},
          {DiskService.DiskStatus.ATTACHED, DiskService.DiskStatus.ATTACHED}
      };
    }

    @Test(dataProvider = "patchCapacityMBytes")
    public void testPatchCapacityMBytes(Long patchValue, Long expectedValue) throws Throwable {
      DiskService.DiskState startState = buildValidStartState();
      startState.capacityMBytes = 100L;

      DiskService.DiskState returnState = host.postServiceSynchronously(
          DiskFactoryService.SELF_LINK,
          startState,
          DiskService.DiskState.class);

      DiskService.DiskState patchState = new DiskService.DiskState();
      patchState.capacityMBytes = patchValue;

      host.patchServiceSynchronously(
          returnState.documentSelfLink,
          patchState);
      returnState = host.getServiceSynchronously(
          returnState.documentSelfLink,
          DiskService.DiskState.class);
      assertThat(returnState.capacityMBytes, is(expectedValue));
    }

    @DataProvider(name = "patchCapacityMBytes")
    public Object[][] getPatchCapacityMBytes() {
      return new Object[][]{
          {0L, 100L},
          {100L, 100L},
          {200L, 200L}
      };
    }

    @Test
    public void testPatchOtherFields() throws Throwable {
      DiskService.DiskState startState = buildValidStartState();
      startState.dataCenterId = "data-center-id1";
      startState.resourcePoolLink = "resource-pool-link1";
      startState.authCredentialsLink = "auth-credentials-link1";
      startState.customProperties = new HashMap<>();
      startState.customProperties.put("cp1-key", "cp1-value");
      startState.tenantLinks = new ArrayList<>();
      startState.tenantLinks.add("tenant-link1");
      startState.bootOrder = 1;
      startState.bootArguments = new String[]{"boot-argument1"};
      startState.currencyUnit = "currency-unit1";

      DiskService.DiskState returnState = host.postServiceSynchronously(
          DiskFactoryService.SELF_LINK,
          startState,
          DiskService.DiskState.class);

      DiskService.DiskState patchState = new DiskService.DiskState();
      patchState.dataCenterId = "data-center-id2";
      patchState.resourcePoolLink = "resource-pool-link2";
      patchState.authCredentialsLink = "auth-credentials-link2";
      patchState.customProperties = new HashMap<>();
      patchState.customProperties.put("cp2-key", "cp2-value");
      patchState.tenantLinks = new ArrayList<>();
      patchState.tenantLinks.add("tenant-link2");
      patchState.bootOrder = 2;
      patchState.bootArguments = new String[]{"boot-argument2"};
      patchState.currencyUnit = "currency-unit2";

      host.patchServiceSynchronously(
          returnState.documentSelfLink,
          patchState);
      returnState = host.getServiceSynchronously(
          returnState.documentSelfLink,
          DiskService.DiskState.class);
      assertThat(returnState.dataCenterId, is(startState.dataCenterId));
      assertThat(returnState.resourcePoolLink, is(startState.resourcePoolLink));
      assertThat(returnState.authCredentialsLink, is(startState.authCredentialsLink));
      assertThat(returnState.customProperties, is(startState.customProperties));
      assertThat(returnState.tenantLinks, is(startState.tenantLinks));
      assertThat(returnState.bootOrder, is(startState.bootOrder));
      assertThat(returnState.bootArguments, is(startState.bootArguments));
      assertThat(returnState.currencyUnit, is(startState.currencyUnit));

    }
  }

  /**
   * This class implements tests for query.
   */
  public class QueryTest extends BaseModelTest {

    @Override
    protected Class[] getFactoryServices() {
      return ModelServices.FACTORIES;
    }

    @Test
    public void testTenantLinksQuery() throws Throwable {
      DiskService.DiskState disk = buildValidStartState();

      URI tenantUri = UriUtils.buildUri(host, TenantFactoryService.class);
      disk.tenantLinks = new ArrayList<>();
      disk.tenantLinks.add(UriUtils.buildUriPath(tenantUri.getPath(), "tenantA"));

      DiskService.DiskState startState = host.postServiceSynchronously(
          DiskFactoryService.SELF_LINK, disk, DiskService.DiskState.class);

      String kind = Utils.buildKind(DiskService.DiskState.class);
      String propertyName = QueryTask.QuerySpecification
          .buildCollectionItemName(ServiceDocumentDescription.FIELD_NAME_TENANT_LINKS);

      QueryTask q = host.createDirectQueryTask(kind, propertyName, disk.tenantLinks.get(0));
      q = host.querySynchronously(q);
      assertNotNull(q.results.documentLinks);
      assertThat(q.results.documentCount, is(1L));
      assertThat(q.results.documentLinks.get(0), is(startState.documentSelfLink));
    }
  }
}
