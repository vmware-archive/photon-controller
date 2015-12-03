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

import json
import logging
import os
import threading

import common

from common.file_util import atomic_write_file
from common.lock import locked
from common.lock import lock_with
from gen.common.ttypes import ServerAddress


class InvalidConfig(Exception):
    """ Exception thrown when an agent config is not valid """
    pass


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
    def __init__(self):
        """
        Constructor
        """
        self._logger = logging.getLogger(__name__)
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
        self.port = 8835
        self.log_level = "info"
        self.logging_file = "/scratch/log/photon-controller-agent.log"
        self.logging_file_size = 10 * 1024 * 1024
        self.logging_file_backup_count = 10
        self.no_syslog = False
        self.console_log = False
        self.hypervisor = "esx"
        self.availability_zone = None
        self.config_path = "/etc/opt/vmware/photon/controller"
        self.datastores = []
        self.vm_network = []
        self.workers = 32
        self.bootstrap_poll_frequency = 5
        self.chairman = None
        self.memory_overcommit = 1.0
        self.cpu_overcommit = 16.0  # Follow Openstack's default value
        self.wait_timeout = 10
        self.image_datastores = []
        self.host_service_threads = 20
        self.scheduler_service_threads = 32
        self.control_service_threads = 1
        self.heartbeat_interval_sec = 10
        self.heartbeat_timeout_factor = 6
        self.thrift_timeout_sec = 3
        self.utilization_transfer_ratio = 9
        self.management_only = False
        self.in_uwsim = False
        self.host_id = None

    @property
    def bootstrap_ready(self):
        """
        Property indicating if we have all the configuration required for the
        agent to be bootstrappable. i.e. expose a thrift endpoint for accepting
        the provisioning configuration
        """
        return (self.hostname and self.port and self.availability_zone and
                self.chairman and self.host_id)

    @property
    def reboot_required(self):
        """ Return true if the configuration update requires a reboot """
        return self._reboot_required

    def _check_and_set_attr(self, attr_name, value):
        """
        Sets a given option attribute.
        @return True if the attribute had to be updated False otherwise.
        """
        # Return None if the attribute is not set.
        if getattr(self, attr_name, None) == value:
            return False
        setattr(self, attr_name, value)
        self._logger.debug("Updating config %s %s" % (attr_name, value))
        return True

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
        if self.vm_network != provision_req.networks:
            self.vm_network = provision_req.networks
            reboot = True
        if self.hostname != provision_req.address.host:
            self.hostname = provision_req.address.host
            reboot = True
        if self.port != provision_req.address.port:
            self.port = provision_req.address.port
            reboot = True

        chairman_list = \
            self._parse_chairman_server_address(provision_req.chairman_server)
        if self.chairman != chairman_list:
            self.chairman = chairman_list
            self._trigger_callbacks(self._fields.chairman, self.chairman_list)

        if provision_req.memory_overcommit < 1.0:
            # Doesn't make sense to not use all the memory in the system.
            raise InvalidConfig("Memory Overcommit less than 1.0")
        if self.memory_overcommit != memory_overcommit:
            self.memory_overcommit = memory_overcommit
            self._trigger_callbacks(self.memory_overcommit, memory_overcommit)

        cpu_overcommit = self.cpu_overcommit
        if provision_req.cpu_overcommit:
            cpu_overcommit = provision_req.cpu_overcommit
            if cpu_overcommit < 1.0:
                raise InvalidConfig("CPU Overcommit less than 1.0")
        if self.cpu_overcommit != cpu_overcommit:
            self.cpu_overcommit = cpu_overcommit
            self._trigger_callbacks(self.cpu_overcommit, cpu_overcommit)

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

        # Persist the updates to the config file.
        self._persist_config()

        if reboot:
            self._logger.info("Agent configuration updated reboot required")
            if not self.bootstrap_ready:
                self._logger.warning("Agent not fully configured %s" %
                                     str(self.__dict__))
            self._reboot_required = True

    @property
    @locked
    def chairman_list(self):
        chairman_str = self.chairman
        return self._parse_chairman_list(chairman_str)

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
            except ValueError:
                self._logger.warning(
                    "Failed to parse server %s, Invalid delemiter" % server)
                pass
            except AttributeError:
                self._logger.warning("Failed to parse chairman server %s" %
                                     server)
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
        return [{"name": ds.name, "used_for_vms": ds.used_for_vms}
                for ds in image_datastores]

    def _read_json_file(self, filename):
        """
        Read a json file given the filename under the default config path
        """
        json_file = os.path.join(self.config_path, filename)
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
        json_file = os.path.join(self.config_path, filename)

        # Its ok to try and create this everytime as we aren't updating config
        # often.
        if not os.path.exists(json_file):
            common.file_util.mkdir_p(self.config_path)

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
            setattr(self, key, data[key])

    def _persist_config(self):
        """
        Persist the current agent configuration to file.
        Only persists options that are set and not set to a default value.
        """
        new_config = {}
        for key, value in self.__dict__.items():
            new_config[key] = value
        self._write_json_file(self._config_file_name, new_config)
