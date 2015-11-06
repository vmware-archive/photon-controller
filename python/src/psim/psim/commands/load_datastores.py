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
import uuid

from psim.command import ParseError, Command
from psim.universe import Universe


COMMMAND_NAME = "load_datastores"


class LoadDatastoresCmd(Command):
    """Load datastores

        Usage:
        > load_datastores <datastores_file>
    """

    def __init__(self, file):
        self.file = Universe.get_path(file)

    def run(self):
        content = open(self.file, 'r').read()
        datastores = yaml.load(content)
        dsmap = Universe.datastores
        for datastore in datastores:
            datastore['used'] = 0
            ds_uuid = uuid.uuid5(uuid.NAMESPACE_DNS,
                                 datastore['id'])
            dsmap[str(ds_uuid)] = datastore

    @staticmethod
    def name():
        return COMMMAND_NAME

    @staticmethod
    def parse(tokens):
        if len(tokens) != 2:
            raise ParseError

        return LoadDatastoresCmd(tokens[1])

    @staticmethod
    def usage():
        return "%s datastores_file" % COMMMAND_NAME
