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

import common
from common.service_name import ServiceName
from host.upgrade.softlink_generator import SoftLinkGenerator


class HostUpgrade:

    @staticmethod
    def upgrade():
        u = HostUpgrade()
        try:
            u._logger.info("HostUpgrade started")

            u._upgrade()

            u._logger.info("HostUpgrade completed")
        except:
            u._logger.exception("HostUpgrade failed")

    def __init__(self):
        self._logger = logging.getLogger(__name__)

    def _upgrade(self):
        hypervisor = common.services.get(ServiceName.HYPERVISOR)
        datastores = hypervisor.datastore_manager.get_datastore_ids()

        soft_link_generator = SoftLinkGenerator()
        for ds in datastores:
            soft_link_generator.create_symlinks_to_new_image_path(ds)
