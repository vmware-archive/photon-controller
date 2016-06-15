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

package com.vmware.photon.controller.apife.backends;

import com.vmware.photon.controller.api.QuotaLineItem;
import com.vmware.photon.controller.api.QuotaUnit;
import com.vmware.photon.controller.api.ResourceList;
import com.vmware.photon.controller.api.ResourceTicket;
import com.vmware.photon.controller.api.ResourceTicketCreateSpec;
import com.vmware.photon.controller.api.common.exceptions.external.ErrorCode;
import com.vmware.photon.controller.api.common.exceptions.external.ExternalException;
import com.vmware.photon.controller.apife.Data;
import com.vmware.photon.controller.apife.TestModule;
import com.vmware.photon.controller.apife.backends.clients.ApiFeXenonRestClient;
import com.vmware.photon.controller.apife.config.PaginationConfig;
import com.vmware.photon.controller.apife.entities.QuotaLineItemEntity;
import com.vmware.photon.controller.apife.entities.ResourceTicketEntity;
import com.vmware.photon.controller.apife.exceptions.external.InvalidResourceTicketSubdivideException;
import com.vmware.photon.controller.apife.exceptions.external.NameTakenException;
import com.vmware.photon.controller.apife.exceptions.external.QuotaException;
import com.vmware.photon.controller.apife.exceptions.external.ResourceTicketNotFoundException;
import com.vmware.photon.controller.apife.lib.QuotaCost;
import com.vmware.photon.controller.cloudstore.xenon.entity.ResourceTicketService;
import com.vmware.photon.controller.cloudstore.xenon.entity.ResourceTicketServiceFactory;
import com.vmware.photon.controller.common.xenon.BasicServiceHost;
import com.vmware.photon.controller.common.xenon.ServiceHostUtils;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.inject.Inject;
import org.junit.AfterClass;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Guice;
import org.testng.annotations.Test;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.testng.Assert.fail;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Tests {@link ResourceTicketXenonBackend}.
 */
public class ResourceTicketXenonBackendTest {

  private static ApiFeXenonRestClient xenonClient;
  private static BasicServiceHost host;

  private static void commonHostAndClientSetup(
      BasicServiceHost basicServiceHost, ApiFeXenonRestClient apiFeXenonRestClient) {
    host = basicServiceHost;
    xenonClient = apiFeXenonRestClient;

    if (host == null) {
      throw new IllegalStateException(
          "host is not expected to be null in this test setup");
    }

    if (xenonClient == null) {
      throw new IllegalStateException(
          "xenonClient is not expected to be null in this test setup");
    }

    if (!host.isReady()) {
      throw new IllegalStateException(
          "host is expected to be in started state, current state=" + host.getState());
    }
  }

  private static void commonHostDocumentsCleanup() throws Throwable {
    if (host != null) {
      ServiceHostUtils.deleteAllDocuments(host, "test-host");
    }
  }

  private static void commonHostAndClientTeardown() throws Throwable {
    if (xenonClient != null) {
      xenonClient.stop();
      xenonClient = null;
    }

    if (host != null) {
      host.destroy();
      host = null;
    }
  }

  @Test
  private void dummy() {
  }

  /**
   * Tests for creating tickets.
   */
  @Guice(modules = {XenonBackendTestModule.class, TestModule.class})
  public static class CreateTicketTest {

    @Inject
    private BasicServiceHost basicServiceHost;

    @Inject
    private ApiFeXenonRestClient apiFeXenonRestClient;

    @Inject
    private ResourceTicketBackend resourceTicketBackend;

    @Inject
    private TenantXenonBackend tenantXenonBackend;

    @BeforeMethod
    public void setUp() throws Throwable {
      commonHostAndClientSetup(basicServiceHost, apiFeXenonRestClient);
    }

    @AfterMethod
    public void tearDown() throws Throwable {
      commonHostDocumentsCleanup();
    }

    @AfterClass
    public static void afterClassCleanup() throws Throwable {
      commonHostAndClientTeardown();
    }

    @Test
    public void testCreateTicket() throws Throwable {
      final String tenantId = XenonBackendTestHelper.createTenant(tenantXenonBackend, "t1");

      ResourceTicketCreateSpec spec = new ResourceTicketCreateSpec();
      spec.setName("rt1");
      spec.setLimits(ImmutableList.of(new QuotaLineItem("vm", 10, QuotaUnit.COUNT)));

      ResourceTicketEntity resourceTicketEntity = resourceTicketBackend.create(tenantId, spec);

      assertThat(resourceTicketEntity.getName(), is(spec.getName()));
      assertThat(resourceTicketEntity.getTenantId(), is(tenantId));

      String key = spec.getLimits().get(0).getKey();
      Double value = spec.getLimits().get(0).getValue();
      QuotaUnit unit = spec.getLimits().get(0).getUnit();

      assertThat(resourceTicketEntity.getLimit(key).getValue(), is(value));
      assertThat(resourceTicketEntity.getLimit(key).getUnit(), is(unit));
    }

