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

from psim.command import Command, ParseError

COMMMAND_NAME = "seed"


class SeedCmd(Command):
    """Set random seed

        Usage:
        > seed <seed_number>
    """

    def __init__(self, seed):
        self.seed = seed

    def run(self):
        random.seed(self.seed)

    @staticmethod
    def name():
        return COMMMAND_NAME

    @staticmethod
    def parse(tokens):
        if len(tokens) != 2:
            raise ParseError

        return SeedCmd(int(tokens[1]))

    @staticmethod
    def usage():
        return "%s <seed_number>" % COMMMAND_NAME
