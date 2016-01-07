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
import random
import threading
import time
import uuid

from agent.tests.common_helper_functions import get_register_host_request
from agent.tests.common_helper_functions import create_chairman_client
from gen.chairman.ttypes import RegisterHostResultCode
from gen.chairman.ttypes import UnregisterHostRequest
from gen.chairman.ttypes import ReportMissingRequest
from gen.chairman.ttypes import ReportResurrectedRequest
from gen.chairman.ttypes import UnregisterHostResultCode
from gen.chairman.ttypes import ReportMissingResultCode
from gen.chairman.ttypes import ReportResurrectedResultCode
from gen.scheduler.ttypes import ConfigureResponse
from gen.scheduler.ttypes import ConfigureResultCode
from gen.host import Host
from integration_tests.servers.thrift_server import ThriftServer


logger = logging.getLogger(__name__)


class IllegalTransition(Exception):
    def __init__(self, msg):
        super(IllegalTransition, self).__init__(msg)


class BadState(Exception):
    def __init__(self, msg):
        super(BadState, self).__init__(msg)


class ConfigureException(Exception):
    def __init__(self):
        super(ConfigureException, self).__init__()


class AgentHandler(Host.Iface):
    """
    Handler to put config requests to a dict from host_id to request.
    """
    def __init__(self, hosts):
        """
        :param hosts: a dict of HostStateMachine objects indexed by host id
        """
        self.hosts = hosts

    def configure(self, config):
        host = self.hosts.get(config.host_id, None)
        if not host:
            logger.warn("Unknown host id %s." % (config.host_id,))
            return ConfigureResultCode.SYSTEM_ERROR
        resp_code = None
        try:
            host.apply_config(config)
            resp_code = ConfigureResultCode.OK
        except ConfigureException:
            resp_code = ConfigureResultCode.SYSTEM_ERROR

        return ConfigureResponse(resp_code)


class Transition(object):
    """
    A transition function that is executed to move from one
    state to another state.
    """
    def __init__(self, name, transition, dest, weight):
        """
        :param name: str, name of the transition (i.e. A->B)
        :param transition: A callable object
        :param dest: The destination State object
        :param weight: int from 1 to 100
        """
        self.name = name
        self.transition = transition
        self.dest = dest
        self.weight = weight

    def execute(self):
        if not self.transition:
            return self.dest
        try:
            self.transition()
            return self.dest
        except Exception:
            logger.exception("Failed to excute transition function!")


class State(object):
    """
    A state in the state machine that can have multiple transitions
    to other states.
    """
    def __init__(self, name):
        """
        :param name: name of the state
        """
        self.name = name
        self.transitions = []
        self.pdf = []

    def add_transition(self, transition):
        """
        Add an out-bound transition from this state to
        another state.
        """
        self.transitions.append(transition)

    def check_weights(self):
        """
        This method checks if the sum of probabilities of all the outbound
        transitions equals 100.
        """
        total_sum = 0
        for trans in self.transitions:
            total_sum += trans.weight
        if not total_sum == 100:
            raise BadState("Probabilities of state \"%s\""
                           " don't add up to 100" % self.name)

    def build_pdf(self):
        """
        Builds a list that represents the probability distribution
        function(PDF) of the outbound transitions from this state.
        For example, choosing a random element from self.pdf on average
        is equivelent to sampling the pdf.
        """
        self.check_weights()
        for trans in self.transitions:
            self.pdf += [trans] * trans.weight


class StateMachine(object):
    def __init__(self, name, start_state):
        """
        :param name: str, name of the state machine
        :param start_state: the starting state of this state machine.
        """
        self.name = name
        self.log = []
        self.start_state = start_state
        self.current_state = self.start_state

    def set_start_state(self, state):
        self.start_state = state
        self.current_state = self.start_state

    def transition(self, ind=None):
        """
        Based on the outbound transition probabilities, this
        method will allow the state machine to move from one
        state to another at random or using a specific transition.
        """
        if not self.current_state:
            raise IllegalTransition("Current state not initialized,"
                                    "can't transit to another state")

        trans = random.choice(self.current_state.pdf)
        destination = trans.execute()
        self.log.append(trans.name)
        if destination:
            self.current_state = destination
        else:
            logger.error("Couldn't move from state %s to state %s"
                         "because function %s failed!" %
                         (self.current_state.name,
                          destination.name, trans.name))


