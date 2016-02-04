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

from kazoo.exceptions import NoNodeError

from agent.tests.common_helper_functions import create_chairman_client
from agent.tests.common_helper_functions import create_root_client
from common.constants import ROOT_SCHEDULER_ID
from common.constants import ROOT_SCHEDULER_SERVICE
from common.constants import HOSTS_PREFIX
from common.constants import MISSING_PREFIX
from common.constants import ROLES_PREFIX
from common.photon_thrift.address import parse_address
from common.photon_thrift.direct_client import DirectClient
from gen.agent import AgentControl
from gen.host.ttypes import HostConfig
from gen.roles.ttypes import GetSchedulersRequest
from gen.roles.ttypes import GetSchedulersResultCode
from gen.roles.ttypes import Roles
from thrift.TSerialization import deserialize
from thrift.transport import TTransport
from thrift.transport import TSocket


ROOT_SCHEDULER_TYPE = "Root"
LEAF_SCHEDULER_TYPE = "Leaf"
STATUS_ONLINE = "ONLINE"
STATUS_OFFLINE = "OFFLINE"


log = logging.getLogger(__name__)


def indent(_str, times=1):
    return "        " * times + _str


def check_connectivity(address, port):
    try:
        socket = TSocket.TSocket(address, port)
        transport = TTransport.TFramedTransport(socket)
        transport.open()
        transport.close()
        return True
    except TTransport.TTransportException:
        return False


class Scheduler(object):
    def __init__(self, _id, _type, owner=None, parent=None):
        self.id = _id
        self.type = _type
        self.owner = owner
        self.children = {}
        self.parent = parent

    def add_child(self, child):
        child.parent = self
        self.children[child.id] = child

    def update_status(self):
        self.owner.update_status()

        for child in self.children.values():
            child.update_status()

    def __eq__(self, dest_sch):
        if self is dest_sch:
            return True

        if not isinstance(dest_sch, Scheduler):
            return False

        equals = (self.id == dest_sch.id and
                  self.type == dest_sch.type and
                  len(self.children) == len(dest_sch.children))
        # check if the owners are the same
        if self.owner and dest_sch.owner:
            equals = equals and (self.owner == dest_sch.owner)
        elif not self.owner and not dest_sch.owner:
            pass
        else:
            return False

        # check if the parents have the same id
        if self.parent and dest_sch.parent:
            equals = (equals and
                      self.parent.id == dest_sch.parent.id)
        elif not self.parent and not dest_sch.parent:
            pass
        else:
            return False

        # check if they have the same children
        for child in self.children.values():
            other_child = dest_sch.children.get(child.id, None)
            if not other_child:
                return False
            equals = equals and (child == other_child)

        return equals

    def __str__(self):
        return self.to_string()

    def to_string(self, base=0):
        parent_id = ""
        if self.parent:
            parent_id = self.parent.id

        sch_str = ("Scheduler(owner=%s, id=%s, type=%s, parent=%s, "
                   "status=%s)" %
                   (self.owner.id, self.id, self.type, parent_id,
                    self.owner.status))

        lst = [indent(sch_str, base)]
        for child in self.children.values():
            child_str = child.to_string(base+1)
            lst.append(child_str)

        return "\n".join(lst) + "\n"


class Host(object):
    def __init__(self, _id, address, port, parent=None):
        self.id = _id
        self.address = address
        self.port = port
        self.parent = parent
        self.status = None

    def __eq__(self, dest_host):
        if not isinstance(dest_host, Host):
            return False

        if self.parent and dest_host.parent:
            parents = self.parent.id == dest_host.parent.id
        elif not self.parent and not dest_host.parent:
            parents = True
        else:
            return False

        return (self.id == dest_host.id and
                self.address == dest_host.address and
                self.port == dest_host.port and
                parents)

    def update_status(self):
        if self.address is None or self.port is None:
            self.status = None
        elif check_connectivity(self.address, self.port):
            self.status = STATUS_ONLINE
        else:
            self.status = STATUS_OFFLINE

    def to_string(self, base=0):
        parent_id = ""
        if self.parent:
            parent_id = self.parent.id
        _str = ("Host(id=%s, address=%s, port=%s, parent=%s, status=%s)" %
                (self.id, self.address, self.port, parent_id, self.status))
        return indent(_str, base)


def get_service_leader(client, serice_name):
    """
    Given a service name this function will return a the tuple (address, port)
    of the leader, or None if there isnt one
    """
    try:
        if client.exists(serice_name):
            master = client.get_children(serice_name)
            if len(master) != 1:
                return

            (val, _) = client.get("%s/%s" % (serice_name, master[0]))
            return parse_address(val)
    except Exception, e:
        log.exception(e)


def get_leaf_scheduler(address, port):
    """
    This function will retrive a leaf scheduler from an agent, if the
    agent is a leaf scheduler and is online. Otherwise, it will return
    None if the agent is not a leaf scheduler or isn't available.
    """
    try:
        leaf_client = DirectClient("AgentControl", AgentControl.Client,
                                   address, port)
        leaf_client.connect()
        req = GetSchedulersRequest()
        resp = leaf_client.get_schedulers(req)
        if resp.result != GetSchedulersResultCode.OK or not resp.schedulers:
            # Doesn't have a role, not a leaf scheduler
            return

        role = resp.schedulers[0].role
        leaf_sch = Scheduler(role.id, LEAF_SCHEDULER_TYPE)
        hosts = role.host_children
        for host in hosts:
            host = Host(host.id, host.address, host.port, leaf_sch)
            if address == host.address and port == host.port:
                leaf_sch.owner = host
            leaf_sch.add_child(host)
        return leaf_sch
    except Exception, e:
        log.exception(e)


