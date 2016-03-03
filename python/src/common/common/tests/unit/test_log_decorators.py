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

from hamcrest import *  # noqa
from mock import *  # noqa

from common.log import log_duration_with


class TestLogDecorators(unittest.TestCase):

    @patch("time.time")
    def test_log_duration_with(self, time_fn):
        time_fn.side_effect = [1000, 2000]

        dummy = DummyClass()
        dummy.foo_1()

        time_fn.side_effect = [1000, 2000]
        dummy.foo_2("vm_id_1", "vm_id_2")

        time_fn.side_effect = [1000, 2000]
        dummy.foo_3("vm_id_1", "vm_id_2",
                    vm_info="vm_info_1", vm_stat="vm_stat_1")

        assert_that(dummy._logger.info.call_args_list,
                    is_([call('foo_1: took 1000')]))

        assert_that(dummy._logger.debug.call_args_list,
                    is_([call('foo_2: vm_id_1 vm_id_2 took 1000')]))

        assert_that(dummy._logger.warn.call_args_list,
                    is_([call('foo_3: vm_id_1 vm_id_2 vm_stat:'
                        'vm_stat_1 vm_info:vm_info_1 took 1000')]))


class DummyClass(object):

    def __init__(self):
        self._logger = MagicMock()

    @log_duration_with()
    def foo_1(self):
        pass

    @log_duration_with("debug")
    def foo_2(self, arg_0, arg_1):
        pass

    @log_duration_with("warn")
    def foo_3(self, arg_0, arg_1, vm_info=None, vm_stat=None):
        pass