class HostStateMachine(StateMachine):

    CREATED_STATE = "Created"
    REGISTERED_STATE = "Registered"
    CONFIGURED_STATE = "Configured"
    MISSING_STATE = "Missing"
    CHANGED_STATE = "Changed"

    def __init__(self, _id, availability_zones, datastores, networks,
                 datastores_num, networks_num, client, host, port):
        """
        :param _id: str that represents the host name/id
        :availability_zones: a list of availability zones to sample from
        :datastores: a list of datastores to sample from
        :networks: a list of networks to sample from
        :image_datastores: a list of image datastore uuids
        :datastores_num: number of datastores to sample
        :networks_num: number of networks to sample
        :client: chairman client
        """
        self.host = host
        self.port = port
        self.id = _id
        self.sample_availability_zones = availability_zones
        self.sample_datastores = datastores
        self.sample_networks = networks
        self.datastores_num = datastores_num
        self.networks_num = networks_num
        # host properties
        self.availability_zone = None
        self.image_datastore = None
        self.datastores = None
        self.networks = None
        self.config = None
        self.new_config = False
        self.chairman = client
        self.lock = threading.RLock()
        self.active = [self.REGISTERED_STATE, self.CONFIGURED_STATE]
        self.states = {}
        super(HostStateMachine, self).__init__(self.id, None)

    def __str__(self):
        class blob(object):
            def __init__(self):
                self.blob = ""

            def add_line(self, _str):
                self.blob += _str + '\n'

        b = blob()
        b.add_line("")
        b.add_line("host id : %s" % (self.id))
        b.add_line("current state : %s" % (self.current_state.name))
        b.add_line("host availability zone : %s" % (self.availability_zone))
        b.add_line("host image datastore : %s" % (self.availability_zone))
        b.add_line("host datastores : %s" % (self.datastores))
        b.add_line("host networks : %s" % (self.networks))
        b.add_line("host new config : %s" % (self.new_config))
        b.add_line("host config : %s" % (self.config))
        b.add_line("host log : %s" % (self.log))
        return b.blob

    def _sample(self, lst, k):
        try:
            return random.sample(lst, k)
        except IndexError:
            return []

    def init_host_properties(self):
        """
        Sets the host properties via sampling.
        """
        self.availability_zone = random.choice(self.sample_availability_zones)
        self.datastores = self._sample(self.sample_datastores,
                                       self.datastores_num)
        self.image_datastore = random.choice(self.datastores)
        self.networks = self._sample(self.sample_networks,
                                     self.networks_num)

    def apply_config(self, config):
        """
        This method will be called by the host handler.
        """
        with self.lock:
            if (self.current_state.name in self.active or
                (self.current_state.name == self.CHANGED_STATE and
                 self.config is not None)):
                self.config = config
                self.new_config = True
            else:
                raise ConfigureException()

    def sync(self):
        """
        Process host configuration flow, if it exists.
        """
        with self.lock:
            if self.new_config and self.current_state.name in self.active:
                self.current_state = self.states[self.CONFIGURED_STATE]
                self.new_config = False
                return True
            else:
                return False

    def discard_config(self):
        with self.lock:
            self.new_config = False
            self.config = None

    def register_host(self):
        # Agent just registered, i.e. restarted
        # discard current config
        self.discard_config()
        image_ds = self.image_datastore.id
        avail_zone = self.availability_zone
        req = get_register_host_request(self.host, self.port,
                                        agent_id=self.id,
                                        networks=self.networks,
                                        datastores=self.datastores,
                                        image_datastore=image_ds,
                                        availability_zone=avail_zone)
        resp = self.chairman.register_host(req)
        if resp.result != RegisterHostResultCode.OK:
            msg = ("Host registration failed for host id %s with error"
                   " code %s" % (self.id, resp.result))
            raise Exception(msg)

    def unregister_host(self):
        req = UnregisterHostRequest(self.id)
        resp = self.chairman.unregister_host(req)
        if resp.result != UnregisterHostResultCode.OK:
            msg = ("Host unregistration failed for host id %s with error"
                   " code %s" % (self.id, resp.result))
            raise Exception(msg)
        # discard current config
        self.discard_config()

    def report_missing(self):
        req = ReportMissingRequest("some_scheduler", None, [self.id])
        resp = self.chairman.report_missing(req)
        if resp.result != ReportMissingResultCode.OK:
            msg = ("Report missing failed for host id %s with error"
                   " code %s" % (self.id, resp.result))
            raise Exception(msg)
        self.discard_config()

    def report_resurrected(self):
        req = ReportResurrectedRequest("some_scheduler", None, [self.id])
        resp = self.chairman.report_resurrected(req)
        if resp.result != ReportResurrectedResultCode.OK:
            msg = ("Report missing failed for host id %s with error"
                   " code %s" % (self.id, resp.result))
            raise Exception(msg)

    def change_host_data(self):
        self.init_host_properties()

    def stall(self):
        pass

    def build(self):
        created = State(self.CREATED_STATE)
        registered = State(self.REGISTERED_STATE)
        configured = State(self.CONFIGURED_STATE)
        missing = State(self.MISSING_STATE)
        changed = State(self.CHANGED_STATE)

        self.states[self.CREATED_STATE] = created
        self.states[self.REGISTERED_STATE] = registered
        self.states[self.CONFIGURED_STATE] = configured
        self.states[self.MISSING_STATE] = missing
        self.states[self.CHANGED_STATE] = changed

        def name(s1, s2):
            return "%s->%s" % (s1.name, s2.name)

        # Transition abbreviations
        # RH - register_host
        # UH - unregister_host
        # config - None
        # RM - report_missing
        # RR - report_resurrected

        # --------Transitions for the created state--------
        # created -RH-> registered
        trans = Transition(name(created, registered),
                           self.register_host, registered, 100)
        created.add_transition(trans)

        # --------Transitions for the changed state--------
        # changed -RH-> registered
        trans = Transition(name(changed, registered),
                           self.register_host, registered, 100)
        changed.add_transition(trans)

        # --------Transition for the registered state--------
        # registered -UH-> created
        trans = Transition(name(registered, created),
                           self.unregister_host, created, 5)
        registered.add_transition(trans)

        # registered -RH-> registered
        trans = Transition(name(registered, registered),
                           self.register_host, registered, 20)
        registered.add_transition(trans)

        # registered -config-> configured
        trans = Transition(name(registered, configured),
                           self.sync, configured, 50)
        registered.add_transition(trans)

        # registered -RM-> missing
        trans = Transition(name(registered, missing),
                           self.report_missing, missing, 5)
        registered.add_transition(trans)

        # registered -stall-> registered
        trans = Transition(name(registered, registered),
                           self.stall, registered, 5)
        registered.add_transition(trans)

        # registered -CD-> changed
        trans = Transition(name(registered, changed),
                           self.change_host_data, changed, 15)
        registered.add_transition(trans)

        # --------Transitions for the configured state--------
        # configured -config-> configured
        trans = Transition(name(configured, configured),
                           self.sync, configured, 50)
        configured.add_transition(trans)

        # configured -RH-> registered
        trans = Transition(name(configured, registered),
                           self.register_host, registered, 10)
        configured.add_transition(trans)

        # configured -UH-> created
        trans = Transition(name(configured, created),
                           self.unregister_host, created, 5)
        configured.add_transition(trans)

        # configured -RR-> configured
        trans = Transition(name(configured, configured),
                           self.report_resurrected, configured, 10)
        configured.add_transition(trans)

        # configured -RM-> missing
        trans = Transition(name(configured, missing),
                           self.report_missing, missing, 20)
        configured.add_transition(trans)

        # configured -CD-> changed
        trans = Transition(name(configured, changed),
                           self.change_host_data, changed, 5)
        configured.add_transition(trans)

        # --------Transitions for the missing state--------
        # missing -RM-> missing
        trans = Transition(name(missing, missing),
                           self.report_missing, missing, 10)
        missing.add_transition(trans)

        # missing -UH-> created
        trans = Transition(name(missing, created),
                           self.unregister_host, created, 10)
        missing.add_transition(trans)

        # missing -RH-> registered
        trans = Transition(name(missing, registered),
                           self.register_host, registered, 70)
        missing.add_transition(trans)

        # missing -CD-> changed
        trans = Transition(name(missing, changed),
                           self.change_host_data, changed, 10)
        missing.add_transition(trans)

        # build pdfs
        created.build_pdf()
        registered.build_pdf()
        configured.build_pdf()
        missing.build_pdf()
        changed.build_pdf()

        # Set the starting state
        self.set_start_state(created)


