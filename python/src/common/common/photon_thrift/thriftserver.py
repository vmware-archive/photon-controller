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
import abc
from thrift.server import TNonblockingServer


class IThriftWorkerCallback:
    @abc.abstractmethod
    def thread_start(self):
        pass

    @abc.abstractmethod
    def thread_exit(self):
        pass


class ThriftWorker(TNonblockingServer.Worker):
    """ Customize TNonblockingServer.Worker to support thread start/exit callbacks
    """
    _callback = None

    @staticmethod
    def set_callback(callback):
        ThriftWorker._callback = callback

    def run(self):
        if ThriftWorker._callback:
            ThriftWorker._callback.thread_start()

        TNonblockingServer.Worker.run(self)

        if ThriftWorker._callback:
            ThriftWorker._callback.thread_exit()


class ThriftServer(TNonblockingServer.TNonblockingServer):
    def prepare(self):
        if self.prepared:
            return
        self.socket.listen()
        for _ in xrange(self.threads):
            thread = ThriftWorker(self.tasks)
            thread.setDaemon(True)
            thread.start()
        self.prepared = True
