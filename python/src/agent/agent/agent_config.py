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
from optparse import OptionParser

import common
from common.file_util import atomic_write_file
from common.lock import lock_with
from common.lock import locked


class InvalidConfig(Exception):
    """ Exception thrown when an agent config is not valid """
    pass


class AgentConfig(object):
    """
    Class encapsulating the agent configuration options and rules around
    updating certain configuration items.

    Options can be set in three ways in increasing order of precedence:
        a - Through the config file read at starup time
        b - Through the command line while starting the agent.
        c - Through a thrift interface when the agent is running, certain
        options cannot be update on running agents once set.

    Updating certain options require a reboot to take effect. The
    reboot_required attribute captures this (e.g. updates to host port number)
    """

    DEFAULT_CONFIG_PATH = "/etc/opt/vmware/photon/controller"
    DEFAULT_LOG_FILE = "/scratch/log/photon-controller-agent.log"
    DEFAULT_LOG_FILE_SIZE = 10 * 1024 * 1024
    DEFAULT_LOG_FILE_BACKUP_COUNT = 10
    DEFAULT_PORT_NUMBER = 8835
    DEFAULT_CONFIG_FILE = "config.json"
    DEFAULT_STATE_FILE = "state.json"
    DEFAULT_CPU_OVERCOMMIT = 16.0  # Follow Openstack's default value

    # Default config file options that are always persisted.
    DATASTORES = "datastores"
    HOSTNAME = "hostname"
    HOST_PORT = "port"
    MEMORY_OVERCOMMIT = "memory_overcommit"
    CPU_OVERCOMMIT = "cpu_overcommit"
    WAIT_TIMEOUT = "wait_timeout"
    MANAGEMENT_ONLY = "management_only"
    HOST_ID = "host_id"
    DEPLOYMENT_ID = "deployment_id"
    IMAGE_DATASTORES = "image_datastores"

    STATS_STORE_ENDPOINT = "stats_store_endpoint"
    STATS_STORE_PORT = "stats_store_port"
    STATS_ENABLED = "stats_enabled"
    STATS_HOST_TAGS = "stats_host_tags"

    PROVISION_ARGS = [HOST_PORT]
    BOOTSTRAP_ARGS = PROVISION_ARGS + [HOSTNAME, HOST_ID, DEPLOYMENT_ID]

    # List of attributes persisted to config.json by default

    def __init__(self, args=None):
        """
        Constructor
        args: The opt args
        """
        self.lock = threading.RLock()
        self._logger = logging.getLogger(__name__)
        # Read the command line options
        self._parse_options(args)
        # Read the options from the config file
        self._load_config()
        # Persist the config to the json file
        self._persist_config()
        # No reboot required yet.
        self._reboot_required = False

        # Option callbacks
        self._option_callbacks = {}
        self._callback_lock = threading.Lock()

    @property
    @locked
    def provision_ready(self):
        """
        Property indicating if we have all the configuration required for a
        host to be succesfully deployed within an instance.
        """
        return self._config_populated(self.PROVISION_ARGS)

    @property
    @locked
    def bootstrap_ready(self):
        """
        Property indicating if we have all the configuration required for the
        agent to be bootstrappable. i.e. expose a thrift endpoint for accepting
        the provisioning configuration
        """
        return self._config_populated(self.BOOTSTRAP_ARGS)

    @property
    @locked
    def options(self):
        """
        Returns the raw options list for the configured options.
        Preferred method of accessing options is through the specific property
        accessors.
        """
        return self._options

    @property
    @locked
    def reboot_required(self):
        """ Return true if the configuration update requires a reboot """
        return self._reboot_required

    def _check_and_set_attr(self, attr_name, value):
        """
        Sets a given option attribute.
        @return True if the attribute had to be updated False otherwise.
        """
        # Return None if the attribute is not set.
        old_value = getattr(self._options, attr_name, None)
        if old_value == value:
            return False
        setattr(self._options, attr_name, value)
        self._logger.debug("Updating config %s: %s -> %s" %
                           (str(attr_name), str(old_value), str(value)))
        return True

    @locked
    def update_config(self, provision_req):
        """
        Update the agent configuration using the provisioning request
        configuration.
        The update is considered to be a fully populated object, we don't
        support partial updates. A value of None for an optional property
        indicates that the value is being unset.
        @param provsion_req: The provision request
        """

        # Validate config.
        memory_overcommit = 1.0
        if provision_req.memory_overcommit:
            memory_overcommit = provision_req.memory_overcommit
            if (memory_overcommit < 1.0):
                # Doesn't make sense to not use all the memory in the system.
                raise InvalidConfig("Memory Overcommit less than 1.0")

        cpu_overcommit = self.DEFAULT_CPU_OVERCOMMIT
        if provision_req.cpu_overcommit:
            cpu_overcommit = provision_req.cpu_overcommit
            if (cpu_overcommit < 1.0):
                raise InvalidConfig("CPU Overcommit less than 1.0")

        reboot = False
        reboot |= self._check_and_set_attr(
            self.DATASTORES, provision_req.datastores)

        host = None
        port = self.DEFAULT_PORT_NUMBER
        if provision_req.address:
            host = provision_req.address.host
            port = provision_req.address.port

        reboot |= self._check_and_set_attr(
            self.HOSTNAME, host)
        reboot |= self._check_and_set_attr(
            self.HOST_PORT, port)

        if provision_req.stats_plugin_config is not None:
            reboot |= self._check_and_set_attr(
                self.STATS_STORE_ENDPOINT,
                provision_req.stats_plugin_config.stats_store_endpoint)
            reboot |= self._check_and_set_attr(
                self.STATS_STORE_PORT,
                provision_req.stats_plugin_config.stats_store_port)
            reboot |= self._check_and_set_attr(
                self.STATS_ENABLED,
                provision_req.stats_plugin_config.stats_enabled)
            reboot |= self._check_and_set_attr(
                self.STATS_HOST_TAGS,
                provision_req.stats_plugin_config.stats_host_tags)

        if self._check_and_set_attr(self.MEMORY_OVERCOMMIT, memory_overcommit):
            self._trigger_callbacks(self.MEMORY_OVERCOMMIT, memory_overcommit)
        if self._check_and_set_attr(self.CPU_OVERCOMMIT, cpu_overcommit):
            self._trigger_callbacks(self.CPU_OVERCOMMIT, cpu_overcommit)

        if provision_req.image_datastores:
            image_datastores = self._convert_image_datastores(
                provision_req.image_datastores)
            reboot |= self._check_and_set_attr(
                self.IMAGE_DATASTORES, image_datastores)

        if provision_req.management_only:
            reboot |= self._check_and_set_attr(
                self.MANAGEMENT_ONLY,
                provision_req.management_only)

        reboot |= self._check_and_set_attr(
            self.HOST_ID, provision_req.host_id)

        reboot |= self._check_and_set_attr(
            self.DEPLOYMENT_ID, provision_req.deployment_id)

        # Persist the updates to the config file.
        self._persist_config()

        # For simplicity mark for reboot when any provision configuration
        # changes.
        if reboot:
            # When we have all the parameters for the agent to be provisioned
            # into esx-cloud notify for reboot.
            self._logger.info("Agent configuration updated reboot required")
            if not self.bootstrap_ready:
                self._logger.warning("Agent not fully configured %s" %
                                     str(self._options))
            self._reboot_required = True

    @property
    @locked
    def datastores(self):
        datastores = getattr(self._options, self.DATASTORES)
        if datastores is None:
            return []
        return datastores

    @property
    @locked
    def hostname(self):
        if hasattr(self._options, self.HOSTNAME):
            return getattr(self._options, self.HOSTNAME)
        return "localhost"

    @property
    @locked
    def host_port(self):
        return getattr(self._options, self.HOST_PORT)

    @property
    @locked
    def stats_store_endpoint(self):
        return getattr(self._options, self.STATS_STORE_ENDPOINT)

    @property
    @locked
    def stats_store_port(self):
        return getattr(self._options, self.STATS_STORE_PORT)

    @property
    @locked
    def stats_host_tags(self):
        return getattr(self._options, self.STATS_HOST_TAGS)

    @property
    @locked
    def stats_enabled(self):
        return getattr(self._options, self.STATS_ENABLED)

    @property
    @locked
    def workers(self):
        return self._options.workers

    @property
    @locked
    def log_level(self):
        return self._options.log_level

    @property
    @locked
    def no_syslog(self):
        return self._options.no_syslog

    @property
    @locked
    def logging_file(self):
        return self._options.logging_file

    @property
    @locked
    def logging_file_size(self):
        return self._options.logging_file_size

    @property
    @locked
    def logging_file_backup_count(self):
        return self._options.logging_file_backup_count

    @property
    @locked
    def console_log(self):
        return self._options.console_log

    @property
    @locked
    def bootstrap_poll_frequency(self):
        return self._options.bootstrap_poll_frequency

    @property
    @locked
    def memory_overcommit(self):
        return getattr(self._options, self.MEMORY_OVERCOMMIT)

    @property
    @locked
    def cpu_overcommit(self):
        return getattr(self._options, self.CPU_OVERCOMMIT)

    @property
    @locked
    def image_datastores(self):
        image_datastores = getattr(self._options, self.IMAGE_DATASTORES)
        if image_datastores is None:
            return []
        return image_datastores

    @property
    @locked
    def wait_timeout(self):
        return getattr(self._options, self.WAIT_TIMEOUT)

    @property
    @locked
    def host_service_threads(self):
        return self._options.host_service_threads

    @property
    @locked
    def control_service_threads(self):
        return self._options.control_service_threads

    @property
    @locked
    def heartbeat_interval_sec(self):
        return self._options.heartbeat_interval_sec

    @property
    @locked
    def heartbeat_timeout_factor(self):
        return self._options.heartbeat_timeout_factor

    @property
    @locked
    def thrift_timeout_sec(self):
        return self._options.thrift_timeout_sec

    @property
    @locked
    def utilization_transfer_ratio(self):
        return self._options.utilization_transfer_ratio

    @property
    @locked
    def management_only(self):
        return self._options.management_only

    @property
    @locked
    def host_id(self):
        return getattr(self._options, self.HOST_ID)

    @property
    @locked
    def deployment_id(self):
        return getattr(self._options, self.DEPLOYMENT_ID)

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

    def _config_populated(self, prop_list):
        """
        Utility method checking if options in prop_list are all populated
        @return True if the options are all populated
        @return False if the property doesn't exist or is None
        """
        for prop in prop_list:
            # Either we don't have the attr.
            if not hasattr(self._options, prop):
                return False
            # Or it is not set
            if not getattr(self._options, prop):
                return False
        return True

    def _parse_options(self, args=None):
        """
        Set of command line options
        """
        parser = OptionParser()
        parser.add_option("--hostname", dest=self.HOSTNAME, type="string",
                          default=None)
        parser.add_option("--port", dest=self.HOST_PORT, type="int",
                          default=self.DEFAULT_PORT_NUMBER,
                          help="The port to run the agent services on.")
        parser.add_option("--logging-level", dest="log_level", type="string",
                          default="info",
                          help="The string log level to log at.")
        parser.add_option("--logging-file", dest="logging_file", type="string",
                          default=self.DEFAULT_LOG_FILE,
                          help="Path to agent log file.")
        parser.add_option("--logging-file-size", dest="logging_file_size",
                          type="int", default=self.DEFAULT_LOG_FILE_SIZE,
                          help="Max log file size in bytes.")
        parser.add_option("--logging-file-backup-count",
                          dest="logging_file_backup_count", type="int",
                          default=self.DEFAULT_LOG_FILE_BACKUP_COUNT,
                          help="Number of rotated log files to keep.")
        parser.add_option("--no-syslog", dest="no_syslog",
                          action="store_true",
                          default=False, help="Disable syslog forwarding.")
        parser.add_option("--console-log", dest="console_log",
                          action="store_true",
                          default=False, help="Show the logs in the console.")
        parser.add_option("--config-path", dest="config_path", type="string",
                          default=self.DEFAULT_CONFIG_PATH)
        parser.add_option("--datastores", dest=self.DATASTORES, type="string",
                          default=None,
                          help="Comma separated list of datastore ids")
        parser.add_option("--workers", dest="workers", type="int", default=32)
        parser.add_option("--bootstrap-poll-frequency",
                          dest="bootstrap_poll_frequency",
                          type="int", default=5)
        parser.add_option("--memory-overcommit", dest=self.MEMORY_OVERCOMMIT,
                          type="float", default=1.0,
                          help="The memory overcommit for this host")
        parser.add_option("--cpu-overcommit", dest=self.CPU_OVERCOMMIT,
                          type="float", default=self.DEFAULT_CPU_OVERCOMMIT,
                          help="The CPU overcommit for this host")
        parser.add_option("--wait-timeout", dest=self.WAIT_TIMEOUT,
                          type="int", default=10,
                          help="Timeout for host notification before sending" +
                               "another request to host. Thus this also " +
                               "works as session keepalive interval.")
        # Note that we don't support this command-line option. It's defined
        # here so that self._options get initialized with the image_datastores
        # attribute. Eventually we should get rid of all the command-line
        # options and always boot from config.json.
        parser.add_option("--image-datastores", dest=self.IMAGE_DATASTORES,
                          type="string",
                          help="List of image datastores")

        # Thread pool configuration for services.
        parser.add_option("--host-service-threads",
                          dest="host_service_threads", type="int",
                          default=20,  # Same as hostd vmops threads.
                          help="The number of threads for the host thrift " +
                               "service")

        parser.add_option("--control-service-threads",
                          dest="control_service_threads", type="int",
                          default=1,  # Doesn't do much currently
                          help="The number of threads for the control " +
                               "thrift service")

        # Health checker related configuration
        parser.add_option("--heartbeat-interval-sec",
                          dest="heartbeat_interval_sec", type="int",
                          default=10, help="Heartbeat interval in seconds")
        # Health checker considers a host down if the host does not respond to
        # ping requests for heartbeat_interval_sec * heartbeat_timeout_factor
        # seconds.
        parser.add_option("--heartbeat-timeout-factor",
                          dest="heartbeat_timeout_factor", type="int",
                          default=6, help="Heartbeat timeout factor")

        # Thrift configuration
        parser.add_option("--thrift-timeout-sec",
                          dest="thrift_timeout_sec", type="int",
                          default=3, help="Thrift client timeout in seconds")

        parser.add_option("--utilization-transfer-ratio",
                          dest="utilization_transfer_ratio", type="float",
                          default=9, help="Utilization to transfer ratio "
                                          "when calculating place score")

        parser.add_option("--management-only", dest="management_only",
                          action="store_true",
                          default=False, help="Management only host")

        parser.add_option("--host-id", dest=self.HOST_ID,
                          type="string", default=None,
                          help="ID of this host")

        parser.add_option("--stats-enabled",
                          dest=self.STATS_ENABLED,
                          action="store_true",
                          default=False,
                          help="Flag to enable stats")

        parser.add_option("--stats-store-endpoint",
                          dest=self.STATS_STORE_ENDPOINT,
                          type="string",
                          default=None,
                          help="Uri or IP Address of the stats "
                               "store server (i.e. graphite)")

        parser.add_option("--stats-store-port",
                          dest=self.STATS_STORE_PORT,
                          type="int",
                          default=0,
                          help="Port number of the stats "
                               "store server (i.e. graphite)")

        parser.add_option("--stats-host-tags",
                          dest=self.STATS_HOST_TAGS,
                          type="string",
                          default=None,
                          help="Host tags for tagging metrics in stats")

        parser.add_option("--deployment-id", dest=self.DEPLOYMENT_ID,
                          type="string", default=None,
                          help="ID of the deployment")
        self._default_options = parser.defaults

        self._options, _ = parser.parse_args(args=args)

        # config file already stores it as a list
        self._sanitize_config()

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

    def _sanitize_config(self):
        """
        Sanitize config to return an array if params are specified as a list
        of strings.
        """
        setattr(self._options, self.DATASTORES,
                self._parse_list(getattr(self._options, self.DATASTORES)))

    def _parse_list(self, string_option):
        """
        Utility method to parse a comma separated string and return the
        corresponding list.
        @returns the corresponding list representation of the string or returns
        the same object if it is not a string (i.e. already a list)
        """
        try:
            return [option.strip() for option in string_option.split(",")]
        except AttributeError:
            # Already a list
            pass
        return string_option

    def _read_json_file(self, filename):
        """
        Read a json file given the filename under the default config path
        """
        json_file = os.path.join(self._options.config_path, filename)
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
        json_file = os.path.join(self._options.config_path, filename)

        # Its ok to try and create this everytime as we aren't updating config
        # often.
        if not os.path.exists(json_file):
            common.file_util.mkdir_p(self._options.config_path)

        with atomic_write_file(json_file) as outfile:
            json.dump(state, outfile, sort_keys=True, indent=4,
                      separators=(',', ': '))

    def _is_unset(self, key, value):
        """
        Check if an option is already set or not.
        @return True if the option is not set or is set to the default value.
        @return False if the option is set to a non default value.
        """
        if not hasattr(self._options, key):
            return False
        # A bit of a hack to override default values
        if key in self._default_options:
            if self._default_options[key] == getattr(self._options, key):
                return True
        if not getattr(self._options, key):
            return True
        return False

    def _load_config(self):
        """
        Load configuration from the default json config file
        Only overrides the options that are not already set to a non default
        value. i.e. command line args take precedence.
        """
        data = self._read_json_file(self.DEFAULT_CONFIG_FILE)
        # Only set the options that are not already set (by the command line)
        for key in data:
            if self._is_unset(key, data[key]):
                setattr(self._options, key, data[key])

        # Convert any "," separated lists to real python lists
        self._sanitize_config()

    def _persist_config(self):
        """
        Persist the current agent configuration to file.
        Only persists options that are set and not set to a default value.
        """
        new_config = {}
        for key, value in self._options.__dict__.items():
            if not self._is_unset(key, value):
                new_config[key] = value
        self._write_json_file(self.DEFAULT_CONFIG_FILE, new_config)
