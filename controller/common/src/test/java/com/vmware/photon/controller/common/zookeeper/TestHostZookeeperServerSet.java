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

package com.vmware.photon.controller.common.zookeeper;

/**
 * Test that the callbacks are triggered correctly when we add and remove keys to the hosts znode.
 */

import com.vmware.photon.controller.common.thrift.ServerSet;
import com.vmware.photon.controller.common.zookeeper.gen.ServerAddress;
import com.vmware.photon.controller.host.gen.HostConfig;
import com.vmware.photon.controller.resource.gen.Datastore;

import org.apache.thrift.TSerializer;
import org.testng.annotations.Test;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertEqualsNoOrder;
import static org.testng.Assert.assertTrue;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Test the host registration workflow and verify that the ServerSet callbacks are correctly triggered.
 */
public class TestHostZookeeperServerSet extends BaseTestWithRealZookeeper {

  @Test
  public void testCallback() throws Throwable {
    zkClient.start();
    DataDictionary hosts = new DataDictionary(zkClient, Executors.newCachedThreadPool(), "hosts");
    ZookeeperHostReader reader = new ZookeeperHostReader();

    try {
      InetSocketAddress server1 = new InetSocketAddress("192.168.1.1", 256);
      InetSocketAddress server2 = new InetSocketAddress("192.168.1.2", 512);

      final CountDownLatch done = new CountDownLatch(2);
      final List<InetSocketAddress> servers = new ArrayList<>();
      ServerSet serverSet = new ZookeeperServerSet(new PathChildrenCacheFactory(zkClient, reader), zkClient,
          reader, "", true);

      serverSet.addChangeListener(new ServerSet.ChangeListener() {
        @Override
        public void onServerAdded(InetSocketAddress address) {
          servers.add(address);
          done.countDown();
        }

        @Override
        public void onServerRemoved(InetSocketAddress address) {
          servers.remove(address);
          done.countDown();
        }
      });
      hosts.write("server-1", getHostConfig("server-1", server1));
      hosts.write("server-2", getHostConfig("server-2", server2));

      assertTrue(done.await(2, TimeUnit.SECONDS), "Timed out waiting for server set callback");
      assertEquals(servers.size(), 2);
      assertEqualsNoOrder(servers.toArray(), new InetSocketAddress[]{server1, server2});

      assertThat(serverSet.getServers().contains(server1), is(true));
      assertThat(serverSet.getServers().contains(server2), is(true));
      serverSet.close();

    } finally {
      zkClient.close();
    }

  }

  private byte[] getHostConfig(String id, InetSocketAddress address) throws Exception {
    ServerAddress sAddr = new ServerAddress(address.getHostString(), address.getPort());
    TSerializer serializer = new TSerializer();
    HostConfig host = new HostConfig(id, new ArrayList<Datastore>(), sAddr);
    return serializer.serialize(host);
  }
}