def get_root_scheduler(address, port):
    """
    This function will retrive a root scheduler from the root-scheduler
    service, if it is online. Otherwise it will return None if it isn't
    available, or if an error happens.
    """
    try:
        _, root_client = create_root_client(port, address)
        resp = root_client.get_schedulers()
        if resp.result != GetSchedulersResultCode.OK:
            return
        root_role = resp.schedulers[0].role
        root_sch_host = Host(ROOT_SCHEDULER_ID, address, port)
        root_sch = Scheduler(root_role.id, ROOT_SCHEDULER_TYPE, root_sch_host)

        scheduler_children = root_role.scheduler_children or []
        for child in scheduler_children:
            owner_host = Host(child.owner_host, child.address, child.port)
            leaf = Scheduler(child.id, LEAF_SCHEDULER_TYPE, owner_host,
                             root_sch)
            owner_host.parent = leaf
            root_sch.add_child(leaf)
        return root_sch
    except Exception, e:
        log.exception(e)


def get_hierarchy_from_zk(zk_client):
    """
    This function will read /hosts and /roles and try to construct a hierarchy.
    """
    try:
        res = get_service_leader(zk_client, ROOT_SCHEDULER_SERVICE)

        if not res:
            log.debug("Couldn't find a root scheduler leader!")
            res = (None, None)
        if not zk_client.exists(ROLES_PREFIX):
            log.error("%s doesn't exist" % (ROLES_PREFIX,))
            return

        root_host = Host(ROOT_SCHEDULER_ID, res[0], res[1])
        root_sch = Scheduler(ROOT_SCHEDULER_ID, ROOT_SCHEDULER_TYPE, root_host)

        if not zk_client.exists(ROLES_PREFIX):
            log.debug("%s doesn't exist" % (ROLES_PREFIX,))
            return

        # TODO(Maithem): cross reference with /hosts
        scheduler_hosts = zk_client.get_children(ROLES_PREFIX)

        for sch_host in scheduler_hosts:
            try:
                path = "%s/%s" % (ROLES_PREFIX, sch_host)
                (value, stat) = zk_client.get(path)
                role = Roles()
                deserialize(role, value)

                if (not role or not role.schedulers or
                        len(role.schedulers) != 1):
                    log.debug("Incorrect role for scheduler host %s" %
                              (sch_host,))
                    continue

                leaf_role = role.schedulers[0]
                leaf = Scheduler(leaf_role.id, LEAF_SCHEDULER_TYPE,
                                 None, root_sch)

                for childHost in (leaf_role.host_children or []):
                    host = Host(childHost.id, childHost.address,
                                childHost.port, leaf)
                    if childHost.id == sch_host:
                        leaf.owner = host
                    leaf.add_child(host)

                root_sch.add_child(leaf)
            except NoNodeError:
                log.debug("Scheduler host %s not found" % (sch_host,))
                continue
        return root_sch
    except Exception, e:
        log.exception(e)


def get_hierarchy_from_chairman(chairman_address, chairman_port,
                                root_address, root_port):
    """This function will will attempt to build the hierarchy from
    chairman's in-memory view of the tree.
    """
    try:
        (_, chairman_client) = create_chairman_client(chairman_address,
                                                      chairman_port)
        req = GetSchedulersRequest()
        resp = chairman_client.get_schedulers(req)
        if resp.result != GetSchedulersResultCode.OK or not resp.schedulers:
            log.error("get_schedulers failed, for chairman on %s:%s" %
                      (chairman_address, chairman_port))
            return
        root = None
        leaves = []
        # get the root scheduler
        for sch in resp.schedulers:
            if sch.role.id == ROOT_SCHEDULER_ID:
                # Todo(Maithem): add address/port in SchedulerRole
                owner = Host(ROOT_SCHEDULER_ID, root_address, root_port)
                root = Scheduler(ROOT_SCHEDULER_ID, ROOT_SCHEDULER_TYPE,
                                 owner)
            else:
                leaves.append(sch)
        if root is None:
            log.error("Couldn't find a root scheduler")
            return None
        # process
        for schEntry in leaves:
            leaf_owner_host = schEntry.agent
            leaf = schEntry.role
            leafSch = Scheduler(leaf.id, LEAF_SCHEDULER_TYPE)
            for childHost in leaf.host_children:
                host = Host(childHost.id, childHost.address,
                            childHost.port, leafSch)
                if childHost.id == leaf_owner_host:
                    leafSch.owner = host
                leafSch.add_child(host)
            root.add_child(leafSch)
        return root
    except Exception, e:
        log.exception(e)


def get_hosts_from_zk(zk_client):
    """This function will read all registered hosts in /hosts and return
    a list of Host objects.
    """
    try:
        hosts_list = []
        if not zk_client.exists(HOSTS_PREFIX):
            log.debug("%s doesn't exist" % (HOSTS_PREFIX,))
            return []

        hosts = zk_client.get_children(HOSTS_PREFIX)

        for host in hosts:
            try:
                path = "%s/%s" % (HOSTS_PREFIX, host)
                (value, stat) = zk_client.get(path)
                config = HostConfig()
                deserialize(config, value)
                _host = Host(config.agent_id, config.address.host,
                             config.address.port)
                hosts_list.append(_host)
            except NoNodeError:
                log.debug("host %s not found" % (host,))
                continue
        return hosts_list
    except Exception, e:
        log.exception(e)


def get_missing_hosts_from_zk(zk_client):
    """Returns a list of missing host ids.
    """
    try:
        missing_hosts = []
        if not zk_client.exists(MISSING_PREFIX):
            log.debug("%s doesn't exist" % (MISSING_PREFIX,))
            return []

        missing_hosts = zk_client.get_children(MISSING_PREFIX)
        return missing_hosts
    except Exception, e:
        log.exception(e)
