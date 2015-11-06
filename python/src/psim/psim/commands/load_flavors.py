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

from common.kind import Flavor, QuotaLineItem, Unit
from psim.command import ParseError, Command
from psim.universe import Universe


COMMMAND_NAME = "load_flavors"


class LoadFlavorCmd(Command):
    """Load flavors

        Usage:
        > load_flavors <flavor_file>
    """

    def __init__(self, file):
        self.file = Universe.get_path(file)

    def run(self):
        content = open(self.file, 'r').read()
        content = yaml.load(content)

        if content['kind'] == 'vm':
            flavor_map = Universe.vm_flavors
        elif content['kind'] == 'ephemeral-disk':
            flavor_map = Universe.ephemeral_disk_flavors
        elif content['kind'] == 'persistent-disk':
            flavor_map = Universe.persistent_disk_flavors

        for dict_flavor in content['flavors']:
            cost = {}

            for dict_item in dict_flavor['cost']:
                unit = getattr(Unit, dict_item['unit'])
                cost[dict_item['key']] = QuotaLineItem(dict_item['key'],
                                                       str(dict_item['value']),
                                                       unit)

            flavor_map[dict_flavor['name']] = Flavor(dict_flavor['name'], cost)

    @staticmethod
    def name():
        return COMMMAND_NAME

    @staticmethod
    def parse(tokens):
        if len(tokens) != 2:
            raise ParseError

        return LoadFlavorCmd(tokens[1])

    @staticmethod
    def usage():
        return "%s flavor_file" % COMMMAND_NAME
