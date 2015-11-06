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

import com.vmware.photon.controller.apife.backends.clients.ApiFeDcpRestClient;
import com.vmware.photon.controller.apife.entities.TombstoneEntity;
import com.vmware.photon.controller.cloudstore.dcp.entity.TombstoneServiceFactory;
import com.vmware.photon.controller.common.dcp.BasicServiceHost;
import com.vmware.photon.controller.common.thrift.StaticServerSet;

import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

import java.net.InetSocketAddress;
import java.util.concurrent.Executors;


/**
 * Tests {@link TombstoneDcpBackend}.
 */
public class TombstoneDcpBackendTest {

  private TombstoneDcpBackend backend;

  private BasicServiceHost host;
  private ApiFeDcpRestClient dcpClient;

  @Test(enabled = false)
  private void dummy() {
  }

  protected void setUpCommon() throws Throwable {
    host = BasicServiceHost.create(BasicServiceHost.BIND_ADDRESS, BasicServiceHost.BIND_PORT,
        null, TombstoneServiceFactory.SELF_LINK, 10, 10);
    host.startServiceSynchronously(new TombstoneServiceFactory(), null);

    StaticServerSet serverSet = new StaticServerSet(
        new InetSocketAddress(host.getPreferredAddress(), host.getPort()));
    dcpClient = spy(new ApiFeDcpRestClient(serverSet, Executors.newFixedThreadPool(1)));

    backend = new TombstoneDcpBackend(dcpClient);
  }

  protected void tearDownCommon() throws Throwable {
    if (host != null) {
      BasicServiceHost.destroy(host);
    }

    dcpClient.stop();
  }

  /**
   * Tests for the create method.
   */
  public class CreateTest {
    String entityId = "entity-id";
    String entityKind = "entity-kind";

    @BeforeMethod
    public void setUp() throws Throwable {
      setUpCommon();
    }

    @AfterMethod
    public void tearDown() throws Throwable {
      tearDownCommon();
    }

    @Test
    public void testSuccess() {
      TombstoneEntity entity = backend.create(entityKind, entityId);
      assertThat(entity.getId(), notNullValue());
      assertThat(entity.getEntityId(), is(entityId));
      assertThat(entity.getEntityKind(), is(entityKind));
      assertThat(entity.getTombstoneTime(), greaterThan(0L));
    }

    @Test(expectedExceptions = RuntimeException.class,
        expectedExceptionsMessageRegExp = ".*entityId cannot be null.*")
    public void testError() {
      backend.create(entityKind, null);
    }
  }

  /**
   * Tests for the delete method.
   */
  public class DeleteTest {

    @BeforeMethod
    public void setUp() throws Throwable {
      setUpCommon();
    }

    @AfterMethod
    public void tearDown() throws Throwable {
      tearDownCommon();
    }

    @Test
    public void testSuccess() {
      String id = "entity-id";
      String kind = "entity-kind";

      TombstoneEntity entity = backend.create(kind, id);
      assertThat(entity, notNullValue());
      assertThat(entity, is(backend.getByEntityId(id)));

      backend.delete(entity);
      assertThat(backend.getByEntityId(id), nullValue());
    }

    @Test
    public void testMissingDocument() {
      TombstoneEntity entity = new TombstoneEntity();
      entity.setId("missing-id");

      backend.delete(entity);
      verify(dcpClient).deleteAndWait(eq(TombstoneServiceFactory.SELF_LINK + "/" + entity.getId()), any());
    }
  }

  /**
   * Tests for the getByEntityId method.
   */
  public class GetByEntityIdTest {

    @BeforeMethod
    public void setUp() throws Throwable {
      setUpCommon();
    }

    @AfterMethod
    public void tearDown() throws Throwable {
      tearDownCommon();
    }

    @Test
    public void testNoDocuments() {
      assertThat(backend.getByEntityId("missing-id"), nullValue());
    }

    @Test
    public void testOneDocument() {
      String id = "entity-id";
      String kind = "entity-kind";
      buildTombstones(1, id, kind);

      TombstoneEntity entity = backend.getByEntityId(id);
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

      backend.getByEntityId(id);
    }

    private void buildTombstones(int count, String id, String kind) {
      for (int i = 0; i < count; i++) {
        backend.create(kind, id);
      }
    }
  }
}
