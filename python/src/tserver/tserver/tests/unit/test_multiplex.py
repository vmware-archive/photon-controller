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

import unittest

from mock import call, patch, MagicMock
from tserver.multiplex import TMultiplexedProcessor, ServiceProcessor, \
    StoredMessageProtocol
from thrift.Thrift import TMessageType


class Matcher(object):
    def __init__(self, compare, obj):
        self.compare = compare
        self.obj = obj

    def __eq__(self, other):
        return self.compare(self.obj, other)


def compare_standard_msg(self, other):
    """ Custom compare fuction for StoredMessageProtocol class """
    if (type(self) != type(other)):
        return False

    if (self.protocol != other.protocol):
        return False

    return True


class TestTMultiPlexedProcessor(unittest.TestCase):

    def setUp(self):
        """
        Setup multiple services.
        Test has two thrift services with two threads allocated for each.
        """
        pass

    def _check_worker_calls(self, service_name, num_workers,
                            worker, test_queue):
        """ Helper method for checking the worker thread calls """
        calls = []
        fake_thread = "{0}-Thread-{1}"

        for i in xrange(num_workers):
            calls.extend(
                [call(test_queue, name=fake_thread.format(service_name, i)),
                 call().setDaemon(True),
                 call().start()])

        worker.assert_has_calls(calls)
        self.assertEqual(worker.call_count, num_workers)

    @patch("tserver.multiplex.Queue.Queue")
    @patch("tserver.multiplex.Worker")
    def test_registerProcessor(self, worker, queue):
        """
        Test to check the registration of a thrift service processor.
        Validates that the right number of threads are created and that all
        threads created for the same processor share the same synchrnoized
        Queue.
        """
        test_worker = MagicMock()
        worker.return_value = test_worker

        test_queue = MagicMock()
        queue.return_value = test_queue

        m_processor = TMultiplexedProcessor()
        processor = MagicMock()

        num_workers = 2
        service_name = "Fake"
        m_processor.registerProcessor(service_name, processor, num_workers)
        queue.assert_called_once_with()
        self._check_worker_calls(service_name, num_workers, worker, test_queue)

        # Register a second processor and verify that we have a new queue
        # created, and is passed to the number of workers specified.
        num_workers = 3
        queue.reset_mock()
        worker.reset_mock()
        test_queue_1 = MagicMock()
        queue.return_value = test_queue_1
        m_processor = TMultiplexedProcessor()
        processor = MagicMock()

        m_processor.registerProcessor(service_name, processor, num_workers)
        queue.assert_called_once_with()
        self._check_worker_calls(
            service_name, num_workers, worker, test_queue_1)
        self.assertEqual(m_processor.services[service_name].processor,
                         processor)
        self.assertEqual(m_processor.services[service_name].name, service_name)
        self.assertEqual(m_processor.services[service_name].queue,
                         test_queue_1)

    @patch("tserver.multiplex.time.time")
    def test_process_queued(self, cur_time):
        """
        Test the basic process_queued functionality.
        +ve cases
        1. Enqueues the request in the right queue for the service.
        2. Checks that the queue is not full before enqueuing the request.
        -ve cases
        1. Fails if the processor is being shutdown.
        2. Fails if the message type is not a call or one way message.
        3. Fails if there is no service type in the message specified.
        4. Fails if the service is not registered.
        5. Fails if the queue is full.
        """

        # Fake the current time
        cur_time.return_value = 5

        # Start by doing a lot of magic.
        iprot = MagicMock()
        oprot = MagicMock()
        otrans = MagicMock()
        otrans.getvalue.return_value = 0
        callback = MagicMock()

        mock_queue = MagicMock()
        mock_queue.qsize.return_value = 5
        processor = MagicMock()
        service_mock = ServiceProcessor("Fake", processor, mock_queue,
                                        MagicMock(), 10)

        m_processor = TMultiplexedProcessor()
        m_processor.services["Fake"] = service_mock

        iprot.readMessageBegin.return_value = \
            ("Fake:test", TMessageType.CALL, 3)
        standardMessage = ("test", TMessageType.CALL, 3)
        m_iprot = StoredMessageProtocol(iprot, standardMessage)
        m_processor.process_queued(iprot, oprot, otrans, callback)

        # Invoke a custom matcher so we compare equivalence and not identity
        # for the StandardMessage
        matcher = Matcher(compare_standard_msg, m_iprot)

        # assert that queue size and put were called
        mock_queue.assert_has_calls([call.qsize()])
        mock_queue.put.assert_called_with([processor, matcher, oprot, otrans,
                                           callback, 5])

        # Negative tests

        # Test shutting down.
        callback.reset_mock()
        m_processor = TMultiplexedProcessor()
        m_processor.shutting_down = True
        m_processor.process_queued(iprot, oprot, otrans, callback)
        callback.assert_called_once_with(False, '')

        # Test invalid_message
        callback.reset_mock()
        iprot.readMessageBegin.return_value = \
            ("Faketest", TMessageType.CALL, 3)
        m_processor = TMultiplexedProcessor()
        m_processor.process_queued(iprot, oprot, otrans, callback)
        callback.assert_called_once_with(False, '')

        # Test unknown service
        callback.reset_mock()
        iprot.readMessageBegin.return_value = \
            ("Fake1:test", TMessageType.CALL, 3)
        m_processor = TMultiplexedProcessor()
        m_processor.process_queued(iprot, oprot, otrans, callback)
        callback.assert_called_once_with(False, '')

        # Test full queue
        callback.reset_mock()
        iprot.readMessageBegin.return_value = \
            ("Fake:test", TMessageType.CALL, 3)
        service_mock = ServiceProcessor("Fake", processor, mock_queue,
                                        MagicMock(), 10)
        m_processor = TMultiplexedProcessor()
        m_processor.services["Fake"] = service_mock
        mock_queue.qsize.return_value = 10
        m_processor.process_queued(iprot, oprot, otrans, callback)
        callback.assert_called_once_with(False, '')

    def test_shutdown(self):
        m_processor = TMultiplexedProcessor()
        processor = MagicMock()
        num_workers = 2
        services = ["Fake_1", "Fake_2"]
        for name in services:
            m_processor.registerProcessor(name, processor, num_workers)

        for name in services:
            self.assertEqual(len(m_processor.services[name].workers), 2)
            for worker in m_processor.services[name].workers:
                self.assertTrue(worker.is_alive())

        m_processor.shutdown()
        self.assertTrue(m_processor.shutting_down)
        for name in services:
            for worker in m_processor.services[name].workers:
                self.assertFalse(worker.is_alive())
