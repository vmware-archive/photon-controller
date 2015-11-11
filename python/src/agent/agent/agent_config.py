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

from optparse import OptionParser
from common.file_util import atomic_write_file
from common.lock import locked
from common.lock import lock_with
from gen.common.ttypes import ServerAddress


class InvalidState(Exception):
    """ Exception thrown when an agent config cannot be updated """
    pass


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
    VM_NETWORK = "vm_network"
    AVAILABILITY_ZONE = "availability_zone"
    HOSTNAME = "hostname"
    HOST_PORT = "port"
    CHAIRMAN = "chairman"
    MEMORY_OVERCOMMIT = "memory_overcommit"
    CPU_OVERCOMMIT = "cpu_overcommit"
    IMAGE_DATASTORE = "image_datastore"
    IMAGE_DATASTORE_FOR_VMS = "image_datastore_for_vms"
    WAIT_TIMEOUT = "wait_timeout"
    MANAGEMENT_ONLY = "management_only"
    HOST_ID = "host_id"

    PROVISION_ARGS = [HOST_PORT]
    BOOTSTRAP_ARGS = PROVISION_ARGS + [AVAILABILITY_ZONE, HOSTNAME, CHAIRMAN,
                                       HOST_ID]

    # List of attributes persisted to config.json by default

    def __init__(self, hostname_hint=None, args=None):
        """
        Constructor
        hostname_hint: A hint for a hostname determined by reading the esx host
        configuration.
        args: The opt args
        """
        self.lock = threading.RLock()
        self._hostname_hint = hostname_hint
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
        if (getattr(self._options, attr_name) == value):
            return False
        setattr(self._options, attr_name, value)
        self._logger.debug("Updating config %s %s" %
                           (str(attr_name), str(value)))
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

        # Check if image_datastore_info is valid
        if provision_req.image_datastore_info and provision_req.datastores:
            if not self._check_image_datastore(
                    provision_req.image_datastore_info.name,
                    provision_req.datastores):
                raise InvalidConfig("image_datastore_info is not valid")

        reboot = False
        reboot |= self._check_and_set_attr(
            self.AVAILABILITY_ZONE, provision_req.availability_zone)
        reboot |= self._check_and_set_attr(
            self.DATASTORES, provision_req.datastores)
        reboot |= self._check_and_set_attr(
            self.VM_NETWORK, provision_req.networks)

        host = None
        port = self.DEFAULT_PORT_NUMBER
        if provision_req.address:
            host = provision_req.address.host
            port = provision_req.address.port

        reboot |= self._check_and_set_attr(
            self.HOSTNAME, host)
        reboot |= self._check_and_set_attr(
            self.HOST_PORT, port)

        chairman_str = \
            self._parse_chairman_server_address(provision_req.chairman_server)
        if self._check_and_set_attr(self.CHAIRMAN, chairman_str):
            self._trigger_callbacks(self.CHAIRMAN, self.chairman_list)

        reboot |= self._check_and_set_attr(
            self.MEMORY_OVERCOMMIT, memory_overcommit)
        reboot |= self._check_and_set_attr(
            self.CPU_OVERCOMMIT, cpu_overcommit)

        image_datastore_for_vms = False
        if provision_req.image_datastore_info:
            reboot |= self._check_and_set_attr(
                self.IMAGE_DATASTORE, provision_req.image_datastore_info.name)
            image_datastore_for_vms = \
                provision_req.image_datastore_info.used_for_vms

        reboot |= self._check_and_set_attr(
            self.IMAGE_DATASTORE_FOR_VMS, image_datastore_for_vms)

        if (provision_req.environment):
            self._logger.info(provision_req.environment)
            for k in provision_req.environment:
                reboot |= self._check_and_set_attr(
                    k, provision_req.environment[k])

        if provision_req.management_only:
            reboot |= self._check_and_set_attr(
                self.MANAGEMENT_ONLY,
                provision_req.management_only)

        reboot |= self._check_and_set_attr(
            self.HOST_ID, provision_req.host_id)

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

    def set_hostname_hint(self, hostname):
        """ Set the hostname hint as read from esx. """
        self._hostname_hint = hostname

    @property
    @locked
    def availability_zone(self):
        return getattr(self._options, self.AVAILABILITY_ZONE)

    @property
    @locked
    def datastores(self):
        datastores = getattr(self._options, self.DATASTORES)
        if datastores is None:
            return []
        return datastores

    @property
    @locked
    def networks(self):
        networks = getattr(self._options, self.VM_NETWORK)
        if networks is None:
            return []
        return networks

    @property
    @locked
    def hostname(self):
        if hasattr(self._options, self.HOSTNAME):
            return getattr(self._options, self.HOSTNAME)
        return self._hostname_hint

    @property
    @locked
    def host_port(self):
        return getattr(self._options, self.HOST_PORT)

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
    def hypervisor(self):
        return self._options.hypervisor.lower()

    @property
    @locked
    def multi_agent_id(self):
        return self._options.multi_agent_id

    @property
    @locked
    def bootstrap_poll_frequency(self):
        return self._options.bootstrap_poll_frequency

    @property
    @locked
    def chairman_list(self):
        chairman_str = getattr(self._options, self.CHAIRMAN)
        return self._parse_chairman_list(chairman_str)

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
    def image_datastore(self):
        return getattr(self._options, self.IMAGE_DATASTORE)

    @property
    @locked
    def image_datastore_for_vms(self):
        return getattr(self._options, self.IMAGE_DATASTORE_FOR_VMS)

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
    def scheduler_service_threads(self):
        return self._options.scheduler_service_threads

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
    def refcount_lock_retries(self):
        return self._options.refcount_lock_retries

    @property
    @locked
    def refcount_max_backoff_ms(self):
        return self._options.refcount_max_backoff_ms

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
    def in_uwsim(self):
        return self._options.in_uwsim

    @property
    @locked
    def host_id(self):
        return getattr(self._options, self.HOST_ID)

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
        parser.add_option("--hypervisor", dest="hypervisor", type="string",
                          default="esx",
                          help="The hypervisor that we are running on.")
        parser.add_option("--availability-zone", dest=self.AVAILABILITY_ZONE,
                          type="string", default=None)
        parser.add_option("--config-path", dest="config_path", type="string",
                          default=self.DEFAULT_CONFIG_PATH)
        parser.add_option("--datastores", dest=self.DATASTORES, type="string",
                          default=None,
                          help="Comma separated list of datastore ids")
        parser.add_option("--vm-network", dest=self.VM_NETWORK, type="string",
                          default=None,
                          help="Comma separated list of vm networks")
        parser.add_option("--workers", dest="workers", type="int", default=32)
        parser.add_option("--multi-agent-id", dest="multi_agent_id",
                          action="store_true",
                          default=None, help="Multi agent run.")
        parser.add_option("--bootstrap-poll-frequency",
                          dest="bootstrap_poll_frequency",
                          type="int", default=5)
        parser.add_option("--chairman", dest=self.CHAIRMAN, type="string",
                          help="comma separated list of chairman ip:port")
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
        parser.add_option("--image-datastore", dest=self.IMAGE_DATASTORE,
                          type="string",
                          help="The datastore used only for images")
        parser.add_option("--image-datastore-for-vms",
                          dest=self.IMAGE_DATASTORE_FOR_VMS,
                          action="store_true", default=False,
                          help="The image datastore can be used for placing " +
                               "vms")

        # Thread pool configuration for services.
        parser.add_option("--host-service-threads",
                          dest="host_service_threads", type="int",
                          default=20,  # Same as hostd vmops threads.
                          help="The number of threads for the host thrift " +
                               "service")
        parser.add_option("--scheduler-service-threads",
                          dest="scheduler_service_threads", type="int",
                          default=32,  # Leaf scheduler span.
                          help="The number of threads for the scheduler " +
                               "thrift service")
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

        # Reference counting
        refcount_help = "Maximum number of retries to acquire a lock for " + \
                        "refcount file before giving up."

        parser.add_option("--refcount-lock-retries",
                          dest="refcount_lock_retries", type="int",
                          default=1000, help=refcount_help)

        backoff_help = "Maximum random backoff interval in milliseconds. " + \
                       "Each backoff interval is chosen randomly with a " + \
                       "uniform distribution between 0 and this max value. " \
                       "The default is 40 ms, which means the average " + \
                       "backoff duration is 20 ms. With the default " + \
                       "max retry count of 1000, agent tries to acquire a " + \
                       "lock for 20 seconds on average. These values are" + \
                       "based on the VMFS lock timeout of 16 seconds."

        parser.add_option("--refcount-max-backoff-ms",
                          dest="refcount_max_backoff_ms", type="int",
                          default=40, help=backoff_help)

        parser.add_option("--utilization-transfer-ratio",
                          dest="utilization_transfer_ratio", type="float",
                          default=9, help="Utilization to transfer ratio "
                                          "when calculating place score")

        parser.add_option("--management-only", dest="management_only",
                          action="store_true",
                          default=False, help="Management only host")

        parser.add_option("--in-uwsim", dest="in_uwsim",
                          action="store_true",
                          default=False, help="Running in UWSim enviroinment")

        parser.add_option("--host-id", dest=self.HOST_ID,
                          type="string", default=None,
                          help="ID of this host")

        self._default_options = parser.defaults

        self._options, _ = parser.parse_args(args=args)

        # config file already stores it as a list
        self._sanitize_config()

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
                self._logger.warning("Failed to parse chairman server %" %
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

    def _sanitize_config(self):
        """
        Sanitize config to return an array if params are specified as a list
        of strings.
        """
        setattr(self._options, self.DATASTORES,
                self._parse_list(getattr(self._options, self.DATASTORES)))
        setattr(self._options, self.VM_NETWORK,
                self._parse_list(getattr(self._options, self.VM_NETWORK)))
        setattr(self._options, self.CHAIRMAN,
                self._parse_list(getattr(self._options, self.CHAIRMAN)))

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

    def _check_image_datastore(self, image_ds, datastores):
        """ Check that the image datastore is a valid one """
        # In the current version we don't actually call out to esx to check if
        # the ds exists, we just check if it is in one of datastores specified
        # by the user through the DC_MAP
        if image_ds is None:
            return True
        if datastores and (image_ds in datastores):
            return True
        self._logger.warning("Image ds %s not in %s " % (image_ds, datastores))
        return False
