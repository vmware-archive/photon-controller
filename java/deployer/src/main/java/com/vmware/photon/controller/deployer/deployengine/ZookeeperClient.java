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

package com.vmware.photon.controller.deployer.deployengine;

import com.vmware.photon.controller.common.zookeeper.PathChildrenCacheFactory;
import com.vmware.photon.controller.common.zookeeper.ServiceConfig;
import com.vmware.photon.controller.common.zookeeper.ZookeeperServiceReader;

import com.google.common.util.concurrent.FutureCallback;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.retry.BoundedExponentialBackoffRetry;
import org.apache.zookeeper.AsyncCallback;
import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.data.Stat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * This class allows to retrieve all known servers for a specified service from a specified zookeeper instance.
 */
public class ZookeeperClient {

  private static final Logger logger = LoggerFactory.getLogger(ZookeeperClient.class);

  public static final int BASE_SLEEP_TIME_MS = 5;
  public static final int MAX_SLEEP_TIME_MS = 100;
  public static final int MAX_RETRIES = 1;
  private String namespace;

  public ZookeeperClient(@Nullable String namespace) {
    logger.info("ZkClient Deployer namespace {}", namespace);
    this.namespace = namespace;
  }

  public Set<InetSocketAddress> getServers(String zookeeperInstance, String serviceName) {
    try (CuratorFramework zkClient =
             connectToZookeeper(zookeeperInstance)){
      ZookeeperServiceReader reader = new ZookeeperServiceReader();

      Set<InetSocketAddress> servers = reader.nodePaths(zkClient, serviceName).stream()
          .map(nodePath -> toInetAdrress(nodePath, reader, zkClient))
          .filter(address -> address != null)
          .collect(Collectors.toSet());

      return servers;
    } catch (Exception e) {
      logger.error("Rethrowing error ", e);
      throw new RuntimeException(e);
    }
  }

  public Set<InetSocketAddress> getServers(CuratorFramework zkClient, String zookeeperInstance, String serviceName) {
    try {
      ZookeeperServiceReader reader = new ZookeeperServiceReader();

      Set<InetSocketAddress> servers = reader.nodePaths(zkClient, serviceName).stream()
          .map(nodePath -> toInetAdrress(nodePath, reader, zkClient))
          .filter(address -> address != null)
          .collect(Collectors.toSet());

      return servers;
    } catch (Exception e) {
      logger.error("Rethrowing error ", e);
      throw new RuntimeException(e);
    }
  }

  public void addServer(String zookeeperInstance, String joiningServerIp, String joiningServerZookeeperPort, Integer
      myId, FutureCallback callback) {
    AsyncCallback.DataCallback dataCallback = getDataCallback(callback);

    try (CuratorFramework zkClient =
             connectToZookeeper(zookeeperInstance)) {
      List<String> joiningServers = new ArrayList<>();
      String serverStr = "server." + myId + "=" + joiningServerIp + ":2888:3888;" + joiningServerZookeeperPort;
      logger.info("Adding server: " + serverStr);
      joiningServers.add(serverStr);
      LinkedList<Integer> results = new LinkedList<Integer>();
      zkClient.getZookeeperClient().getZooKeeper().reconfig(joiningServers, null,
          null, -1, dataCallback, results);

    } catch (Exception e) {
      logger.error("Ignoring Zookeeper reconfig error ", e);
      throw new RuntimeException(e);
    }
  }

  public void removeServer(String zookeeperInstance, Integer myId, FutureCallback callback) {
    AsyncCallback.DataCallback dataCallback = getDataCallback(callback);

    try (CuratorFramework zkClient =
             connectToZookeeper(zookeeperInstance)) {
      List<String> leavingServers = new ArrayList<>();
      leavingServers.add(myId.toString());
      logger.info("Removing server: " + myId.toString());
      LinkedList<Integer> results = new LinkedList<Integer>();
      zkClient.getZookeeperClient().getZooKeeper().reconfig(null, leavingServers,
          null, -1, dataCallback, results);

    } catch (Exception e) {
      logger.error("Ignoring Zookeeper reconfig error ", e);
      throw new RuntimeException(e);
    }
  }

  public ServiceConfig getServiceConfig(String zookeeperInstance, String serviceName) {
    logger.info("getServiceConfig for " + zookeeperInstance);
    CuratorFramework zkClient = connectToZookeeper(zookeeperInstance);
    PathChildrenCacheFactory pathCache = new PathChildrenCacheFactory(zkClient, new ZookeeperServiceReader());
    try {
      return new ServiceConfig(zkClient, pathCache, serviceName);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  private AsyncCallback.DataCallback getDataCallback(FutureCallback callback) {
    return new AsyncCallback.DataCallback() {
      @Override
      public void processResult(int rc, String path, Object ctx, byte[] data, Stat stat) {
        switch (KeeperException.Code.get(rc)) {
          case OK:
            logger.info("Zookeeper successfully reconfigured");
            callback.onSuccess(null);
            break;
          default:
            logger.error("Zookeeper returned error code during reconfig" + KeeperException.Code.get(rc));
            callback.onFailure(new RuntimeException("Failed to reconfigure zookeeper"));
        }
      }
    };
  }

  private CuratorFramework connectToZookeeper(String zookeeperInstance) {
    CuratorFramework zkClient =
        CuratorFrameworkFactory
            .builder()
            .connectString(zookeeperInstance)
            .namespace(namespace)
            .retryPolicy(new BoundedExponentialBackoffRetry(
                BASE_SLEEP_TIME_MS,
                MAX_SLEEP_TIME_MS,
                MAX_RETRIES))
            .build();
      zkClient.start();
      return zkClient;
  }

  private InetSocketAddress toInetAdrress(String nodePath, ZookeeperServiceReader reader, CuratorFramework zkClient) {
    try {
      byte[] data = zkClient.getData().forPath(nodePath);
      if (data == null) {
        // clustered service member went away, but path still exists
        return null;
      }
      return reader.deserialize(data);
    } catch (Exception e) {
      logger.error("Rethrowing exception ", e);
      throw new RuntimeException(e);
    }
  }
}
