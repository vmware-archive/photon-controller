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

package com.vmware.photon.controller.api.frontend.backends;

import com.vmware.photon.controller.api.frontend.backends.clients.ApiFeXenonRestClient;
import com.vmware.photon.controller.api.frontend.entities.TombstoneEntity;
import com.vmware.photon.controller.common.xenon.BasicServiceHost;
import com.vmware.photon.controller.common.xenon.ServiceHostUtils;

import com.google.inject.Inject;
import org.junit.AfterClass;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Guice;
import org.testng.annotations.Test;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;


/**
 * Tests {@link TombstoneXenonBackend}.
 */
public class TombstoneXenonBackendTest {

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

  @Test(enabled = false)
  private void dummy() {
  }

  /**
   * Tests for the create method.
   */
  @Guice(modules = {XenonBackendTestModule.class})
  public static class CreateTest {
    String entityId = "entity-id";
    String entityKind = "entity-kind";

    @Inject
    private BasicServiceHost basicServiceHost;

    @Inject
    private ApiFeXenonRestClient apiFeXenonRestClient;

    @Inject
    private TombstoneBackend tombstoneBackend;

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
    public void testSuccess() {
      TombstoneEntity entity = tombstoneBackend.create(entityKind, entityId);
      assertThat(entity.getId(), notNullValue());
      assertThat(entity.getEntityId(), is(entityId));
      assertThat(entity.getEntityKind(), is(entityKind));
      assertThat(entity.getTombstoneTime(), greaterThan(0L));
    }

    @Test(expectedExceptions = RuntimeException.class,
        expectedExceptionsMessageRegExp = ".*entityId cannot be null.*")
    public void testError() {
      tombstoneBackend.create(entityKind, null);
    }
  }

  /**
   * Tests for the delete method.
   */
  @Guice(modules = {XenonBackendTestModule.class})
  public static class DeleteTest {

    @Inject
    private BasicServiceHost basicServiceHost;

    @Inject
    private ApiFeXenonRestClient apiFeXenonRestClient;

    @Inject
    private TombstoneBackend tombstoneBackend;

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
    public void testSuccess() {
      String id = "entity-id";
      String kind = "entity-kind";

      TombstoneEntity entity = tombstoneBackend.create(kind, id);
      assertThat(entity, notNullValue());
      assertThat(entity, is(tombstoneBackend.getByEntityId(id)));

      tombstoneBackend.delete(entity);
      assertThat(tombstoneBackend.getByEntityId(id), nullValue());
    }

    @Test
    public void testMissingDocumentNoOp() {
      TombstoneEntity entity = new TombstoneEntity();
      entity.setId("missing-id");

      tombstoneBackend.delete(entity);
    }
  }

  /**
   * Tests for the getByEntityId method.
   */
  @Guice(modules = {XenonBackendTestModule.class})
  public static class GetByEntityIdTest {

    @Inject
    private BasicServiceHost basicServiceHost;

    @Inject
    private ApiFeXenonRestClient apiFeXenonRestClient;

    @Inject
    private TombstoneBackend tombstoneBackend;

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
    public void testNoDocuments() {
      assertThat(tombstoneBackend.getByEntityId("missing-id"), nullValue());
    }

    @Test
    public void testOneDocument() {
      String id = "entity-id";
      String kind = "entity-kind";
      buildTombstones(1, id, kind);

      TombstoneEntity entity = tombstoneBackend.getByEntityId(id);
      assertThat(entity, notNullValue());
      assertThat(entity.getEntityId(), is(id));
      assertThat(entity.getEntityKind(), is(kind));
      assertThat(entity.getTombstoneTime(), notNullValue());
    }

    @Test(expectedExceptions = RuntimeException.class,
        expectedExceptionsMessageRegExp = "Multiple tombstones for entity: entity-id")
    public void testMultipleDocuments() {
      String id = "entity-id";
      String kind = "entity-kind";
      buildTombstones(3, id, kind);

      tombstoneBackend.getByEntityId(id);
    }

    private void buildTombstones(int count, String id, String kind) {
      for (int i = 0; i < count; i++) {
        tombstoneBackend.create(kind, id);
      }
    }
  }
}
