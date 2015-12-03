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

import enum
import json
import logging
import os
import threading


import common

from common.file_util import atomic_write_file
from common.lock import lock_with
from gen.common.ttypes import ServerAddress


class InvalidConfig(Exception):
    """ Exception thrown when an agent config is not valid """
    pass


@enum.unique
class CallbackType(enum.Enum):
    CHAIRMAN = 1
    CPU_OVERCOMMIT = 2
    MEMORY_OVERCOMMIT = 3


class AgentConfig(object):
    """
    Class encapsulating the agent configuration options and rules around
    updating certain configuration items.

    Options can be set in three ways in increasing order of precedence:
        a - Through the config file read at starup time
        b - Through a thrift interface when the agent is running, certain
        options cannot be update on running agents once set.

    Updating certain options require a reboot to take effect. The
    reboot_required attribute captures this (e.g. updates to host port number)
    """
    def __init__(self, config_path):
        """
        Constructor
        """
        self._logger = logging.getLogger(__name__)
        if config_path:
            self._config_path = config_path
        else:
            self._config_path = "/etc/opt/vmware/photon/controller"

        self._config_file_name = "config.json"

        # Populate the config with default settings
        self._initialize_fields()
        # Read the options from the config file
        self._load_config()
        # No reboot required yet.
        self._reboot_required = False
        # Option callbacks
        self._option_callbacks = {}
        self._callback_lock = threading.Lock()

    def _initialize_fields(self):
        self.hostname = "localhost"
        self.host_port = 8835
        self.log_level = "info"
        self.logging_file = "/scratch/log/photon-controller-agent.log"
        self.logging_file_size = 10 * 1024 * 1024
        self.logging_file_backup_count = 10
        self.no_syslog = None
        self.console_log = None
        self.hypervisor = "esx"
        self.availability_zone = None
        self._datastores = []
        self._image_datastores = []
        self._networks = None
        self.workers = 32
        self.bootstrap_poll_frequency = 5
        self.chairman = []
        self.memory_overcommit = 1.0
        self.cpu_overcommit = 16.0  # Follow Openstack's default value
        self.wait_timeout = 10
        self.host_service_threads = 20
        self.scheduler_service_threads = 32
        self.control_service_threads = 1
        self.heartbeat_interval_sec = 10
        self.heartbeat_timeout_factor = 6
        self.thrift_timeout_sec = 3
        self.utilization_transfer_ratio = 9
        self.management_only = None
        self.in_uwsim = None
        self.host_id = None

    @property
    def datastores(self):
        return self._datastores

    @datastores.setter
    def datastores(self, value):
        if value is None:
            value = []
        self._datastores = value

    @property
    def image_datastores(self):
        return self._image_datastores

    @image_datastores.setter
    def image_datastores(self, value):
        if value is None:
            value = []
        self._image_datastores = value

    @property
    def networks(self):
        return self._networks

    @networks.setter
    def networks(self, value):
        if value is None:
            value = []
        self._networks = value

    @property
    def callback_type(self):
        return CallbackType

    @property
    def bootstrap_ready(self):
        """
        Property indicating if we have all the configuration required for the
        agent to be bootstrappable. i.e. expose a thrift endpoint for accepting
        the provisioning configuration
        """
        return (self.hostname and self.host_port and self.availability_zone and
                self.chairman and self.host_id)

    @property
    def reboot_required(self):
        """ Return true if the configuration update requires a reboot """
        return self._reboot_required

    def update_config(self, provision_req):
        """
        Update the agent configuration using the provisioning request
        configuration.
        The update is considered to be a fully populated object, we don't
        support partial updates. A value of None for an optional property
        indicates that the value is being unset.

        @param provision_req: The provision request
        """
        reboot = False
        if self.availability_zone != provision_req.availability_zone:
            self.availability_zone = provision_req.availability_zone
            reboot = True
        if self.datastores != provision_req.datastores:
            self.datastores = provision_req.datastores
            reboot = True
        if self.networks != provision_req.networks:
            self.networks = provision_req.networks
            reboot = True
        host = "localhost"
        port = 8835
        if provision_req.address:
            host = provision_req.address.host
            port = provision_req.address.port
        if self.hostname != host:
            self.hostname = host
            reboot = True
        if self.host_port != port:
            self.host_port = port
            reboot = True
        image_datastores = self._convert_image_datastores(
            provision_req.image_datastores)
        if self.image_datastores != image_datastores:
            self.image_datastores = image_datastores
            reboot = True
        if self.management_only != provision_req.management_only:
            self.management_only = provision_req.management_only
            reboot = True
        if self.host_id != provision_req.host_id:
            self.host_id = provision_req.host_id
            reboot = True

        chairman_list = \
            self._parse_chairman_server_address(provision_req.chairman_server)
        if self.chairman != chairman_list:
            self.chairman = chairman_list
            self._trigger_callbacks(CallbackType.CHAIRMAN, self.chairman_list)

        if self.memory_overcommit != provision_req.memory_overcommit:
            self.memory_overcommit = provision_req.memory_overcommit
            self._trigger_callbacks(CallbackType.MEMORY_OVERCOMMIT,
                                    self.memory_overcommit)

        if self.cpu_overcommit != provision_req.cpu_overcommit:
            self.cpu_overcommit = provision_req.cpu_overcommit
            self._trigger_callbacks(CallbackType.CPU_OVERCOMMIT,
                                    provision_req.cpu_overcommit)

        # Persist the updates to the config file.
        self._persist_config()

        if reboot:
            self._logger.info("Agent configuration updated reboot required")
            if not self.bootstrap_ready:
                self._logger.warning("Agent not fully configured %s" %
                                     str(self.__dict__))
            self._reboot_required = True

    @property
    def chairman_list(self):
        return self._parse_chairman_list(self.chairman)

    @lock_with("_callback_lock")
    def on_config_change(self, option, callback):
        """
        Register a callback on config change for a specific option. The
        callback will be called whenever the configuration changes.
        """
        if option not in self._option_callbacks:
            self._option_callbacks[option] = []
        self._option_callbacks[option].append(callback)

    @lock_with("_callback_lock")
    def _trigger_callbacks(self, option, new_value):
        """
        Trigger callbacks for a specific option
        """
        if option in self._option_callbacks:
            for callback in self._option_callbacks[option]:
                callback(new_value)

    def _parse_chairman_list(self, chairman_list):
        """
        Converts a list of chairman with port config into a list of
        ServerAddress.
        Chairman list is persisted as a list of "ip:port"
        :param chairman_list: The list of chairman as a comma separated string.
        :rtype list of chairman service addresses of type ServerAddress
        """
        if not chairman_list:
            return []

        server_list = []
        for server in chairman_list:
            try:
                host, port = server.split(":")
                server_list.append(ServerAddress(host=host, port=int(port)))
            except ValueError as ex:
                self._logger.warning(
                    "Failed to parse server %s, Invalid delemiter" % server)
                print ex
                pass
            except AttributeError as ex:
                self._logger.warning("Failed to parse chairman server %s" %
                                     server)
                print ex
                pass
        return server_list

    def _parse_chairman_server_address(self, server_addresses):
        """
        Converts a thrift list of ServeAddress objects to a list of : separated
        ip:port pairs.
        :type server_addresses a list of ServerAddress objects representing the
        list of chairman services
        :rtype server_str list of server addresses
        """
        servers = []
        if server_addresses:
            for address in server_addresses:
                server_str = str(address.host) + ":" + str(address.port)
                servers.append(server_str)
        return servers

    def _convert_image_datastores(self, image_datastores):
        """
        Convert a set of ImageDatastore thrift struct to a simple dict.

        Deployer sends a set of image datastores as a part of the provision
        request. This method converts the set to a list of dicts to be saved
        in config.json. Here is an example of the image_datastores field in
        config.json:

        > "image_datastores": [
        >     {"name": "ds1", "used_for_vms": True},
        >     {"name": "ds2", "used_for_vms": False}
        > ]
        """
        if not image_datastores:
            return []
        return [{"name": ds.name, "used_for_vms": ds.used_for_vms}
                for ds in image_datastores]

    def _read_json_file(self, filename):
        """
        Read a json file given the filename under the default config path
        """
        json_file = os.path.join(self._config_path, filename)
        if os.path.exists(json_file):
            with open(json_file) as fh:
                data = json.load(fh)
                return data
        return {}

    def _write_json_file(self, filename, state):
        """
        Write a json file given a filename under the default config path.
        Creates the file and directory if required.
        """
        json_file = os.path.join(self._config_path, filename)

        # Its ok to try and create this everytime as we aren't updating config
        # often.
        if not os.path.exists(json_file):
            common.file_util.mkdir_p(self._config_path)

        with atomic_write_file(json_file) as outfile:
            json.dump(state, outfile, sort_keys=True, indent=4,
                      separators=(',', ': '))

    def _load_config(self):
        """
        Load configuration from the default json config file
        """
        data = self._read_json_file(self._config_file_name)
        # Only set the options that are not already set (by the command line)
        for key in data:
            if hasattr(self, key):
                setattr(self, key, data[key])

    def _persist_config(self):
        """
        Persist the current agent configuration to file.
        Only persists options that are set and not set to a default value.
        """
        new_config = {}
        for key, value in self.__dict__.items():
            if not key.startswith("_"):
                new_config[key] = value
        self._write_json_file(self._config_file_name, new_config)
