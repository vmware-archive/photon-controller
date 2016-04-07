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

import os
from host.hypervisor.vm_utils \
    import parse_vmdk
from matchers import *  # noqa


class VmUtilsTest(unittest.TestCase):

    def setUp(self):
        self.base_dir = os.path.dirname(__file__)
        self.test_dir = os.path.join(self.base_dir, "test_files", "vm_good")
    def test_parse_vmdk(self):
        vmdk_pathname = os.path.join(self.test_dir, 'good.vmdk')
        dictionary = parse_vmdk(vmdk_pathname)
        assert_that(dictionary["version"] == "1")
        assert_that(dictionary["encoding"] == "UTF-8")
        assert_that(dictionary["CID"] == "fffffffe")
        assert_that(dictionary["parentCID"] == "fffffffe")
        assert_that(dictionary["isNativeSnapshot"] == "no")
        assert_that(dictionary["createType"] == "vmfsSparse")
        assert_that(dictionary["parentFileNameHint"] ==
                    "/vmfs/volumes/555ca9f8-9f24fa2c-41c1-0025b5414043/"
                    "image_92e62599-6689-4a8f-ba2a-633914b5048e/92e"
                    "62599-6689-4a8f-ba2a-633914b5048e.vmdk")