    @Test
    public void testCreateTicketWhenNameIsAlreadyTaken() throws Throwable {
      final String tenantId = XenonBackendTestHelper.createTenant(tenantXenonBackend, "t1");

      ResourceTicketCreateSpec spec = new ResourceTicketCreateSpec();
      spec.setName("rt1");
      spec.setLimits(ImmutableList.of(new QuotaLineItem("vm", 10, QuotaUnit.COUNT)));

      resourceTicketBackend.create(tenantId, spec);
      try {
        resourceTicketBackend.create(tenantId, spec);
        fail("Ticket creation with name already taken should have failed");
      } catch (NameTakenException e) {
        assertThat(e.getMessage(), containsString(spec.getName()));
      }
    }
  }

  /**
   * Tests for getting tickets.
   */
  @Guice(modules = {XenonBackendTestModule.class, TestModule.class})
  public static class GetTicketTest {

    @Inject
    private BasicServiceHost basicServiceHost;

    @Inject
    private ApiFeXenonRestClient apiFeXenonRestClient;

    @Inject
    private ResourceTicketBackend resourceTicketBackend;

    @Inject
    private TenantXenonBackend tenantXenonBackend;

    private ResourceTicketEntity resourceTicketEntity;
    private String tenantId;
    private ResourceTicketCreateSpec spec;

    @BeforeMethod
    public void setUp() throws Throwable {
      commonHostAndClientSetup(basicServiceHost, apiFeXenonRestClient);

      tenantId = XenonBackendTestHelper.createTenant(tenantXenonBackend, "t1");

      spec = new ResourceTicketCreateSpec();
      spec.setName("rt1");
      spec.setLimits(ImmutableList.of(new QuotaLineItem("vm", 10, QuotaUnit.COUNT)));

      resourceTicketEntity = resourceTicketBackend.create(tenantId, spec);

    }

    @AfterMethod
    public void tearDown() throws Throwable {
      commonHostDocumentsCleanup();
    }

    @AfterClass
    public static void afterClassCleanup() throws Throwable {
      commonHostAndClientTeardown();
    }

    @Test
    public void testFindById() throws Throwable {
      String resourceTicketId = resourceTicketEntity.getId();
      ResourceTicketEntity foundTicket = resourceTicketBackend.findById(resourceTicketId);
      assertThat(foundTicket.getName(), is(spec.getName()));
      assertThat(foundTicket.getTenantId(), is(tenantId));
      assertThat(foundTicket.getLimits().size(), is(1));

      String key = spec.getLimits().get(0).getKey();
      Double value = spec.getLimits().get(0).getValue();
      QuotaUnit unit = spec.getLimits().get(0).getUnit();
      assertThat(foundTicket.getLimit(key).getValue(), is(value));
      assertThat(foundTicket.getLimit(key).getUnit(), is(unit));
    }

    @Test
    public void testFindByName() throws Throwable {
      ResourceTicketService.State resourceTicket = new ResourceTicketService.State();
      resourceTicket.name = UUID.randomUUID().toString();
      resourceTicket.tenantId = tenantId;
      resourceTicket.parentId = UUID.randomUUID().toString();

      xenonClient.post(ResourceTicketServiceFactory.SELF_LINK, resourceTicket);

      ResourceTicketEntity foundTicket = resourceTicketBackend.findByName(
          resourceTicket.tenantId, resourceTicket.name);
      assertThat(foundTicket, is(notNullValue()));
      assertThat(foundTicket.getName(), is(resourceTicket.name));
      assertThat(foundTicket.getTenantId(), is(resourceTicket.tenantId));
    }

    @Test
    public void testFindByNonExistingName() throws Throwable {
      ResourceTicketService.State resourceTicket = new ResourceTicketService.State();
      resourceTicket.name = UUID.randomUUID().toString();
      resourceTicket.tenantId = tenantId;
      resourceTicket.parentId = UUID.randomUUID().toString();

      xenonClient.post(ResourceTicketServiceFactory.SELF_LINK, resourceTicket);

      String randomName = UUID.randomUUID().toString();
      try {
        resourceTicketBackend.findByName(resourceTicket.tenantId, randomName);
        fail("findByName of non existing name should have failed");
      } catch (ResourceTicketNotFoundException e) {
        assertThat(e.getMessage(), containsString(randomName));
      }
    }

    @Test
    public void testFilterByParentId() throws Throwable {
      ResourceTicketService.State resourceTicket = new ResourceTicketService.State();
      resourceTicket.name = UUID.randomUUID().toString();
      resourceTicket.tenantId = UUID.randomUUID().toString();
      resourceTicket.parentId = UUID.randomUUID().toString();

      xenonClient.post(ResourceTicketServiceFactory.SELF_LINK, resourceTicket);

      List<ResourceTicketEntity> foundTickets = resourceTicketBackend.filterByParentId(
          resourceTicket.parentId);
      assertThat(foundTickets, is(notNullValue()));
      assertThat(foundTickets.size(), is(1));
      assertThat(foundTickets.get(0).getParentId(), is(resourceTicket.parentId));
    }

