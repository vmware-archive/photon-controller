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
import os
import subprocess
import sys
import time
import unittest

from thrift.transport import TTransport

from common.photon_thrift.direct_client import DirectClient
from gen.agent import AgentControl
from gen.agent.ttypes import ProvisionRequest
from gen.agent.ttypes import ProvisionResultCode
from gen.common.ttypes import ServerAddress
from gen.host.ttypes import CopyImageRequest
from gen.host.ttypes import CopyImageResultCode
from gen.host.ttypes import GetConfigResultCode
from gen.host.ttypes import GetImagesRequest
from gen.host.ttypes import GetImagesResultCode
from gen.resource.ttypes import Host
from gen.resource.ttypes import Image
from gen.resource.ttypes import ImageDatastore

logger = logging.getLogger()
logger.level = logging.DEBUG
stream_handler = logging.StreamHandler(sys.stdout)
logger.addHandler(stream_handler)


class ThinCopyTestCase(unittest.TestCase):
    """
    Test thin copy
    """
    def setUp(self):
        self.image_path = \
            "{0[vmfs_dir]}/{0[datastore]}/images/{0[header]}/{0[image]}"
        self.vmdk_path = self.image_path + "/{0[vmdk_file]}"
        self.vmdk_flat_path = self.image_path + "/{0[vmdk_flat_file]}"

        self.host_client = self._create_client()
        self.agent_client = self._create_agent_client()
        self.infos = []

    def tearDown(self):
        self.host_client.close()
        self.agent_client.close()

        for info in self.infos:
            self._remove_image(info)

    def _connect_client(self, service, cls, server):
        """ Utility method to connect to a remote agent """
        max_sleep_time = 32
        sleep_time = 0.1
        while sleep_time < max_sleep_time:
            try:
                client = DirectClient(service, cls, server, 8835)
                client.connect()
                return client
            except TTransport.TTransportException:
                time.sleep(sleep_time)
                sleep_time *= 2
        self.fail("Cannot connect to agent %s" % server)

    def _create_client(self):
        return self._connect_client("Host", Host.Client, "localhost")

    def _create_agent_client(self):
        return self._connect_client("AgentControl", AgentControl.Client,
                                    "localhost")

    def _provision_host(self, datastore):
        """ Provisions the agents on the remote hosts """
        image_datastore = datastore

        req = ProvisionRequest()
        req.availability_zone = "test"
        req.datastores = [datastore]
        req.networks = ["VM Network"]
        req.address = ServerAddress(host="localhost", port=8835)
        req.chairman_server = [ServerAddress("localhost", 13000)]
        req.memory_overcommit = 2.0
        req.image_datastore_info = ImageDatastore(
            name=image_datastore,
            used_for_vms=True)
        res = self.agent_client.provision(req)

        # This will trigger a restart if the agent config changes, which
        # will happen the first time provision_hosts is called.
        self.assertEqual(res.result, ProvisionResultCode.OK)

        # If a restart is required subsequent calls will fail.
        # This is an indirect way to determine if a restart is required.
        host_config_request = Host.GetConfigRequest()
        res = self.host_client.get_host_config(host_config_request)

        if (res.result != GetConfigResultCode.OK):
            # Sleep for 15 s for the agent to reboot and come back up
            time.sleep(15)

    def _prepare_info(self, image):
        info = {}
        info["vmfs_dir"] = "/vmfs/volumes"
        info["datastore"] = "datastore1"
        info['image'] = image
        info['header'] = image[0:2]
        info["vmdk_file"] = info['image'] + ".vmdk"
        info["vmdk_flat_file"] = info['image'] + "-flat.vmdk"
        return info

    def _check_vmdk_size_equals_to_zero(self, info):
        _command = ['du', "-ah", self.vmdk_flat_path.format(info)]
        _p = subprocess.Popen(_command, stdout=subprocess.PIPE)
        _result = _p.communicate()[0]
        # assert flat vmdk real size should be 0
        self.assertEqual(_result[0], "0")

    def _create_thin_vmdk(self, info):
        if not os.path.exists(self.image_path.format(info)):
            os.makedirs(self.image_path.format(info))

        _command = ["vmkfstools", "-c", "100m", "-d", "thin",
                    self.vmdk_path.format(info)]

        _rc = subprocess.call(_command)
        self.assertEqual(_rc, 0)

        _thinProvisionedFlag = False

        with open(self.vmdk_path.format(info), 'r') as fd:
            for line in fd:
                name, var = line.partition("=")[::2]
                if name.strip().lower().find("thinprovisioned") != -1:
                    if var.find("1") != -1:
                        _thinProvisionedFlag = True
                    break

        self.assertEqual(_thinProvisionedFlag, True)

    def _find_configured_datastore_in_host_config(self, info):
        config_request = Host.GetConfigRequest()
        config_response = self.host_client.get_host_config(config_request)
        self.assertNotEqual(config_response.hostConfig.datastores, None)
        self.assertNotEqual(len(config_response.hostConfig.datastores), 0)

        logger.debug("Configured test image datastore %s" %
                     info['datastore'])

        for datastore_item in config_response.hostConfig.datastores:
            if datastore_item.name == info['datastore']:
                return datastore_item

        self.fail("datastore list returned by agent does not contain %s" %
                  info['datastore'])

    def _remove_image(self, info):
        _remove_image_command = ["rm", "-rf", self.image_path.format(info)]
        _rc = subprocess.call(_remove_image_command)
        self.assertEqual(_rc, 0)

    def test_create_thin_vmdk(self):
        src_image_info = self._prepare_info("thin_copy")
        self.infos.append(src_image_info)

        # Provision the host
        self._provision_host(src_image_info['datastore'])
        # Make client connections again
        self.host_client = self._create_client()

        datastore = \
            self._find_configured_datastore_in_host_config(src_image_info)
        self.assertNotEqual(datastore, None)

        self._create_thin_vmdk(src_image_info)
        self._check_vmdk_size_equals_to_zero(src_image_info)

        dst_image_info = self._prepare_info("copy_thin_copy")
        self.infos.append(dst_image_info)

        src_image = Image(src_image_info['image'], datastore)
        dst_image = Image(dst_image_info['image'], datastore)

        # Verify thin copied image is not in datastore
        request = GetImagesRequest(datastore.id)
        response = self.host_client.get_images(request)
        self.assertEqual(response.result, GetImagesResultCode.OK)
        self.assertEqual(src_image_info['image'] in response.image_ids, True)
        self.assertEqual(dst_image_info['image'] in response.image_ids, False)
        image_number = len(response.image_ids)

        # Copy thin image
        request = CopyImageRequest(src_image, dst_image)
        response = self.host_client.copy_image(request)
        self.assertEqual(response.result, CopyImageResultCode.OK)

        # Verify thin copied image is in datastore
        request = GetImagesRequest(datastore.id)
        response = self.host_client.get_images(request)
        self.assertEqual(response.result, GetImagesResultCode.OK)
        self.assertEqual(src_image_info['image'] in response.image_ids, True)
        self.assertEqual(dst_image_info['image'] in response.image_ids, True)
        self.assertEqual(len(response.image_ids), image_number + 1)

        # check thin copied vmdk file size
        self._check_vmdk_size_equals_to_zero(dst_image_info)


if __name__ == '__main__':
    unittest.main()
