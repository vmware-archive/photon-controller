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

import threading
import time
from eccli.thrift import get_client


def timed(method, client, request):
    now = time.time()
    try:
        response = getattr(client, method)(request)
        if response.result == 0:
            return True, time.time() - now
        else:
            raise Exception()
    except Exception:
        return False, time.time() - now


class Collector(object):
    def __init__(self, requests):
        self._requests = requests
        self._current = 0
        self._req_lock = threading.Lock()
        self._report_lock = threading.Lock()

        # metrics
        self._time = 0
        self._success = 0
        self._failed = 0

    def fetch(self):
        with self._req_lock:
            if self._current == self._requests:
                return False
            else:
                self._current += 1
                return True

    def report_success(self, elapse):
        with self._report_lock:
            self._time += elapse
            self._success += 1

    def report_failed(self, elapse):
        with self._report_lock:
            self._time += elapse
            self._failed += 1

    def print_report(self):
        print("success: %d" % self._success)
        print("failed: %d" % self._failed)
        print("average: %.2fs" % (self._time // self._current))


def run_concurrency(method, request, options):
    if not options.requests or not options.concurrency:
        return

    def _loop(collector):
        client = get_client(options)
        while True:
            if not collector.fetch():
                break

            success, elapse = timed(method, client, request)
            if success:
                collector.report_success(elapse)
            else:
                collector.report_failed(elapse)

    print("Run concurrency test:")
    print("Concurrency: %d, Requests: %d" % (options.concurrency,
                                             options.requests))
    threads = []
    collector = Collector(options.requests)
    for i in range(options.concurrency):
        thread = threading.Thread(target=_loop, args=(collector,))
        thread.start()
        threads.append(thread)

    for thread in threads:
        thread.join()

    collector.print_report()