    @Test
    public void testFilterByTenantIdAndName() throws Throwable {
      ResourceTicketService.State resourceTicket = new ResourceTicketService.State();
      resourceTicket.name = UUID.randomUUID().toString();
      resourceTicket.tenantId = tenantId;
      resourceTicket.parentId = UUID.randomUUID().toString();

      xenonClient.post(ResourceTicketServiceFactory.SELF_LINK, resourceTicket);

      ResourceList<ResourceTicket> foundTickets = resourceTicketBackend.filter(
          resourceTicket.tenantId, Optional.of(resourceTicket.name),
          Optional.of(PaginationConfig.DEFAULT_DEFAULT_PAGE_SIZE));
      assertThat(foundTickets, is(notNullValue()));
      assertThat(foundTickets.getItems().size(), is(1));
      assertThat(foundTickets.getItems().get(0).getTenantId(), is(resourceTicket.tenantId));
      assertThat(foundTickets.getItems().get(0).getName(), is(resourceTicket.name));

      foundTickets = resourceTicketBackend.filter(resourceTicket.tenantId, Optional.<String>absent(), Optional.of(1));
      assertThat(foundTickets, is(notNullValue()));
      assertThat(foundTickets.getItems().size(), is(1));
      assertThat(foundTickets.getItems().get(0).getTenantId(), is(resourceTicket.tenantId));

      foundTickets = resourceTicketBackend.getPage(foundTickets.getNextPageLink());
      assertThat(foundTickets, is(notNullValue()));
      assertThat(foundTickets.getItems().size(), is(1));
      assertThat(foundTickets.getItems().get(0).getTenantId(), is(resourceTicket.tenantId));
    }
  }

  /**
   * Tests for deleting tickets.
   */
  @Guice(modules = {XenonBackendTestModule.class, TestModule.class})
  public static class DeleteTicketTest {

    @Inject
    private BasicServiceHost basicServiceHost;

    @Inject
    private ApiFeXenonRestClient apiFeXenonRestClient;

    @Inject
    private ResourceTicketBackend resourceTicketBackend;

    @Inject
    private TenantXenonBackend tenantXenonBackend;

    private ResourceTicketEntity resourceTicketEntity;
    private String tenantId;
    private ResourceTicketCreateSpec spec;

    @BeforeMethod
    public void setUp() throws Throwable {
      commonHostAndClientSetup(basicServiceHost, apiFeXenonRestClient);

      tenantId = XenonBackendTestHelper.createTenant(tenantXenonBackend, "t1");
      spec = new ResourceTicketCreateSpec();
      spec.setName("rt1");
      spec.setLimits(ImmutableList.of(new QuotaLineItem("vm", 10, QuotaUnit.COUNT)));

      resourceTicketEntity = resourceTicketBackend.create(tenantId, spec);

    }

    @AfterMethod
    public void tearDown() throws Throwable {
      commonHostDocumentsCleanup();
    }

    @AfterClass
    public static void afterClassCleanup() throws Throwable {
      commonHostAndClientTeardown();
    }

    @Test
    public void testDeleteTicket() throws Throwable {
      String resourceTicketId = resourceTicketEntity.getId();
      ResourceTicketEntity foundTicket = resourceTicketBackend.findById(resourceTicketId);
      assertThat(foundTicket, is(notNullValue()));
      assertThat(foundTicket.getId(), is(resourceTicketId));
      resourceTicketBackend.delete(resourceTicketId);
      try {
        resourceTicketBackend.findById(resourceTicketId);
        fail("Deleted ticket should not have been found");
      } catch (ResourceTicketNotFoundException e) {
        assertThat(e.getMessage(), containsString(resourceTicketId));
      }

      try {
        resourceTicketBackend.delete(resourceTicketId);
        fail("Deleted ticket should not have been found");
      } catch (ResourceTicketNotFoundException e) {
        assertThat(e.getMessage(), containsString(resourceTicketId));
      }
    }

    @Test
    public void testDeleteOfNonExistingTicket() throws Throwable {
      String resourceTicketId = UUID.randomUUID().toString();
      try {
        resourceTicketBackend.delete(resourceTicketId);
        fail("Delete of non existing ticket should have failed");
      } catch (ResourceTicketNotFoundException e) {
        assertThat(e.getMessage(), containsString(resourceTicketId));
      }
    }
  }

  /**
   * Tests for patching tickets.
   */
  @Guice(modules = {XenonBackendTestModule.class, TestModule.class})
  public static class PatchTicketTest {

    @Inject
    private BasicServiceHost basicServiceHost;

    @Inject
    private ApiFeXenonRestClient apiFeXenonRestClient;

    @Inject
    private ResourceTicketBackend resourceTicketBackend;

    @Inject
    private TenantXenonBackend tenantXenonBackend;

    @BeforeMethod
    public void setUp() throws Throwable {
      commonHostAndClientSetup(basicServiceHost, apiFeXenonRestClient);
    }

    @AfterMethod
    public void tearDown() throws Throwable {
      commonHostDocumentsCleanup();
    }

    @AfterClass
    public static void afterClassCleanup() throws Throwable {
      commonHostAndClientTeardown();
    }

