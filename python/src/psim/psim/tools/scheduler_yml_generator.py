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

import yaml

from gen.resource.ttypes import ResourceConstraintType


class SchedulerYmlGenerator(object):
    """
    Stateless Scheduler YML file generator.
    Generate YML file depends on input list of HostConfiguration instance
    """
    def __init__(self, a_file_name):
        """
        :param a_file_name: Dump scheduler yml file name
        :type: string
        """
        self._yml_file_name = a_file_name

    def _write_to_yml(self, a_yml_data):
        """
        :param a_yml_data:
        :type: list of yml dictionary
        """
        with open(self._yml_file_name, 'w') as outfile:
            outfile.write(yaml.dump(a_yml_data, default_flow_style=None))

    def __call__(self, a_result):
        """
        Parsing Logic:
            if ConfigureRequest.scheduler is None:
                it's root scheduler

            if ConfigureRequest.roles is empty list:
                it's a host

            otherwise:
                it's a leaf scheduler

        :param a_result:
        :type: list of HostConfiguration
            scheduler <= return from ConfigureRequest.scheduler
            roles <= return from ConfigureRequest.roles
        """
        yml_data = []

        for hc in a_result:
            result = {'role': None, 'children': []}
            result['id'] = hc.host_id

            if hc.scheduler == 'None':
                result['role'] = 'root'

            if hc.roles.schedulers is not None:
                if result['role'] is None:
                    result['role'] = 'leaf'

                for sr in hc.roles.schedulers:
                    if sr.hosts is not None:
                        result['children'].extend(sr.hosts)

                    for ci in sr.host_children:
                        sub_result = \
                            {'id': ci.id,
                             'role': 'host',
                             'constraints': []}

                        for rc in ci.constraints:
                            sub_constraint = \
                                {'type': ResourceConstraintType.
                                 _VALUES_TO_NAMES[rc.type],
                                 'values': rc.values}
                            sub_result['constraints'].\
                                append(sub_constraint)

                        yml_data.append(sub_result)
                    yml_data.append(result)
            else:
                continue

        self._write_to_yml(yml_data)
