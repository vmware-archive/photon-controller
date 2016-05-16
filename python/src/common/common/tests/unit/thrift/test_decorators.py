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
import re
import threading
import unittest

from hamcrest import *  # noqa
from logging import StreamHandler
from mock import *  # noqa
from thrift.Thrift import TType

import common
from common.service_name import ServiceName
from common.photon_thrift.decorators import error_handler
from common.photon_thrift.decorators import log_request
from thrift.protocol import TProtocol


class TestDecorators(unittest.TestCase):

    @patch("threading.current_thread")
    def test_log_format(self, thread_fn):
        """
        Test log format.
            - Test Worker thread name is correctly being logged.
        """
        from common.log import _add_handler
        from common.log import FILE_LOG_FORMAT
        from common.log import SYSLOG_FORMAT
        common.services.register(ServiceName.REQUEST_ID, threading.local())

        match_format = \
            r".*?" \
            r"\[(.*?):\w+:(?P<THREAD_NAME>.*?)\]"
        matcher = re.compile(match_format)

        self.result = None
        logger = logging.getLogger()
        stream_mock = MagicMock()

        def _side_effect(arg):
            self.result = arg

        # test SYSLOG_FORMAT
        stream_mock.write.side_effect = _side_effect
        syslog_formatter = logging.Formatter(SYSLOG_FORMAT)
        handler = StreamHandler(stream_mock)

        _add_handler(handler, syslog_formatter, logging.DEBUG, logger)

        logger.info("TEST")

        self.assertTrue(stream_mock.write.called)
        regex_result = matcher.match(self.result)
        self.assertNotEqual(regex_result, None)
        stream_mock.reset_mock()
        stream_mock.write.side_effect = _side_effect

        # test FILE_LOG_FORMAT
        match_format = \
            r".*?\s+\[.*?\]\s+" \
            r"\[(.*?):\w+:(?P<THREAD_NAME>.*?)\]"
        matcher = re.compile(match_format)
        filelog_formatter = logging.Formatter(FILE_LOG_FORMAT)
        handler = StreamHandler(stream_mock)

        _add_handler(handler, filelog_formatter, logging.DEBUG, logger)

        logger.info("TEST")

        self.assertTrue(stream_mock.write.called)
        regex_result = matcher.match(self.result)
        self.assertNotEqual(regex_result, None)

    @patch("time.time")
    @patch("threading.current_thread")
    def test_log_request(self, thread_fn, time_fn):
        time_fn.side_effect = [1000, 2000]
        common.services.register(ServiceName.REQUEST_ID, threading.local())

        dummy = DummyClass()
        dummy.foo(DummyRequest("101010"))
        assert_that(dummy._logger.log.call_args_list,
                    is_([
                        call(logging.INFO, "%s no tracing", "101010"),
                        call(logging.INFO, "result:%d [Duration:%f] %s",
                             DummyResultCode.OK, 1000,
                             "DummyResponse(result:0)")
                        ]))

    def test_nested_log_request(self):
        common.services.register(ServiceName.REQUEST_ID, threading.local())
        queued_time = threading.local()
        queued_time.value = 50
        dummy = DummyClass()
        dummy.foo_caller(DummyRequest("101010"))

    def test_error_handler(self):
        dummy = DummyClass()
        response = dummy.bar(DummyRequest(None))
        assert_that(response.result, is_(DummyResultCode.OK))
        assert_that(dummy._logger.warning.called, is_(False))

        response = dummy.bar(DummyRequest("101010"))
        assert_that(response, is_(instance_of(DummyResponse)))
        assert_that(response.result, is_(DummyResultCode.SYSTEM_ERROR))
        assert_that(dummy._logger.warning.called, is_(True))


class DummyRequest(object):

    thrift_spec = (
        None,
        (1, TType.STRING, 'value', None, None, ),
    )

    def __init__(self, value=None):
        self.value = value

    def __repr__(self):
        return self.value

    def validate(self):
        pass


class DummyResponse(object):

    thrift_spec = (
        None,
        (1, TType.I32, 'result', None, None, ),
    )

    def __init__(self, result=None):
        self.result = result

    def __repr__(self):
        return "DummyResponse(result:%d)" % self.result

    def validate(self):
        if self.result is None:
            raise TProtocol.TProtocolException(
                message='Required field result is unset!')


class DummyResultCode(object):

    OK = 0
    SYSTEM_ERROR = 1


class DummyClass(object):

    def __init__(self):
        self._logger = MagicMock()

    @log_request
    def foo(self, request):
        return DummyResponse(DummyResultCode.OK)

    @log_request
    def foo_caller(self, request):
        return self.foo(request)

    @error_handler(DummyResponse, DummyResultCode)
    def bar(self, request):
        if request.value:
            raise ValueError()
        return DummyResponse(DummyResultCode.OK)
