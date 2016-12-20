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

package com.vmware.photon.controller.clustermanager.statuschecks;

import com.vmware.photon.controller.clustermanager.servicedocuments.ClusterManagerConstants;

import com.google.common.util.concurrent.FutureCallback;
import org.apache.curator.test.TestingServer;
import org.testng.annotations.Test;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;
import static org.testng.AssertJUnit.fail;

/**
 * Implements tests for {@link ZookeeperStatusChecker}.
 */
public class ZookeeperStatusCheckerTest {

  private ZookeeperStatusChecker zookeeperStatusChecker = new ZookeeperStatusChecker();
  private static final String SERVER_ADDRESS = "127.0.0.1";

  /**
   * Dummy test case to make Intellij recognize this as a test class.
   */
  @Test
  private void dummy() {
  }

  /**
   * Implements tests for the isReady method.
   */
  public class IsReadyTest {
    @Test
    public void testZookeeperRunning() throws Throwable {
      try (TestingServer ignored = new TestingServer(ClusterManagerConstants.Mesos.ZOOKEEPER_PORT)) {
        zookeeperStatusChecker.checkNodeStatus(SERVER_ADDRESS, new FutureCallback<Boolean>() {
          @Override
          public void onSuccess(Boolean isReady) {
            assertTrue(isReady);
          }

          @Override
          public void onFailure(Throwable t) {
            fail("Unexpected failure");
          }
        });
      }
    }

    @Test
    public void testZookeeperDown() throws Throwable {
      zookeeperStatusChecker.checkNodeStatus(SERVER_ADDRESS, new FutureCallback<Boolean>() {
        @Override
        public void onSuccess(Boolean isReady) {
          assertFalse(isReady);
        }

        @Override
        public void onFailure(Throwable t) {
          fail("Unexpected failure");
        }
      });
    }
  }
}
