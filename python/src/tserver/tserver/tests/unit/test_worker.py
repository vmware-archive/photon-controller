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

import Queue
import time
import unittest

from hamcrest import *  # noqa
from mock import MagicMock
from tserver.thrift_server import Worker


class TestWorker(unittest.TestCase):

    def test_worker(self):
        """Test Worker thread.
        """
        data = [MagicMock(), MagicMock(), MagicMock(), MagicMock(),
                MagicMock(), MagicMock()]
        service_queue = Queue.Queue()
        workers = []

        def wait_for_processing(timer=20):
            retries = 0
            while (retries < timer):
                if service_queue.empty():
                    break
                retries += 1
                time.sleep(1)

        def produce_task():
            for _ in xrange(100):
                service_queue.put(data)

        for i in xrange(32):
            worker = Worker(service_queue)
            worker.setDaemon(True)
            worker.start()
            workers.append(worker)

        produce_task()
        wait_for_processing()
        self.assertEqual(service_queue.empty(), True)

        # Test queue.get() timeout.
        time.sleep(4)

        produce_task()
        wait_for_processing()
        self.assertEqual(service_queue.empty(), True)

if __name__ == "__main__":
    unittest.main()
