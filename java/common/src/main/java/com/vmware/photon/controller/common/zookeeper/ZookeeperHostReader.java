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

import com.vmware.photon.controller.host.gen.HostConfig;

import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.utils.ZKPaths;
import org.apache.thrift.TDeserializer;
import org.apache.thrift.TException;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;

/**
 * Zookeeper reader for parsing and reading host configuration.
 */
public class ZookeeperHostReader implements ZookeeperServerReader {

  /**
   * @param data the znode payload
   * @return the Inet address of the host captured in the payload.
   */
  public InetSocketAddress deserialize(byte[] data) {
    HostConfig config = new HostConfig();
    try {
      new TDeserializer().deserialize(config, data);
    } catch (TException e) {
      throw new RuntimeException(e);
    }
    return new InetSocketAddress(config.getAddress().getHost(), config.getAddress().getPort());
  }

  /**
   * @param nodeName the nodeName
   * @return the zk node corresponding to hosts
   */
  public String basePath(String nodeName) {
    return ZKPaths.makePath("hosts", nodeName);
  }

  public List<String> nodePaths(CuratorFramework zkClient, String nodeName) throws Exception {
    // XXX TODO(amar): There are two ways in which hosts are monitored either by watching /hosts or by attempting to
    // monitor a specific host. The list of nodes in both these cases is different.
    // Unify this by only monitoring /hosts. https://www.pivotaltracker.com/story/show/82171608
    List<String> nodes = new ArrayList<>();
    if (!nodeName.isEmpty()) {
      // We are actually monitoring a specific agent.
      nodes.add(ZKPaths.makePath("hosts", nodeName));
    } else {
      List<String> parent = zkClient.getChildren().forPath(basePath(nodeName));
      for (String nodePath : parent) {
        nodes.add(ZKPaths.makePath(basePath(nodeName), nodePath));
      }
    }
    return nodes;
  }
}