    @Test
    public void testConsumeQuotaWithLimitsSpecified() throws Throwable {
      final String tenantId = XenonBackendTestHelper.createTenant(tenantXenonBackend, "t1");

      ResourceTicketCreateSpec spec = new ResourceTicketCreateSpec();
      spec.setName("rt1");

      List<QuotaLineItem> baseLimits = new ArrayList<>();
      for (QuotaLineItemEntity lineItemEntity : Data.baseLimits) {
        baseLimits.add(new QuotaLineItem(
            lineItemEntity.getKey(), lineItemEntity.getValue(), lineItemEntity.getUnit()));
      }

      spec.setLimits(baseLimits);

      String resourceTicketId = resourceTicketBackend.create(tenantId, spec).getId();
      ResourceTicketEntity resourceTicketEntity = resourceTicketBackend.findById(resourceTicketId);

      assertThat(resourceTicketEntity.getLimitMap().size(), is(3));
      assertThat(
          resourceTicketEntity.getLimitMap().get(Data.baseLimits.get(0).getKey()).getValue(),
          is(equalTo(Data.baseLimits.get(0).getValue())));

      QuotaCost cost = new QuotaCost(Data.vm100Cost);

      resourceTicketBackend.consumeQuota(resourceTicketId, cost);

      resourceTicketEntity = resourceTicketBackend.findById(resourceTicketId);

      assertThat("usage is correct: vm",
          resourceTicketEntity.getUsage("vm").getValue(),
          is(1.0));
      assertThat("usage is correct: vm.cost",
          resourceTicketEntity.getUsage("vm.cost").getValue(),
          is(1.0));
      assertThat("usage is correct: vm.flavor.core-100",
          resourceTicketEntity.getUsage("vm.flavor.core-100").getValue(),
          is(1.0));
      assertThat("usage is correct: vm.cpu",
          resourceTicketEntity.getUsage("vm.cpu").getValue(),
          is(1.0));
      assertThat("usage is correct: vm.memory",
          resourceTicketEntity.getUsage("vm.memory").getValue(),
          is(2.0));

      for (int i = 0; i < 4; i++) {
        resourceTicketBackend.consumeQuota(resourceTicketId, cost);
      }

      resourceTicketEntity = resourceTicketBackend.findById(resourceTicketId);

      // now validate that usage is as expected
      assertThat("usage is correct: vm",
          resourceTicketEntity.getUsage("vm").getValue(),
          is(5.0));
      assertThat("usage is correct: vm.cost",
          resourceTicketEntity.getUsage("vm.cost").getValue(),
          is(5.0));
      assertThat("usage is correct: vm.flavor.core-100",
          resourceTicketEntity.getUsage("vm.flavor.core-100").getValue(),
          is(5.0));
      assertThat("usage is correct: vm.cpu",
          resourceTicketEntity.getUsage("vm.cpu").getValue(),
          is(5.0));
      assertThat("usage is correct: vm.memory",
          resourceTicketEntity.getUsage("vm.memory").getValue(),
          is(10.0));

      try {
        resourceTicketBackend.consumeQuota(resourceTicketId, cost);
        fail("consume quota exceeding limit should have failed");
      } catch (QuotaException e) {
        assertThat(e.getLimit().getValue(), is(5.0));
        assertThat(e.getUsage().getValue(), is(5.0));
        assertThat(e.getNewUsage().getValue(), is(6.0));
      }
    }


    @Test(enabled = false) //disabled till Xenon fix https://enatai-jira.eng.vmware.com/browse/DCP-727 is taken
    public void testConsumeQuotaAboveLimitsErrorDetails() throws Throwable {
      final String tenantId = XenonBackendTestHelper.createTenant(tenantXenonBackend, "t1");

      ResourceTicketCreateSpec spec = new ResourceTicketCreateSpec();
      spec.setName("rt1");

      List<QuotaLineItem> baseLimits = new ArrayList<>();
      for (QuotaLineItemEntity lineItemEntity : Data.baseLimits) {
        baseLimits.add(new QuotaLineItem(
            lineItemEntity.getKey(), lineItemEntity.getValue(), lineItemEntity.getUnit()));
      }

      spec.setLimits(baseLimits);

      String resourceTicketId = resourceTicketBackend.create(tenantId, spec).getId();
      ResourceTicketEntity resourceTicketEntity = resourceTicketBackend.findById(resourceTicketId);

      assertThat(resourceTicketEntity.getLimitMap().size(), is(3));
      assertThat(
          resourceTicketEntity.getLimitMap().get(Data.baseLimits.get(0).getKey()).getValue(),
          is(equalTo(Data.baseLimits.get(0).getValue())));

      QuotaCost cost = new QuotaCost(Data.vm100Cost);

      for (int i = 0; i < 5; i++) {
        resourceTicketBackend.consumeQuota(resourceTicketId, cost);
      }

      resourceTicketEntity = resourceTicketBackend.findById(resourceTicketId);

      // now validate that usage is as expected
      assertThat("usage is correct: vm",
          resourceTicketEntity.getUsage("vm").getValue(),
          is(5.0));
      assertThat("usage is correct: vm.cost",
          resourceTicketEntity.getUsage("vm.cost").getValue(),
          is(5.0));
      assertThat("usage is correct: vm.flavor.core-100",
          resourceTicketEntity.getUsage("vm.flavor.core-100").getValue(),
          is(5.0));
      assertThat("usage is correct: vm.cpu",
          resourceTicketEntity.getUsage("vm.cpu").getValue(),
          is(5.0));
      assertThat("usage is correct: vm.memory",
          resourceTicketEntity.getUsage("vm.memory").getValue(),
          is(10.0));

      try {
        resourceTicketBackend.consumeQuota(resourceTicketId, cost);
        fail("consume quota exceeding limit should have failed");
      } catch (QuotaException e) {
        assertThat(e.getLimit().getValue(), is(5.0));
        assertThat(e.getUsage().getValue(), is(5.0));
        assertThat(e.getNewUsage().getValue(), is(6.0));
        return;
      }
    }

