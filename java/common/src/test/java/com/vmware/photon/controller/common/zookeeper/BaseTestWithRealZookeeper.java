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
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.retry.RetryOneTime;
import org.apache.curator.test.TestingServer;
import org.apache.curator.test.Timing;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;

/**
 * Test hooks that spin up and shutdown ZooKeeper. Tests requiring a real ZooKeeper connection need to subclass this
 * class.
 */
public class BaseTestWithRealZookeeper {

  protected TestingServer zookeeper;
  protected CuratorFramework zkClient;
  protected CuratorFramework zkClient2;

  @BeforeMethod
  public void startZookeeper() throws Exception {
    this.zookeeper = new TestingServer();
    Timing timing = new Timing();

    this.zkClient = CuratorFrameworkFactory
        .newClient(zookeeper.getConnectString(),
            timing.session(),
            timing.connection(),
            new RetryOneTime(1));

    this.zkClient2 = CuratorFrameworkFactory
        .newClient(zookeeper.getConnectString(),
            timing.session(),
            timing.connection(),
            new RetryOneTime(1));
  }

  @AfterMethod
  public void stopZookeeper() throws Exception {
    zookeeper.close();
  }

}
