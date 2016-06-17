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
import tempfile
import time
import unittest

from hamcrest import *  # noqa
from host.placement.placement_manager import NoSuchResourceException
from mock import MagicMock
from mock import patch
from mock import call
from nose_parameterized import parameterized
from nose.tools import raises

from common import file_util
from common import services
from common.service_name import ServiceName
from gen.resource.ttypes import DatastoreType
from host.hypervisor.disk_manager import DiskAlreadyExistException
from host.hypervisor.esx.path_util import compond_path_join
from host.hypervisor.esx.path_util import TMP_IMAGE_FOLDER_NAME_PREFIX
from host.hypervisor.esx.path_util import METADATA_FILE_EXT
from host.image.image_manager import DirectoryNotFound
from host.image.image_manager import ImageManager
from host.hypervisor.esx.vim_client import VimClient


class TestImageManager(unittest.TestCase):
    """Image Manager tests."""

    # We can use even more unit test coverage of the image manager here

    def setUp(self):
        self.vim_client = VimClient(auto_sync=False)
        self.vim_client._content = MagicMock()
        self.ds_manager = MagicMock()
        services.register(ServiceName.AGENT_CONFIG, MagicMock())
        self.image_manager = ImageManager(self.vim_client, self.ds_manager)

    @patch("host.image.image_manager.ImageManager.reap_tmp_images")
    def test_periodic_reaper(self, mock_reap):
        """ Test that the we invoke the image reaper periodically """
        image_manager = ImageManager(self.vim_client, self.ds_manager)
        image_manager.monitor_for_cleanup(reap_interval=0.1)

        self.assertFalse(image_manager._image_reaper is None)

        retry = 0
        while mock_reap.call_count < 2 and retry < 10:
            time.sleep(0.1)
            retry += 1
        image_manager.cleanup()
        assert_that(mock_reap.call_count, greater_than(1))
        assert_that(retry, is_not(10), "reaper cleanup not called repeatedly")

    @parameterized.expand([
        (True, ),
        (False, )
    ])
    @patch("uuid.uuid4", return_value="fake_id")
    @patch("host.hypervisor.esx.path_util.os_datastore_root")
    def test_reap_tmp_images(self, _allow_grace_period, _os_datastore_root,
                             _uuid):
        """ Test that stray images are found and deleted by the reaper """
        ds = MagicMock()
        ds.id = "dsid"
        ds.type = DatastoreType.EXT3

        # In a random transient directory, set up a directory to act as the
        # tmp images folder and to contain a stray image folder with a file.
        tmpdir = file_util.mkdtemp(delete=True)
        tmp_ds_dir = os.path.join(tmpdir, ds.id)
        os.mkdir(tmp_ds_dir)
        tmp_image_dir = os.path.join(tmp_ds_dir, compond_path_join(TMP_IMAGE_FOLDER_NAME_PREFIX, "stray_image"))
        os.mkdir(tmp_image_dir)
        (fd, path) = tempfile.mkstemp(prefix='strayimage_', dir=tmp_image_dir)

        self.assertTrue(os.path.exists(path))

        def _fake_os_datastore_root(datastore):
            return os.path.join(tmpdir, datastore)

        _os_datastore_root.side_effect = _fake_os_datastore_root

        ds_manager = MagicMock()
        ds_manager.get_datastores.return_value = [ds]
        image_manager = ImageManager(self.vim_client, ds_manager)
        if not _allow_grace_period:
            image_manager.REAP_TMP_IMAGES_GRACE_PERIOD = 0.0
            time.sleep(0.1)
        image_manager.reap_tmp_images()

        if _allow_grace_period:
            # verify stray image is not deleted due to grace period
            self.assertTrue(os.path.exists(path))
        else:
            # verify stray image is deleted
            self.assertFalse(os.path.exists(path))

    @patch("pysdk.task.WaitForTask")
    @patch("uuid.uuid4", return_value="fake_id")
    @patch("os.path.exists")
    @patch("shutil.copy")
    @patch.object(VimClient, "move_file")
    @patch.object(VimClient, "copy_disk")
    @patch.object(ImageManager, "_get_datastore_type", return_value=DatastoreType.EXT3)
    @patch.object(ImageManager, "_check_image_repair", return_value=False)
    @patch.object(ImageManager, "check_and_validate_image", return_value=False)
    @patch.object(ImageManager, "_create_image_timestamp_file")
    @patch("host.image.image_manager.FileBackedLock")
    def test_copy_image(self, _flock, _create_image_timestamp, check_image, _check_image_repair,
                        _get_ds_type, _copy_disk, _mv_dir, _copy, _exists, _uuid, _wait_for_task):
        _exists.side_effect = (True,  # tmp_dir exists
                               True,  # dest image vmdk missing
                               True)  # source meta file present

        self.image_manager.copy_image("ds1", "foo", "ds2", "bar")

        os_path_prefix1 = '/vmfs/volumes/ds1'
        os_path_prefix2 = '/vmfs/volumes/ds2'

        assert_that(_copy.call_count, equal_to(1))
        _copy.assert_has_calls([
            call('%s/image_foo/foo.%s' % (os_path_prefix1, METADATA_FILE_EXT),
                 '/vmfs/volumes/ds2/tmp_image_fake_id/bar.%s' % METADATA_FILE_EXT), ])

        ds_path_prefix1 = '[] ' + os_path_prefix1

        expected_tmp_disk_ds_path = '%s/tmp_image_fake_id/bar.vmdk' % (os_path_prefix2)
        _copy_disk.assert_called_once_with('%s/image_foo/foo.vmdk' % ds_path_prefix1, expected_tmp_disk_ds_path)
        _mv_dir.assert_called_once_with('/vmfs/volumes/ds2/tmp_image_fake_id', '%s/image_bar' % os_path_prefix2)

        _create_image_timestamp.assert_called_once_with("/vmfs/volumes/ds2/tmp_image_fake_id")

    @patch("pysdk.task.WaitForTask")
    @patch("uuid.uuid4", return_value="fake_id")
    @patch("os.path.exists")
    @patch("os.makedirs")
    @patch("shutil.copy")
    @patch.object(VimClient, "copy_disk")
    @patch.object(ImageManager, "_get_datastore_type",
                  return_value=DatastoreType.EXT3)
    @patch.object(ImageManager, "check_image", return_value=False)
    @patch.object(ImageManager, "_create_image_timestamp_file")
    def test_create_tmp_image(self, _create_image_timestamp, check_image, _get_ds_type,
                              _copy_disk, _copy, _makedirs, _exists, _uuid, _wait_for_task):

        # Common case is the same as the one covered by test_copy_image.

        # Check that things work when the src metadata file doesn't exist.
        _exists.side_effect = (False, False, True)
        ds_path_prefix1 = '[] /vmfs/volumes/ds1'
        expected_tmp_disk_ds_path = "/vmfs/volumes/ds2/tmp_image_fake_id/bar.vmdk"
        self.image_manager._copy_to_tmp_image("ds1", "foo", "ds2", "bar")
        # Verify that we don't copy the metadata file.
        self.assertFalse(_copy.called)

        # Verify that we copy the disk correctly
        _copy_disk.assert_called_once_with('%s/image_foo/foo.vmdk' % ds_path_prefix1, expected_tmp_disk_ds_path)

        # check that we return an IO error if the copy of metadata fails.
        _copy.side_effect = IOError
        _exists.side_effect = (True, True)
        _copy_disk.reset_mock()
        self.assertRaises(IOError, self.image_manager._copy_to_tmp_image,
                          "ds1", "foo", "ds2", "bar")
        self.assertFalse(_copy_disk.called)
        _create_image_timestamp.assert_called_once_with(
            "/vmfs/volumes/ds2/tmp_image_fake_id")

    @patch.object(VimClient, "delete_file")
    @patch("os.path.exists", return_value=True)
    @patch("os.makedirs")
    @patch("shutil.rmtree")
    @patch("shutil.move")
    @patch.object(ImageManager, "_get_datastore_type",
                  return_value=DatastoreType.EXT3)
    @patch.object(ImageManager, "_check_image_repair", return_value=True)
    @patch("host.image.image_manager.FileBackedLock")
    @raises(DiskAlreadyExistException)
    def test_move_image(self, _flock, check_image, _get_ds_type, _mv_dir,
                        _rmtree, _makedirs, _exists, _delete_file):
        # Common case is covered in test_copy_image.

        # check that if destination image directory exists we don't call move
        # and just bail after removing the tmp dir
        _rmtree.reset_mock()
        _mv_dir.reset_mock()
        expected_tmp_disk_folder = '/vmfs/volumes/ds2/tmp_images/bar'
        expected_rm_calls = [call(expected_tmp_disk_folder)]
        self.image_manager._move_image("foo", "ds1", expected_tmp_disk_folder)
        self.assertEqual(expected_rm_calls, _rmtree.call_args_list)
        _makedirs.assert_called_once_with('/vmfs/volumes/ds1/images/fo')
        _flock.assert_called_once_with('/vmfs/volumes/ds1/image_foo',
                                       DatastoreType.EXT3, 3)
        _delete_file.assert_called_once_with('/vmfs/volumes/ds1/image_foo')

    @parameterized.expand([
        (True, ),
        (False, )
    ])
    @patch("os.path.exists")
    @patch.object(ImageManager, "_get_datastore_type",
                  return_value=DatastoreType.EXT3)
    @patch.object(ImageManager, "_create_image_timestamp_file")
    @patch("host.image.image_manager.FileBackedLock")
    def test_validate_existing_image(self,
                                     create,
                                     _flock,
                                     _create_timestamp_file,
                                     _get_ds_type,
                                     _path_exists):
        self._create_image_timestamp_file = create
        _path_exists.side_effect = self._local_os_path_exists
        _disk_folder = '/vmfs/volumes/ds1/image_foo'
        self.image_manager._check_image_repair("foo", "ds1")

        if create:
            _create_timestamp_file.assert_called_once_with(_disk_folder)
        else:
            assert not _create_timestamp_file.called

    def _local_os_path_exists(self, pathname):
        if not self._create_image_timestamp_file:
            return True
        if pathname.endswith(ImageManager.IMAGE_TIMESTAMP_FILE_NAME):
            return False
        else:
            return True

    def test_image_path(self):
        image_path = "/vmfs/volumes/ds/image_ttylinux/ttylinux.vmdk"
        ds = self.image_manager.get_datastore_id_from_path(image_path)
        image = self.image_manager.get_image_id_from_path(image_path)
        self.assertEqual(ds, "ds")
        self.assertEqual(image, "ttylinux")

    @patch.object(ImageManager, "_get_datastore_type")
    def test_create_image(self, _get_ds_type):
        image_id = "image_id"
        datastore_id = "ds1"
        _get_ds_type.side_effect = (DatastoreType.LOCAL_VMFS, DatastoreType.VSAN)

        tmp_image_path = self.image_manager.create_image(image_id, datastore_id)
        prefix = "[] /vmfs/volumes/%s/tmp_image_" % datastore_id
        self.assertTrue(tmp_image_path.startswith(prefix))

        tmp_image_path = self.image_manager.create_image(image_id, datastore_id)
        prefix = "[] /vmfs/volumes/%s/image_%s/tmp_image_" % (datastore_id, image_id)
        self.assertTrue(tmp_image_path.startswith(prefix))

    @patch.object(ImageManager, "_move_image")
    @patch.object(ImageManager, "_create_image_timestamp_file")
    @patch("os.path.exists")
    def test_finalize_image(self, _exists, _create_timestamp, move_image):

        # Happy path verify move is called with the right args.
        _exists.side_effect = ([True])
        self.image_manager.finalize_image("ds1", "[] /vmfs/volumes/ds1/foo", "img_1")
        move_image.assert_called_once_with('img_1', 'ds1', '/vmfs/volumes/ds1/foo')
        _create_timestamp.assert_called_once_with("/vmfs/volumes/ds1/image_img_1")

    @patch.object(ImageManager, "finalize_image")
    @patch.object(VimClient, "copy_disk")
    @patch("os.path.exists", return_value=True)
    def test_create_image_with_vm_disk(self, _exists, _copy_disk, _create_image):
        vm_disk_path = "/vmfs/volumes/dsname/vms/ab/cd.vmdk"
        self.image_manager.create_image_with_vm_disk(
            "ds1", "[] /vmfs/volumes/ds1/foo", "img_1", vm_disk_path)

        # Verify that we copy the disk correctly
        expected_tmp_disk_ds_path = "/vmfs/volumes/ds1/foo/img_1.vmdk"
        _copy_disk.assert_called_once_with(vm_disk_path, expected_tmp_disk_ds_path)
        _create_image.assert_called_once_with("ds1", "[] /vmfs/volumes/ds1/foo", "img_1")

    @patch("shutil.rmtree")
    @patch("os.path.exists")
    def test_delete_tmp_dir(self, _exists, _rmtree):
        self.image_manager.delete_tmp_dir("ds1", "foo")
        _exists.assert_called_once("/vmfs/volumes/ds1/foo")
        _rmtree.assert_called_once("/vmfs/volumes/ds1/foo")

        _exists.reset_mock()
        _exists.return_value = False
        _rmtree.reset_mock()
        self.assertRaises(DirectoryNotFound,
                          self.image_manager.delete_tmp_dir,
                          "ds1", "foo")
        _exists.assert_called_once("/vmfs/volumes/ds1/foo")
        self.assertFalse(_rmtree.called)

    def test_image_size(self):
        self.ds_manager.image_datastores.return_value = ["ds1", "ds2"]
        with patch("host.image.image_manager.os_vmdk_flat_path"
                   "") as image_path:
            tmpdir = file_util.mkdtemp(delete=True)
            image_path.return_value = tmpdir

            size = self.image_manager.image_size("image_id")
            self.assertTrue(size > 0)

    def test_image_size_not_exist(self):
        self.ds_manager.image_datastores.return_value = ["ds1", "ds2"]
        self.assertRaises(NoSuchResourceException,
                          self.image_manager.image_size,
                          "image_id")