    @Test
    public void testConsumeQuotaWithoutLimits() throws Throwable {
      final String tenantId = XenonBackendTestHelper.createTenant(tenantXenonBackend, "t1");

      ResourceTicketCreateSpec spec = new ResourceTicketCreateSpec();
      spec.setName("rt1");

      List<QuotaLineItem> baseLimits = new ArrayList<>();
      for (QuotaLineItemEntity lineItemEntity : Data.noMatchLimits) {
        baseLimits.add(new QuotaLineItem(
            lineItemEntity.getKey(), lineItemEntity.getValue(), lineItemEntity.getUnit()));
      }

      spec.setLimits(baseLimits);

      String resourceTicketId = resourceTicketBackend.create(tenantId, spec).getId();
      ResourceTicketEntity resourceTicketEntity = resourceTicketBackend.findById(resourceTicketId);

      assertThat(resourceTicketEntity.getLimitMap().size(), is(3));
      assertThat(
          resourceTicketEntity.getLimitMap().get(Data.noMatchLimits.get(0).getKey()).getValue(),
          is(equalTo(Data.noMatchLimits.get(0).getValue())));

      QuotaCost cost = new QuotaCost(Data.vm200Cost);

      for (int i = 0; i < 10; i++) {
        resourceTicketBackend.consumeQuota(resourceTicketId, cost);
      }

      resourceTicketEntity = resourceTicketBackend.findById(resourceTicketId);

      // now validate that usage is as expected
      assertThat("usage is correct: vm",
          resourceTicketEntity.getUsage("vm").getValue(),
          is(10.0));
      assertThat("usage is correct: vm.cost",
          resourceTicketEntity.getUsage("vm.cost").getValue(),
          is(20.0));
      assertThat("usage is correct: vm.flavor.core-200",
          resourceTicketEntity.getUsage("vm.flavor.core-200").getValue(),
          is(10.0));
      assertThat("usage is correct: vm.cpu",
          resourceTicketEntity.getUsage("vm.cpu").getValue(),
          is(20.0));
      assertThat("usage is correct: vm.memory",
          resourceTicketEntity.getUsage("vm.memory").getValue(),
          is(40.0));
    }

    @Test
    public void testReturnQuotaWithLimits() throws Throwable {
      final String tenantId = XenonBackendTestHelper.createTenant(tenantXenonBackend, "t1");

      ResourceTicketCreateSpec spec = new ResourceTicketCreateSpec();
      spec.setName("rt1");

      List<QuotaLineItem> baseLimits = new ArrayList<>();
      for (QuotaLineItemEntity lineItemEntity : Data.baseLimits) {
        baseLimits.add(new QuotaLineItem(
            lineItemEntity.getKey(), lineItemEntity.getValue(), lineItemEntity.getUnit()));
      }

      spec.setLimits(baseLimits);

      String resourceTicketId = resourceTicketBackend.create(tenantId, spec).getId();
      ResourceTicketEntity resourceTicketEntity = resourceTicketBackend.findById(resourceTicketId);

      assertThat(resourceTicketEntity.getLimitMap().size(), is(3));
      assertThat(
          resourceTicketEntity.getLimitMap().get(Data.baseLimits.get(0).getKey()).getValue(),
          is(equalTo(Data.baseLimits.get(0).getValue())));

      QuotaCost cost = new QuotaCost(Data.vm100Cost);

      resourceTicketBackend.consumeQuota(resourceTicketId, cost);

      // now return quota and assert correct values
      resourceTicketBackend.returnQuota(resourceTicketId, cost);

      resourceTicketEntity = resourceTicketBackend.findById(resourceTicketId);

      assertThat("usage is correct: vm",
          resourceTicketEntity.getUsage("vm").getValue(),
          is(0.0));
      assertThat("usage is correct: vm.cost",
          resourceTicketEntity.getUsage("vm.cost").getValue(),
          is(0.0));
      assertThat("usage is correct: vm.flavor.core-100",
          resourceTicketEntity.getUsage("vm.flavor.core-100").getValue(),
          is(0.0));
      assertThat("usage is correct: vm.cpu",
          resourceTicketEntity.getUsage("vm.cpu").getValue(),
          is(0.0));
      assertThat("usage is correct: vm.memory",
          resourceTicketEntity.getUsage("vm.memory").getValue(),
          is(0.0));

      for (int i = 0; i < 2; i++) {
        resourceTicketBackend.consumeQuota(resourceTicketId, cost);
      }

      resourceTicketBackend.returnQuota(resourceTicketId, cost);

      resourceTicketEntity = resourceTicketBackend.findById(resourceTicketId);

      // now validate that usage is as expected
      assertThat("usage is correct: vm",
          resourceTicketEntity.getUsage("vm").getValue(),
          is(1.0));
      assertThat("usage is correct: vm.cost",
          resourceTicketEntity.getUsage("vm.cost").getValue(),
          is(1.0));
      assertThat("usage is correct: vm.flavor.core-100",
          resourceTicketEntity.getUsage("vm.flavor.core-100").getValue(),
          is(1.0));
      assertThat("usage is correct: vm.cpu",
          resourceTicketEntity.getUsage("vm.cpu").getValue(),
          is(1.0));
      assertThat("usage is correct: vm.memory",
          resourceTicketEntity.getUsage("vm.memory").getValue(),
          is(2.0));

      for (int i = 0; i < 4; i++) {
        resourceTicketBackend.consumeQuota(resourceTicketId, cost);
      }

      try {
        resourceTicketBackend.consumeQuota(resourceTicketId, cost);
        fail("consumeQuota above limit should have failed");
      } catch (QuotaException e) {
        assertThat(e.getLimit().getValue(), is(5.0));
        assertThat(e.getUsage().getValue(), is(5.0));
        assertThat(e.getNewUsage().getValue(), is(6.0));
      }

      for (int i = 0; i < 5; i++) {
        resourceTicketBackend.returnQuota(resourceTicketId, cost);
      }

      resourceTicketEntity = resourceTicketBackend.findById(resourceTicketId);

      assertThat("usage is correct: vm",
          resourceTicketEntity.getUsage("vm").getValue(),
          is(0.0));
      assertThat("usage is correct: vm.cost",
          resourceTicketEntity.getUsage("vm.cost").getValue(),
          is(0.0));
      assertThat("usage is correct: vm.flavor.core-100",
          resourceTicketEntity.getUsage("vm.flavor.core-100").getValue(),
          is(0.0));
      assertThat("usage is correct: vm.cpu",
          resourceTicketEntity.getUsage("vm.cpu").getValue(),
          is(0.0));
      assertThat("usage is correct: vm.memory",
          resourceTicketEntity.getUsage("vm.memory").getValue(),
          is(0.0));
    }

