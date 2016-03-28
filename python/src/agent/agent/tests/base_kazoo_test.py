# Copyright 2015 VMware, Inc. All Rights Reserved.
#
# Licensed under the Apache License, Version 2.0 (the "License"); you may not
# use this file except in compliance with the License.  You may obtain a copy
# of the License at http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS, without
# warranties or conditions of any kind, EITHER EXPRESS OR IMPLIED.  See the
# License for then specific language governing permissions and limitations
# under the License.

import logging
import os

import nose.plugins.skip

from kazoo.testing import KazooTestHarness
from kazoo.testing import harness  # We need to varify server config.
from kazoo.testing.common import ZookeeperCluster


ZK_CLASSPATH = os.environ.get("ZOOKEEPER_CLASSPATH")
ZK_HOME = os.environ.get("ZOOKEEPER_PATH")
ZK_PORT_OFFSET = os.environ.get("ZOOKEEPER_PORT_OFFSET")

if ZK_PORT_OFFSET:
    DEFAULT_ZK_PORT = int(ZK_PORT_OFFSET)
else:
    DEFAULT_ZK_PORT = 60000


class BaseKazooTestCase(KazooTestHarness):
    """
    Test hooks that setup and tear down a zookeeper cluster. Test
    cases that require a real zk instance should inherit from this classs.
    """
    def set_up_kazoo_base(self):
        if not ZK_HOME:
            raise nose.plugins.skip.SkipTest(
                "ZOOKEEPER_PATH environment variable is not set")

        # There is no way to vary the size of the zk cluster by just using
        # the KazooTestHarness, so directly set the cluster here.
        if harness.CLUSTER:
            logging.warn("harness.CLUSTER is not None. Tearing down.")
            self.tear_down_kazoo_base()
        logging.info("Starting ZooKeeper")
        harness.CLUSTER = ZookeeperCluster(install_path=ZK_HOME,
                                           classpath=ZK_CLASSPATH,
                                           size=1,
                                           port_offset=DEFAULT_ZK_PORT)

        # Start the cluster
        self.cluster.start()

    def stop_kazoo_base(self):
        """
        :param wait [int]: in seconds
        """
        logging.info("Stopping ZooKeeper")
        harness.CLUSTER.stop()

    def tear_down_kazoo_base(self):
        logging.info("Terminating ZooKeeper")
        harness.CLUSTER.terminate()
        harness.CLUSTER = None
