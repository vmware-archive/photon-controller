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
import com.vmware.photon.controller.api.ResourceTicketCreateSpec;
import com.vmware.photon.controller.api.common.exceptions.external.ErrorCode;
import com.vmware.photon.controller.api.common.exceptions.external.ExternalException;
import com.vmware.photon.controller.apife.Data;
import com.vmware.photon.controller.apife.db.HibernateTestModule;
import com.vmware.photon.controller.apife.db.dao.BaseDaoTest;
import com.vmware.photon.controller.apife.db.dao.ProjectDao;
import com.vmware.photon.controller.apife.entities.QuotaLineItemEntity;
import com.vmware.photon.controller.apife.entities.ResourceTicketEntity;
import com.vmware.photon.controller.apife.entities.TenantEntity;
import com.vmware.photon.controller.apife.exceptions.external.InvalidResourceTicketSubdivideException;
import com.vmware.photon.controller.apife.exceptions.external.QuotaException;
import com.vmware.photon.controller.apife.exceptions.external.ResourceTicketNotFoundException;
import com.vmware.photon.controller.apife.lib.QuotaCost;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.inject.Inject;
import org.testng.annotations.Guice;
import org.testng.annotations.Test;
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
 * Tests {@link ResourceTicketSqlBackend}.
 */
@Guice(modules = {HibernateTestModule.class, BackendTestModule.class})
public class ResourceTicketSqlBackendTest extends BaseDaoTest {

  @Inject
  private ProjectDao projectDao;

  @Inject
  private EntityFactory entityFactory;

  @Inject
  private ResourceTicketBackend resourceTicketBackend;

  @Test
  public void testCreateTenantResourceTicket() throws Exception {
    TenantEntity tenantEntity = entityFactory.createTenant("t1");
    final String tenantId = tenantEntity.getId();

    ResourceTicketCreateSpec spec = new ResourceTicketCreateSpec();
    spec.setName("rt1");
    spec.setLimits(ImmutableList.of(new QuotaLineItem("vm", 10, QuotaUnit.COUNT)));

    ResourceTicketEntity resourceTicketEntity = resourceTicketBackend.create(tenantId, spec);
    String resourceTicketId = resourceTicketEntity.getId();

    ResourceTicketEntity ticket = resourceTicketBackend.findById(resourceTicketId);

    assertThat(ticket.getName(), is("rt1"));
    assertThat(ticket.getTenantId(), is(tenantId));
    assertThat(ticket.getParentId(), is(nullValue()));
    assertThat(ticket.getLimits().size(), is(1));
    assertThat(ticket.getLimit("vm").getValue(), is(10.0));
    assertThat(ticket.getLimit("vm").getUnit(), is(QuotaUnit.COUNT));
  }

  @Test
  public void testFindResourceTicketByName() throws Exception {
    TenantEntity tenantEntity = entityFactory.createTenant("t1");
    final String tenantId = tenantEntity.getId();

    ResourceTicketCreateSpec spec = new ResourceTicketCreateSpec();
    spec.setName("rt1");
    spec.setLimits(ImmutableList.of(new QuotaLineItem("vm", 10, QuotaUnit.COUNT)));

    String resourceTicketId = resourceTicketBackend.create(tenantId, spec).getId();
    ResourceTicketEntity resourceTicketEntity = resourceTicketBackend.findByName(tenantId, "rt1");
    assertThat(resourceTicketEntity.getId(), is(resourceTicketId));
  }

  @Test
  public void testFilterByParentId() throws Throwable {
    TenantEntity tenantEntity = entityFactory.createTenant("t1");
    final String tenantId = tenantEntity.getId();

    ResourceTicketCreateSpec spec = new ResourceTicketCreateSpec();
    spec.setName("rt1");

    List<QuotaLineItem> baseLimits = new ArrayList<>();
    for (QuotaLineItemEntity lineItemEntity : Data.large10000Limits) {
      baseLimits.add(new QuotaLineItem(
          lineItemEntity.getKey(), lineItemEntity.getValue(), lineItemEntity.getUnit()));
    }

    spec.setLimits(baseLimits);

    String parentTicketId = resourceTicketBackend.create(tenantId, spec).getId();

    ResourceTicketEntity projectTicketEntity = resourceTicketBackend.subdivide(parentTicketId, Data.small100Limits);

    List<ResourceTicketEntity> tickets = resourceTicketBackend.filterByParentId(parentTicketId);

    assertThat(tickets, is(notNullValue()));
    assertThat(tickets.size(), is(1));
    assertThat(tickets.get(0).getId(), is(projectTicketEntity.getId()));
    assertThat(tickets.get(0).getParentId(), is(parentTicketId));
  }

  @Test(expectedExceptions = ResourceTicketNotFoundException.class)
  public void testDeleteTicket() throws Throwable {
    TenantEntity tenantEntity = entityFactory.createTenant("t1");
    final String tenantId = tenantEntity.getId();

    ResourceTicketCreateSpec spec = new ResourceTicketCreateSpec();
    spec.setName("rt1");
    spec.setLimits(ImmutableList.of(new QuotaLineItem("vm", 10, QuotaUnit.COUNT)));

    String resourceTicketId = resourceTicketBackend.create(tenantId, spec).getId();
    resourceTicketBackend.delete(resourceTicketId);
    resourceTicketBackend.findById(resourceTicketId);
  }

  @Test(expectedExceptions = ResourceTicketNotFoundException.class)
  public void testDeleteNonExistingTicket() throws Throwable {
    resourceTicketBackend.delete(UUID.randomUUID().toString());
  }

  @Test
  public void testConsumeQuotaWithLimits() throws Throwable {
    TenantEntity tenantEntity = entityFactory.createTenant("t1");
    final String tenantId = tenantEntity.getId();

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
    } catch (QuotaException e) {
      assertThat(e.getLimit().getValue(), is(5.0));
      assertThat(e.getUsage().getValue(), is(5.0));
      assertThat(e.getNewUsage().getValue(), is(6.0));
    }
  }

  @Test
  public void testConsumeQuotaWithoutLimits() throws Throwable {
    TenantEntity tenantEntity = entityFactory.createTenant("t1");
    final String tenantId = tenantEntity.getId();

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
    TenantEntity tenantEntity = entityFactory.createTenant("t1");
    final String tenantId = tenantEntity.getId();

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
    TenantEntity tenantEntity = entityFactory.createTenant("t1");
    final String tenantId = tenantEntity.getId();

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

    TenantEntity tenantEntity = entityFactory.createTenant("t1");
    final String tenantId = tenantEntity.getId();

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

    sessionFactory.getCurrentSession().flush();
    sessionFactory.getCurrentSession().clear();

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
      assertThat(e.getUsage().getValue(), is(100.0));
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

    TenantEntity tenantEntity = entityFactory.createTenant("t1");
    final String tenantId = tenantEntity.getId();

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

    sessionFactory.getCurrentSession().flush();
    sessionFactory.getCurrentSession().clear();

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