    @Test
    public void testReturnQuotaOfChildTicket() throws Throwable {
      final String tenantId = XenonBackendTestHelper.createTenant(tenantXenonBackend, "t1");

      ResourceTicketCreateSpec spec = new ResourceTicketCreateSpec();
      spec.setName("rt1");

      List<QuotaLineItem> baseLimits = new ArrayList<>();
      for (QuotaLineItemEntity lineItemEntity : Data.large10000Limits) {
        baseLimits.add(new QuotaLineItem(
            lineItemEntity.getKey(), lineItemEntity.getValue(), lineItemEntity.getUnit()));
      }

      spec.setLimits(baseLimits);

      String tenantTicketId = resourceTicketBackend.create(tenantId, spec).getId();

      ResourceTicketEntity projectTicketEntity = resourceTicketBackend.subdivide(tenantTicketId, Data.small100Limits);

      ResourceTicketEntity tenantTicketEntity = resourceTicketBackend.findById(tenantTicketId);

      assertThat("tenantTicket:usage: vm.cost",
          tenantTicketEntity.getUsage("vm.cost").getValue(),
          is(100.0));
      assertThat("t:usage: persistent-disk.cost",
          tenantTicketEntity.getUsage("persistent-disk.cost").getValue(),
          is(100.0));
      assertThat("t:usage: network.cost",
          tenantTicketEntity.getUsage("network.cost").getValue(),
          is(100.0));
      assertThat("t:usage: ephemeral-disk.cost",
          tenantTicketEntity.getUsage("ephemeral-disk.cost").getValue(),
          is(100.0));

      // now return quota and assert correct values
      resourceTicketBackend.returnQuota(projectTicketEntity);

      tenantTicketEntity = resourceTicketBackend.findById(tenantTicketId);

      assertThat("tenantTicket:usage: vm.cost",
          tenantTicketEntity.getUsage("vm.cost").getValue(),
          is(0.0));
      assertThat("t:usage: persistent-disk.cost",
          tenantTicketEntity.getUsage("persistent-disk.cost").getValue(),
          is(0.0));
      assertThat("t:usage: network.cost",
          tenantTicketEntity.getUsage("network.cost").getValue(),
          is(0.0));
      assertThat("t:usage: ephemeral-disk.cost",
          tenantTicketEntity.getUsage("ephemeral-disk.cost").getValue(),
          is(0.0));
    }

