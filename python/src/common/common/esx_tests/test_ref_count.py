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
from gen.resource.ttypes import DatastoreType
from ref_count_common_tests import RefCountCommonTests


class VmfsRefCountBasicTestCase(unittest.TestCase, RefCountCommonTests):

    def setUp(self):
        self._test_dir = "/vmfs/volumes/datastore1/"
        self._fs_type = DatastoreType.LOCAL_VMFS
        self.init()


if __name__ == '__main__':
    unittest.main()