class Simulator(object):
    ROOT_HOST_ID = "ROOT_SCHEDULER_HOST"

    def __init__(self, chairman_list, chairman_clients_num,
                 host_num, agent_host, agent_port, datastores,
                 availability_zones, networks, sleep_min=0, sleep_max=500):
        self.clients = []
        self.hosts = {}
        self.agent_host = agent_host
        self.agent_port = agent_port
        self.host_num = host_num
        self.handler = AgentHandler(self.hosts)
        self.datastores = datastores
        self.availability_zones = availability_zones
        self.networks = networks
        self.sleep_min = sleep_min
        self.sleep_max = sleep_max
        self.servers = []
        self.log = []
        # Create clients
        for x in xrange(chairman_clients_num):
            chairman = random.choice(chairman_list)
            chairman = chairman.split(":")
            client = create_chairman_client(chairman[0], int(chairman[1]))
            self.clients.append(client[1])

    def _random_int(self, lst):
        return random.randrange(0, len(lst)) + 1

    def init(self):
        # Add root scheduler
        root_host = HostStateMachine(self.ROOT_HOST_ID, None, None, None,
                                     None, None, None, None, None)
        root_host.set_start_state(State(HostStateMachine.REGISTERED_STATE))
        self.hosts[self.ROOT_HOST_ID] = root_host
        # Create hosts
        for host in xrange(self.host_num):
            _id = str(uuid.uuid4())
            client = random.choice(self.clients)
            datastore_num = self._random_int(self.datastores)
            networks_num = self._random_int(self.networks)
            host = HostStateMachine(_id, self.availability_zones,
                                    self.datastores, self.networks,
                                    datastore_num, networks_num, client,
                                    self.agent_host, self.agent_port)
            host.init_host_properties()
            host.build()
            self.hosts[_id] = host

        self.server = ThriftServer(self.agent_host, self.agent_port,
                                   self.handler, None, self.handler)
        self.servers.append(self.server)
        self.server.start_server()

    def sync_machines(self):
        """
        Since the state machine will transition a random number of times,
        the last transition might not process a host config, even if it
        exists. This method, will sync all state machines that are in
        a registered or configured state and have a new config, that hasn't
        been processed.
        """
        for host in self.hosts.values():
            if host.id == self.ROOT_HOST_ID:
                continue
            host.sync()

    def run_batch(self, size):
        for x in xrange(size):
            random_host = random.choice(self.hosts.values())
            while random_host.id == self.ROOT_HOST_ID:
                random_host = random.choice(self.hosts.values())
            self.log.append(random_host)
            random_host.transition()
            sleep_ms = (random.randint(self.sleep_min, self.sleep_max)
                        / 1000.0)
            time.sleep(sleep_ms)

    def clean_up(self):
        for server in self.servers:
            try:
                server.stop_server()
            except Exception:
                pass
