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

from psim.command import Command

COMMMAND_NAME = "quit"


class QuitCmd(Command):
    """Quit the program

        Usage:
        > quit
    """

    def run(self):
        exit(0)

    @staticmethod
    def name():
        return COMMMAND_NAME

    @staticmethod
    def parse(tokens):
        return QuitCmd()

    @staticmethod
    def usage():
        return "%s" % COMMMAND_NAME
