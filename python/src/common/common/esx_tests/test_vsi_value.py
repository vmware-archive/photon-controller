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

import copy
import logging
import sys
import unittest

from host.hypervisor.esx.vim_client import VimClient

logger = logging.getLogger()
logger.level = logging.DEBUG
stream_handler = logging.StreamHandler(sys.stdout)
logger.addHandler(stream_handler)


class VsiWrapper(object):
    def __init__(self):
        self._import_vsi()

    def _import_vsi(self):
        try:
            from vmware import vsi
            self.vsi_get = vsi.get
        except ImportError:
            logger.error("VSI not supported on this platform")
            raise

    def get(self, path):
        return self.vsi_get(path)


class VsiTestCase(unittest.TestCase):
    """
    Check hostd rescpu.actav1 value equals to vsi's cpuLoadHistory1MinInPct
    """
    def setUp(self):
        self.vim_client = VimClient()

    def tearDown(self):
        pass

    def test_hostd_rescpu_actav1_match_vsi_value(self):
        try:
            vsi = VsiWrapper()
        except:
            return

        cpu_load_history = vsi.get(
            "/sched/groups/0/stats"
            "/cpuStatsDir/cpuLoadHistory"
            "/cpuLoadHistory1MinInPct")
        vsi_cpu_load = cpu_load_history["avgActive"]

        # get average cpu load percentage in past 20 seconds
        # since hostd takes a sample in every 20 seconds
        # we use the min 20secs here to get the latest
        # CPU active average over 1 minute
        host_stats = copy.copy(self.vim_client.get_perf_manager_stats(20))
        rescpu_cpu_load = host_stats['rescpu.actav1'] / 100
        check_value = False

        # vsi gets the current cpu active average over 1 minute.
        # hostd gets the cpu active average over 1 minute 20 seconds ago.
        # Thus if there's a CPU active average boost during the
        # past 20 seconds, the value from hostd's CPU active average
        # value will be 20 seconds late which will have a large deviation.
        if (1 if vsi_cpu_load - 7 < 1 else vsi_cpu_load - 7) \
                <= rescpu_cpu_load \
                <= vsi_cpu_load + 7:
            check_value = True

        self.assertEqual(check_value, True)


if __name__ == '__main__':
    unittest.main()
