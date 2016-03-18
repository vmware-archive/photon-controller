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
from common.state import State

from host.hypervisor.task_runner import TaskRunner
from host.upgrade.softlink_generator import SoftLinkGenerator


class HostUpgradeTaskRunner(TaskRunner):
    def __init__(self, name, host_upgrade):
        super(HostUpgradeTaskRunner, self).__init__(name)
        self._logger = logging.getLogger(__name__)
        self._host_upgrade = host_upgrade

    # Override
    def execute_task(self):
        self._logger.info("HostUpgrade started")

        try:
            datastores = self._host_upgrade._datastore_manager.get_datastore_ids()

            soft_link_generator = SoftLinkGenerator()
            for ds in datastores:
                soft_link_generator.create_symlinks_to_new_image_path(ds)
        except:
            self._logger.exception("HostUpgrade failed")
        finally:
            self._host_upgrade.set_complete()

        self._logger.info("HostUpgrade completed")


class HostUpgrade:

    DEFAULT_TIMEOUT = 5 * 60
    PREVIOUS_VERSION = "previous_version"

    def __init__(self, datastore_manager, config_path):
        self._logger = logging.getLogger(__name__)
        self._datastore_manager = datastore_manager
        self._marker_file_path = os.path.join(config_path, "upgrade.json")
        self._timeout = HostUpgrade.DEFAULT_TIMEOUT
        self._task_runner = HostUpgradeTaskRunner(__name__, self)

    def set_upgrade_needed(self, previous_version=""):
        state = State(self._marker_file_path)
        state.set(HostUpgrade.PREVIOUS_VERSION, previous_version)

    def in_progress(self):
        return self._task_runner.is_running()

    def start(self, timeout=None):
        if not os.path.exists(self._marker_file_path):
            self._logger.exception("HostUpgrade is not needed")
            return

        if timeout:
            self._timeout = timeout
        self._task_runner.start(self._timeout)

    def stop(self):
        self._task_runner.stop()

    def wait_for_task_end(self):
        return self._task_runner.wait_for_task_end()

    def set_complete(self):
        try:
            os.remove(self._marker_file_path)
        except:
            pass
