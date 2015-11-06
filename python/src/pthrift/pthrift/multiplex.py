# Portions Copyright 2015 VMware, Inc. All Rights Reserved.

# Licensed to the Apache Software Foundation (ASF) under one
# or more contributor license agreements. See the NOTICE file
# distributed with this work for additional information
# regarding copyright ownership. The ASF licenses this file
# to you under the Apache License, Version 2.0 (the
# "License"); you may not use this file except in compliance
# with the License. You may obtain a copy of the License at
#
#   http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing,
# software distributed under the License is distributed on an
# "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
# KIND, either express or implied. See the License for the
# specific language governing permissions and limitations
# under the License.
#
"""
Python port of
https://issues.apache.org/jira/secure/attachment/12416581/THRIFT-563.patch with
the following additions
Implement a new process_queued interface to be used with our non blocking
thrift server implementation. For other thrift servers the process
implementation remains unmodified.
The following modifications are made:
    The parsing logic is factored out into an internal method to be used by
    process and process_queued.
    Create a threadpool per service and queue requests per service if
    requested, defaults to no workers.
    Allow for capping of queue length to prevent DDOS
    Leave the process interface as is for compatability with other thrift
    servers.
"""

import logging
import Queue
import time
from tserver.thrift_server import Worker

from thrift.Thrift import TProcessor, TMessageType, TException
from types import MethodType, UnboundMethodType, FunctionType, LambdaType, \
    BuiltinFunctionType, BuiltinMethodType

SEPARATOR = ":"


class ServiceProcessor(object):
    """
    Helper class containing information about the specific service processor
    """

    def __init__(self, service_name, processor, queue, workers,
                 max_queued_entries=0):
        """
        Constructor:
            service_name: the service name of the processor
            processor: The actual thrift processor
            queue: The synchronized queue instance associated with the service
            workers: The worker threads associated with the service.
            max_queue_entries: The max_queue size for the service. Value of 0
            indicates that the queue is unbounded.
        """
        self._name = service_name
        self._processor = processor
        self._queue = queue
        self._workers = workers
        self._max_queued_entries = max_queued_entries

    @property
    def name(self):
        return self._name

    @property
    def processor(self):
        return self._processor

    @property
    def queue(self):
        """
        accessor for the queue
        The underlying queue is unbounded so it is the callers
        responsibility to check if the queue is_full before inserting elemets
        into the queue using the put call.
        """
        return self._queue

    @property
    def workers(self):
        return self._workers

    def is_full(self):
        """
        Check if the queue is full
        """
        # If queue is bounded check if it is full
        if self._max_queued_entries > 0:
            return (self.queue.qsize() >= self._max_queued_entries)
        return False


