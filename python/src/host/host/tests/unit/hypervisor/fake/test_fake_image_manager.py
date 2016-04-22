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
from mock import MagicMock
from nose_parameterized import parameterized

from common.file_util import mkdtemp
from host.hypervisor.fake.image_manager import FakeImageManager as Fim


class TestFakeImageManager(unittest.TestCase):
    """Fake placement manager tests."""

    def setUp(self):
        self.hypervisor = MagicMock()
        self.datastore_id = "ds_id_1"
        self.datastore_name = "ds_name_1"
        self.hypervisor.datastore_manager.get_datastore_ids.return_value = \
            [self.datastore_id]
        self.hypervisor.datastore_manager.datastore_name.return_value = \
            self.datastore_name
        self.im = Fim(self.hypervisor, "ds_id_1", mkdtemp(delete=True))
        self.hypervisor.image_manager = self.im

    @parameterized.expand([
        ([None], None),
        ([None, "ds1"], "ds1"),
        (["ds1", None], "ds1"),
    ])
    def test_get_image_ids_from_disks(self, disk_ids, expected):
        disks = []
        for disk_id in disk_ids:
            disk = MagicMock()
            disk.image.id = disk_id
            disks.append(disk)
        assert_that(self.im.get_image_id_from_disks(disks), is_(expected))

    @parameterized.expand([
        ("image_id", ["datastore_id_1"],
         ["datastore_id_1"], ["datastore_id_1"]),
        ("image_id", ["datastore_id_2"],
         ["datastore_id_1", "datastore_id_2"], ["datastore_id_2"]),
        ("image_id", ["datastore_id_1"],
         ["datastore_id_2", "datastore_id_3"], [])
    ])
    def test_datastores_with_images(self, image_id, ds_ids_with_image,
                                    ds_ids_to_search, expected):
        for ds_id in ds_ids_with_image:
            self.im.deploy_image("ignored", image_id, ds_id)
        result = self.im.datastores_with_image(image_id, ds_ids_to_search)
        assert_that(result, is_(expected))

    @parameterized.expand([
        ("ds1", "disk1", "ds2", "disk2"),
        ("ds1", "disk1", "ds2", "disk1"),
    ])
    def test_deploy_copy_image(self, src_ds, src_id, dst_ds, dst_id):
        self.im.deploy_image("ignored", src_id, src_ds)
        assert_that(self.im.check_image(src_id, src_ds), is_(True))

        self.im.copy_image(src_ds, src_id, dst_ds, dst_id)
        assert_that(self.im.check_image(dst_id, dst_ds), is_(True))

    def test_image_path(self):
        ds = "ds"
        image = "image"
        image_path = self.im.image_file(ds, image)
        assert_that(self.im.get_datastore_id_from_path(image_path), is_(ds))
        assert_that(self.im.get_image_id_from_path(image_path), is_(image))
