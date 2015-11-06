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

from psim.command import Command, ParseError
from psim.universe import Universe

COMMMAND_NAME = "load_tree"


class LoadTreeCmd(Command):
    """Load scheduler tree from configuration

        Usage:
        > load_tree <config_file>
    """

    def __init__(self, file):
        self.file = Universe.get_path(file)

    def run(self):
        def _print_error(msg):
            print "! " + msg

        # Load scheduler tree from yaml file
        content = open(self.file, 'r').read()
        tree_config = yaml.load(content)
        Universe.get_tree().load_schedulers(tree_config, _print_error)

    @staticmethod
    def name():
        return COMMMAND_NAME

    @staticmethod
    def parse(tokens):
        if len(tokens) != 2:
            raise ParseError

        return LoadTreeCmd(tokens[1])

    @staticmethod
    def usage():
        return "%s tree_file" % COMMMAND_NAME
