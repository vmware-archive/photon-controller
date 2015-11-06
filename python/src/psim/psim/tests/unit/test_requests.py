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

import os
import unittest

from hamcrest import *  # noqa
from matchers import *  # noqa

from psim.requests import Requests


class RequestsTestCase(unittest.TestCase):

    def setUp(self):
        self.test_dir = os.path.join(os.path.dirname(__file__), 'test_files')

    def tearDown(self):
        pass

    def xtest_load_requests(self):
        """
        Disabled since test_files not updated. Will enable in a separate
        commit.
        """
        requests = Requests(os.path.join(self.test_dir, 'test_req.yml'))

        assert_that(len(requests.requests), equal_to(2))
        request = requests.requests[0]
        assert_that(request.resource.vm.flavor, equal_to('core-1'))
        assert_that(len(request.resource.disks), equal_to(3))
        assert_that(request.resource.disks[0].flavor, equal_to('core-2'))
        assert_that(request.resource.disks[1].flavor, equal_to('core-3'))
        assert_that(request.resource.disks[2].flavor, equal_to('core-4'))


if __name__ == '__main__':
    unittest.main()
