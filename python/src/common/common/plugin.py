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

import abc
import logging

from pkg_resources import iter_entry_points


# All loaded plugins
import common
from common.service_name import ServiceName

loaded_plugins = []

# Get logger
logger = logging.getLogger(__name__)


class Plugin(object):
    """ Plugin represents a plugin that is dynamically loadable in photon
    controller agent.
    """

    __metaclass__ = abc.ABCMeta

    def __init__(self, name, is_core=True):
        """
        :param name: plugin name
        :param is_core: boolean to indicate if this is the core plugins or not. Core plugin should be able to start
        """
        self.name = name
        self.is_core = is_core
        self._thrift_services = set()

    @abc.abstractmethod
    def init(self):
        """ The method to initialize a plugin. Normally it reads the
        configuration and initialize the thrift handlers and backend
        workers. It also registers services (common object) which could be
        used by other plugins. The plugin won't start to work until start is
        called.
        """
        pass

    def start(self):
        """ This method to make a plugin start to function. Normally it
        only starts backend workers. If there is extra things that need to
        be done, overwrite this method in subclass.
        """
        pass

    @property
    def thrift_services(self):
        return self._thrift_services

    def add_thrift_service(self, service):
        assert isinstance(service, ThriftService)
        self._thrift_services.add(service)

    def remove_thrift_service(self, service):
        assert isinstance(service, ThriftService)
        self._thrift_services.remove(service)

    def agent_config(self):
        """ Get agent configuration. Normally called in init. The agent
        configuration has to be registered in common services before loading
        plugins.

        :return AgentConfig, agent configuration
        """
        return common.services.get(ServiceName.AGENT_CONFIG)


class ThriftService(object):

    def __init__(self, name, service, handler, num_threads, max_entries=0):
        """
        :param name: plugin name
        :param service: thrift service class
        :param handler: thrift handler
        :param num_threads: number of dedicated worker threads
        :param max_entries: max number of queued entries. 0 as unbounded.
        """
        self.name = name
        self.service = service
        self.handler = handler
        self.num_threads = num_threads
        self.max_entries = max_entries

    def __repr__(self):
        return "<name: %s, service: %s, handler: %s, num_threads: %d," \
               "max_entries: %d>" % (self.name, self.service, self.handler,
                                     self.num_threads, self.max_entries)


def load_plugins():
    # Load plugins
    plugins = []
    for entries in iter_entry_points(group="photon.controller.plugin"):
        plugins.append(entries.load())

    logger.info("Plugins found: %s " % plugins)
    # Init all plugins
    for plugin in plugins:
        if plugin.init:
            try:
                plugin.init()
                logger.info("Plugin %s initialized" % plugin.name)
            except:
                logger.exception("Init of plugin %s failed" % plugin.name)
                if plugin.is_core:
                    raise
    # Start all plugins
    for plugin in plugins:
        if plugin.start:
            try:
                plugin.start()
                logger.info("Plugin %s started" % plugin.name)
            except:
                logger.exception("Start plugin %s failed" % plugin.name)
                if plugin.is_core:
                    raise

    global loaded_plugins
    loaded_plugins = plugins
    return plugins


def thrift_services():
    """ Get all the thrift services in loaded plugins
    :return: set, all thrift services in loaded plugins
    """
    services = set()
    for plugin in loaded_plugins:
        services = services.union(plugin.thrift_services)
    return services
