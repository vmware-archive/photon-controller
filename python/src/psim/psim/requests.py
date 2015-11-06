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

import random
import yaml

from gen.resource.ttypes import Disk
from gen.resource.ttypes import Resource
from gen.resource.ttypes import ResourceConstraint
from gen.resource.ttypes import ResourceConstraintType as RCT
from gen.resource.ttypes import Vm
from gen.resource.ttypes import State
from gen.scheduler.ttypes import PlaceRequest
from gen.tracing.ttypes import TracingInfo

from psim.universe import Universe


class PsimVmRequest(object):
    """wraps the placement request with the load
       information
    """
    def __init__(self, place_request, env_info):
        self.place_request = place_request
        self.env_info = env_info


class Requests(object):
    """Placement requests that can be run in simulator. The requests are loaded
       from yaml files.
    """

    def __init__(self, requests_file):
        self.requests = []
        content = open(Universe.get_path(requests_file), 'r').read()
        requests = yaml.load(content)

        request_id = 1
        disk_id = 1
        if 'auto' in requests:
            requests = self.generate_requests(requests['auto'])

        for request in requests:
            place_request = PlaceRequest()
            resource = Resource()
            resource.disks = []
            env_info = {}

            if 'vm' in request:
                resource.vm = Vm()
                # Make the vm id look like a uuid by zero-padding. Otherwise
                # reference counting doesn't work.
                resource.vm.id = "{0:032d}".format(request_id)
                resource.vm.state = State.STARTED
                flavor = Universe.vm_flavors[request['vm']['flavor']]
                resource.vm.flavor = flavor.name
                resource.vm.flavor_info = flavor.to_thrift()
                resource.vm.disks = []
                if 'constraints' in request:
                    constraints = []
                    for c in request['constraints']:
                        constraint = ResourceConstraint()
                        constraint.type = RCT._NAMES_TO_VALUES[c['type']]
                        constraint.values = c['values']
                        if 'negative' in c:
                            constraint.negative = c['negative']
                        else:
                            constraint.negative = False
                        constraints.append(constraint)
                    if constraints:
                        resource.vm.resource_constraints = constraints
                if 'load' in request['vm']:
                    env_info['mem_load'] = request['vm']['load']['mem']

            if 'disks' in request:
                for d in request['disks']:
                    disk = Disk()
                    flavor = Universe.ephemeral_disk_flavors[d['flavor']]
                    disk.flavor = flavor.name
                    disk.flavor_info = flavor.to_thrift()
                    disk.id = str(disk_id)
                    disk.persistent = False
                    disk.new_disk = True
                    disk.capacity_gb = 1024  # hard coded in FakeVmManager
                    disk_id += 1
                    resource.vm.disks.append(disk)

            place_request.resource = resource
            tracing_info = TracingInfo()
            tracing_info.request_id = request_id
            place_request.tracing_info = tracing_info
            request_id += 1
            self.requests.append(PsimVmRequest(place_request, env_info))

    def generate_requests(self, config):
        """
        Generates some requests based on the VMs and the ratio specified.
        Depending on the ratio, the total number of requests may not be exactly
        equal to the count.
        """
        count = config['count']
        vm_types = config['types']
        type_count = 0
        for vm_type in vm_types:
            type_count += int(vm_type['ratio'])
        multiplier = float(count) / type_count

        requests = []
        for vm_type in vm_types:
            total = round(int(vm_type['ratio']) * multiplier)
            for i in range(1, int(total) + 1):
                request = {}
                request['vm'] = {}
                request['vm']['flavor'] = vm_type['flavor']
                if 'load' in vm_type:
                    request['vm']['load'] = vm_type['load']
                request['disks'] = []
                for flavor in vm_type['disks']:
                    request['disks'].append(flavor)
                requests.append(request)
        random.shuffle(requests)
        return requests