class TMultiplexedProcessor(TProcessor):
    def __init__(self):
        self.services = {}
        self.shutting_down = False

    def registerProcessor(self, service_name, processor, num_workers=0,
                          max_queued_entries=0):
        """
        Method to register a processor for a thrift service.
        service_name: The well known string service name.
        processor: The processor implementing the service interface
        num_workers: The number of worker threads for this service
        max_queued_entires: The maximum number of entries that can be queued
        for the service, a value < 0 indicates an
        unbounded queue.
        The method has the sideeffect of spawning a num_workers number of
        threads associated with the service.
        """
        # Use an unbounded queue and fail the insert if the queue size is
        # greater than max_queued_entries otherwise we will block inserts which
        # we don't want to do in the multiplex processor.
        service_queue = Queue.Queue()
        workers = []
        thread_name = "{0}-Thread-{1}"

        for i in xrange(num_workers):
            worker = Worker(
                service_queue, name=thread_name.format(service_name, i))
            worker.setDaemon(True)
            worker.start()
            workers.append(worker)

        # Keep track of workers for shutdown.
        self.services[service_name] = ServiceProcessor(service_name, processor,
                                                       service_queue, workers,
                                                       max_queued_entries)
        logging.info("Initialized service %s, with threadpool size %d"
                     % (service_name, num_workers))

    def shutdown(self):
        """
        Shutdown the processor
        Join all the worker threads as part of shutdown """
        self.shutting_down = True
        for service_processor in self.services.itervalues():
            for worker in service_processor.workers:
                worker.queue.put([None, None, None, None, None, None])
            for worker in service_processor.workers:
                worker.join()

    def process_queued(self, iprot, oprot, otrans, callback):
        try:
            # Process the input message and extract the service_name and the
            # corresponding StoredMessageProtocol object for the input
            service_name, m_iprot = self._process(iprot)
            # Queue the request now.
            queue = self.services[service_name].queue
            processor = self.services[service_name].processor
            queue.put([processor, m_iprot, oprot, otrans, callback,
                       time.time()])
        except:
            # Detailed log already posted at the place where the exception is
            # raised. Only call the callback if we didn't put the message into
            # the queue. For the case where the msg is enqued the Worker will
            # trigger the callback
            callback(False, '')

    def process(self, iprot, oprot):
        # Extract the service name and the message protocol object
        service_name, m_iprot = self._process(iprot)
        processor = self.services[service_name].processor

        return processor.process(m_iprot, oprot)

    def _process(self, iprot):
        """
        Extract the service name and the StoredMessageProtocol
        representation of the input protocol message.
        raises: TException if i_msg is invalid or service name is not known.
        returns: service_name, m_iprot
        """
        # Check if we are shutting down.
        if self.shutting_down:
            logging.warning("Processor shutting down, failing request")
            raise TException("Processor shuttind down")

        # Check if it is a valid message
        (name, m_type, seqid) = iprot.readMessageBegin()
        if m_type != TMessageType.CALL & m_type != TMessageType.ONEWAY:
            logging.warning("Invalid message: This should not have happened")
            raise TException("Invalid message type")

        # Check if we can find the service name
        index = name.find(SEPARATOR)
        if index < 0:
            logging.warning(
                "Service name not found in message name: " + name +
                ". Did you forget to use TMultiplexProtocol in your client?")
            raise TException("Service name not found")

        # Check that we know of the service
        service_name = name[0:index]
        m_call = name[index+len(SEPARATOR):]
        if service_name not in self.services:
            logging.warning("Service name not found: " + service_name +
                            ". Did you forget to call registerProcessor()?")
            raise TException("Service not found")

        # Check if the queue is already backed up.
        if (self.services[service_name].is_full()):
            err = "Queue is full for service: %s" % service_name
            logging.warning(err)
            raise TException(err)

        standardMessage = (
            m_call,
            m_type,
            seqid
        )

        m_iprot = StoredMessageProtocol(iprot, standardMessage)
        return service_name, m_iprot


class TProtocolDecorator(object):
    def __init__(self, protocol):
        self.protocol = protocol

    def __getattr__(self, name):
        if hasattr(self.protocol, name):
            member = getattr(self.protocol, name)
            if type(member) in [MethodType, UnboundMethodType, FunctionType,
                                LambdaType, BuiltinFunctionType,
                                BuiltinMethodType]:
                return lambda *args, **kwargs: self._wrap(member, args, kwargs)
            else:
                return member
        raise AttributeError(name)

    def _wrap(self, func, args, kwargs):
        if type(func) == MethodType:
            result = func(*args, **kwargs)
        else:
            result = func(self.protocol, *args, **kwargs)
        return result


class TMultiplexedProtocol(TProtocolDecorator):
    def __init__(self, protocol, service_name):
        super(TMultiplexedProtocol, self).__init__(protocol)
        self.service_name = service_name

    def writeMessageBegin(self, name, m_type, seqid):
        if (m_type == TMessageType.CALL or
                m_type == TMessageType.ONEWAY):
            self.protocol.writeMessageBegin(
                self.service_name + SEPARATOR + name,
                m_type,
                seqid
            )
        else:
            self.protocol.writeMessageBegin(name, m_type, seqid)


class StoredMessageProtocol(TProtocolDecorator):
    def __init__(self, protocol, messageBegin):
        super(StoredMessageProtocol, self).__init__(protocol)
        self.messageBegin = messageBegin

    def readMessageBegin(self):
        return self.messageBegin
