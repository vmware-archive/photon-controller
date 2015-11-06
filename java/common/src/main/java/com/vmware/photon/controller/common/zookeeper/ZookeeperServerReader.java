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

import org.apache.curator.framework.CuratorFramework;

import java.net.InetSocketAddress;
import java.util.List;

/**
 * Interface to read server ip address and related information.
 * Implemented by various thrift endpoints which persist their data
 * differently in zookeeper.
 */
public interface ZookeeperServerReader {

  /**
   * Given the byte payload of the znode corresponding to the server the
   * utility returns the InetSocketAddress of the node element.
   *
   * @param data the znode payload
   * @return InetSocketAddress corresponding to the server
   */
  public InetSocketAddress deserialize(byte[] data);

  /**
   * Return the znode basepath for a given server based on the nodeName.
   *
   * @param nodeName the nodeName
   * @return the znode basepath corresponding to the server
   */
  public String basePath(String nodeName);

  /**
   * Given a node name return the list of child node paths to read the server address from.
   *
   * @param zkClient
   * @param nodeName
   * @return The list of children to read the server information from.
   */
  public List<String> nodePaths(CuratorFramework zkClient, String nodeName) throws Exception;

}