    @Test
    public void testSubdivideByLimits() throws ExternalException {

      final String tenantId = XenonBackendTestHelper.createTenant(tenantXenonBackend, "t1");

      ResourceTicketCreateSpec spec = new ResourceTicketCreateSpec();
      spec.setName("rt1");

      List<QuotaLineItem> baseLimits = new ArrayList<>();
      for (QuotaLineItemEntity lineItemEntity : Data.large10000Limits) {
        baseLimits.add(new QuotaLineItem(
            lineItemEntity.getKey(), lineItemEntity.getValue(), lineItemEntity.getUnit()));
      }

      spec.setLimits(baseLimits);

      String tenantResourceTicketId = resourceTicketBackend.create(tenantId, spec).getId();

      ResourceTicketEntity projectTicket = resourceTicketBackend.subdivide(tenantResourceTicketId, Data.small100Limits);

      ResourceTicketEntity tenantTicket = resourceTicketBackend.findById(tenantResourceTicketId);

      String projectTicketId = projectTicket.getId();

      projectTicket = resourceTicketBackend.findById(projectTicketId);
      assertThat(projectTicket, is(notNullValue()));
      assertThat(projectTicket.getTenantId(), nullValue());
      assertThat(projectTicket.getParentId(), is(tenantResourceTicketId));

      assertThat("projectTicket:limit: vm.cost",
          projectTicket.getLimit("vm.cost").getValue(),
          is(100.0));
      assertThat("projectTicket:limit: persistent-disk.cost",
          projectTicket.getLimit("persistent-disk.cost").getValue(),
          is(100.0));
      assertThat("projectTicket:limit: network.cost",
          projectTicket.getLimit("network.cost").getValue(),
          is(100.0));
      assertThat("projectTicket:limit: ephemeral-disk.cost",
          projectTicket.getLimit("ephemeral-disk.cost").getValue(),
          is(100.0));
      assertThat("projectTicket:limitKeys",
          projectTicket.getLimitKeys().size(),
          is(4));

      assertThat("tenantTicket:usage: vm.cost",
          tenantTicket.getUsage("vm.cost").getValue(),
          is(100.0));
      assertThat("t:usage: persistent-disk.cost",
          tenantTicket.getUsage("persistent-disk.cost").getValue(),
          is(100.0));
      assertThat("t:usage: network.cost",
          tenantTicket.getUsage("network.cost").getValue(),
          is(100.0));
      assertThat("t:usage: ephemeral-disk.cost",
          tenantTicket.getUsage("ephemeral-disk.cost").getValue(),
          is(100.0));

      allocateN(tenantResourceTicketId, Data.small100Limits, 4);

      tenantTicket = resourceTicketBackend.findById(tenantResourceTicketId);

      assertThat("tenantTicket:usage: vm.cost",
          tenantTicket.getUsage("vm.cost").getValue(),
          is(500.0));
      assertThat("tenantTicket:usage: persistent-disk.cost",
          tenantTicket.getUsage("persistent-disk.cost").getValue(),
          is(500.0));
      assertThat("tenantTicket:usage: network.cost",
          tenantTicket.getUsage("network.cost").getValue(),
          is(500.0));
      assertThat("tenantTicket:usage: ephemeral-disk.cost",
          tenantTicket.getUsage("ephemeral-disk.cost").getValue(),
          is(500.0));

      List<ResourceTicketEntity> projectTickets = allocateN(tenantResourceTicketId, Data.small100Limits, 5);

      try {
        allocateN(tenantResourceTicketId, Data.small100Limits, 1);
        fail("subdivide above limit should have failed");
      } catch (QuotaException e) {
        assertThat(e.getLimit().getValue(), is(1000.0));
        assertThat(e.getUsage().getValue(), is(1000.0));
        assertThat(e.getNewUsage().getValue(), is(1100.0));
      }

      tenantTicket = resourceTicketBackend.findById(tenantResourceTicketId);

      assertThat("tenantTicket:usage:2: vm.cost",
          tenantTicket.getUsage("vm.cost").getValue(),
          is(1000.0));
      assertThat("t:usage:2: persistent-disk.cost",
          tenantTicket.getUsage("persistent-disk.cost").getValue(),
          is(1000.0));
      assertThat("t:usage:2: network.cost",
          tenantTicket.getUsage("network.cost").getValue(),
          is(1000.0));
      assertThat("t:usage:2: ephemeral-disk.cost",
          tenantTicket.getUsage("ephemeral-disk.cost").getValue(),
          is(1000.0));

      try {
        allocateN(projectTickets.get(0).getId(), Data.small50Limits, 1);
        fail("subdivide of project ticket should have failed");
      } catch (ExternalException e) {
        assertThat(e.getErrorCode(), is(ErrorCode.INTERNAL_ERROR.getCode()));
      }

      assertThat("tenantTicket:usage:3: vm.cost",
          tenantTicket.getUsage("vm.cost").getValue(),
          is(1000.0));
      assertThat("t:usage:3: persistent-disk.cost",
          tenantTicket.getUsage("persistent-disk.cost").getValue(),
          is(1000.0));
      assertThat("t:usage:3: network.cost",
          tenantTicket.getUsage("network.cost").getValue(),
          is(1000.0));
      assertThat("t:usage:3: ephemeral-disk.cost",
          tenantTicket.getUsage("ephemeral-disk.cost").getValue(),
          is(1000.0));

      try {
        resourceTicketBackend.consumeQuota(projectTickets.get(0).getId(), new QuotaCost(Data.large10000Limits));
        fail("consumeQuota above limit should have failed");
      } catch (QuotaException e) {
        assertThat(e.getLimit().getValue(), is(100.0));
        assertThat(e.getUsage().getValue(), is(0.0));
        assertThat(e.getNewUsage().getValue(), is(1000.0));
      }

      try {
        resourceTicketBackend.subdivide(tenantResourceTicketId, Data.small50WithMissingLimits);
        fail("subdivide with missing limits should have fail");
      } catch (InvalidResourceTicketSubdivideException e) {
        assertThat(e.getMissingLimits().containsAll(ImmutableSet.of("persistent-disk.cost", "network.cost")), is(true));
      }
    }

