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

package com.vmware.photon.controller.apife.db.dao;

import com.vmware.photon.controller.api.DeploymentState;
import com.vmware.photon.controller.apife.db.HibernateTestModule;
import com.vmware.photon.controller.apife.entities.DeploymentEntity;

import com.google.common.base.Optional;
import com.google.inject.Inject;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Guice;
import org.testng.annotations.Test;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

/**
 * Tests {@link DeploymentDao}.
 */
@Guice(modules = {HibernateTestModule.class})
public class DeploymentDaoTest extends BaseDaoTest {

  @Inject
  private DeploymentDao deploymentDao;

  private DeploymentEntity deploymentEntity;

  @Override
  @BeforeMethod
  public void setUp() throws Throwable {
    super.setUp();

    deploymentEntity = new DeploymentEntity();
    deploymentEntity.setState(DeploymentState.READY);
    deploymentEntity.setAuthEnabled(true);
    deploymentEntity.setSyslogEndpoint("http://syslog");
    deploymentEntity.setOauthEndpoint("192.168.0.1");
    deploymentEntity.setOauthPort(443);
    deploymentEntity.setOauthUsername("user");
    deploymentEntity.setOauthPassword("password");
    deploymentEntity.setOauthTenant("tenant");
    deploymentEntity.setOauthSecurityGroups(Arrays.asList(new String[]{"adminGroup1", "adminGroup2"}));
    deploymentEntity.setNtpEndpoint("http://ntp");
    deploymentEntity.setImageDatastore("datastore1");
    deploymentEntity.setUseImageDatastoreForVms(true);
    deploymentEntity.setOperationId("opid");
    deploymentEntity.setMigrationProgress(new HashMap<>());
  }

  @Test
  public void testCRUDOperations() {
    // create
    deploymentDao.create(deploymentEntity);
    assertThat(deploymentEntity.getId(), notNullValue());

    flushSession();

    // find by id
    Optional<DeploymentEntity> entity = deploymentDao.findById(deploymentEntity.getId());
    assertThat(entity.isPresent(), is(true));
    assertThat(entity.get(), equalTo(deploymentEntity));

    // list all
    List<DeploymentEntity> entities = deploymentDao.listAll();
    assertThat(entities.size(), is(1));
    assertThat(entities.get(0), equalTo(deploymentEntity));

    flushSession();

    // delete
    deploymentDao.delete(deploymentEntity);
    assertThat(deploymentEntity.getId(), notNullValue());

    flushSession();

    // find by id
    entity = deploymentDao.findById(deploymentEntity.getId());
    assertThat(entity.isPresent(), is(false));

    // list all
    entities = deploymentDao.listAll();
    assertThat(entities.size(), is(0));
  }
}