    @Test
    public void testSubdivideByPercent() throws ExternalException {
      final String tenantId = XenonBackendTestHelper.createTenant(tenantXenonBackend, "t1");

      ResourceTicketCreateSpec spec = new ResourceTicketCreateSpec();
      spec.setName("rt1");

      List<QuotaLineItem> baseLimits = new ArrayList<>();
      for (QuotaLineItemEntity lineItemEntity : Data.small100Limits) {
        baseLimits.add(new QuotaLineItem(
            lineItemEntity.getKey(), lineItemEntity.getValue(), lineItemEntity.getUnit()));
      }

      spec.setLimits(baseLimits);

      String tenantResourceTicketId = resourceTicketBackend.create(tenantId, spec).getId();

      ResourceTicketEntity projectTicket = resourceTicketBackend.subdivide(
          tenantResourceTicketId, 10.0);

      ResourceTicketEntity tenantTicket = resourceTicketBackend.findById(tenantResourceTicketId);

      String projectTicketId = projectTicket.getId();

      projectTicket = resourceTicketBackend.findById(projectTicketId);
      assertThat(projectTicket, is(notNullValue()));
      assertThat(projectTicket.getTenantId(), nullValue());
      assertThat(projectTicket.getParentId(), is(tenantResourceTicketId));

      assertThat("projectTicket:limit: vm.cost",
          projectTicket.getLimit("vm.cost").getValue(),
          is(10.0));
      assertThat("projectTicket:limit: persistent-disk.cost",
          projectTicket.getLimit("persistent-disk.cost").getValue(),
          is(10.0));
      assertThat("projectTicket:limit: network.cost",
          projectTicket.getLimit("network.cost").getValue(),
          is(10.0));
      assertThat("projectTicket:limit: ephemeral-disk.cost",
          projectTicket.getLimit("ephemeral-disk.cost").getValue(),
          is(10.0));
      assertThat("projectTicket:limitKeys",
          projectTicket.getLimitKeys().size(),
          is(4));

      assertThat("tenantTicket:usage: vm.cost",
          tenantTicket.getUsage("vm.cost").getValue(),
          is(10.0));
      assertThat("t:usage: persistent-disk.cost",
          tenantTicket.getUsage("persistent-disk.cost").getValue(),
          is(10.0));
      assertThat("t:usage: network.cost",
          tenantTicket.getUsage("network.cost").getValue(),
          is(10.0));
      assertThat("t:usage: ephemeral-disk.cost",
          tenantTicket.getUsage("ephemeral-disk.cost").getValue(),
          is(10.0));

      projectTicket = resourceTicketBackend.subdivide(
          tenantResourceTicketId, 80.0);
      tenantTicket = resourceTicketBackend.findById(tenantResourceTicketId);

      assertThat("projectTicket:limit: vm.cost",
          projectTicket.getLimit("vm.cost").getValue(),
          is(80.0));
      assertThat("projectTicket:limit: persistent-disk.cost",
          projectTicket.getLimit("persistent-disk.cost").getValue(),
          is(80.0));
      assertThat("projectTicket:limit: network.cost",
          projectTicket.getLimit("network.cost").getValue(),
          is(80.0));
      assertThat("projectTicket:limit: ephemeral-disk.cost",
          projectTicket.getLimit("ephemeral-disk.cost").getValue(),
          is(80.0));
      assertThat("projectTicket:limitKeys",
          projectTicket.getLimitKeys().size(),
          is(4));

      assertThat("tenantTicket:usage: vm.cost",
          tenantTicket.getUsage("vm.cost").getValue(),
          is(90.0));
      assertThat("t:usage: persistent-disk.cost",
          tenantTicket.getUsage("persistent-disk.cost").getValue(),
          is(90.0));
      assertThat("t:usage: network.cost",
          tenantTicket.getUsage("network.cost").getValue(),
          is(90.0));
      assertThat("t:usage: ephemeral-disk.cost",
          tenantTicket.getUsage("ephemeral-disk.cost").getValue(),
          is(90.0));

      projectTicket = resourceTicketBackend.subdivide(
          tenantResourceTicketId, 1.5);
      tenantTicket = resourceTicketBackend.findById(tenantResourceTicketId);

      assertThat("projectTicket:limit: vm.cost",
          projectTicket.getLimit("vm.cost").getValue(),
          is(1.5));
      assertThat("projectTicket:limit: persistent-disk.cost",
          projectTicket.getLimit("persistent-disk.cost").getValue(),
          is(1.5));
      assertThat("projectTicket:limit: network.cost",
          projectTicket.getLimit("network.cost").getValue(),
          is(1.5));
      assertThat("projectTicket:limit: ephemeral-disk.cost",
          projectTicket.getLimit("ephemeral-disk.cost").getValue(),
          is(1.5));
      assertThat("projectTicket:limitKeys",
          projectTicket.getLimitKeys().size(),
          is(4));

      assertThat("tenantTicket:usage: vm.cost",
          tenantTicket.getUsage("vm.cost").getValue(),
          is(91.5));
      assertThat("t:usage: persistent-disk.cost",
          tenantTicket.getUsage("persistent-disk.cost").getValue(),
          is(91.5));
      assertThat("t:usage: network.cost",
          tenantTicket.getUsage("network.cost").getValue(),
          is(91.5));
      assertThat("t:usage: ephemeral-disk.cost",
          tenantTicket.getUsage("ephemeral-disk.cost").getValue(),
          is(91.5));
    }

    private List<ResourceTicketEntity> allocateN(String resourceTicketId, List<QuotaLineItemEntity> limits,
                                                 int n) throws ExternalException {
      List<ResourceTicketEntity> projectTickets = new ArrayList<>();
      // allocate a set of project level resource tickets
      for (int i = 0; i < n; i++) {
        ResourceTicketEntity projectTicket = resourceTicketBackend.subdivide(resourceTicketId, limits);
        if (projectTicket != null) {
          projectTickets.add(projectTicket);
        }
      }

      if (projectTickets.size() == 0) {
        projectTickets = null;
      }
      return projectTickets;
    }
  }
}
