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

# Save all the command classes
from psim.error import ParseError

commands = {}


class Command(object):
    """
    Command is basically a string which is inputted by user. It could be parsed
    into a sequence of orders to simulator, and be execuated by simulator.
    Currently, command has flat namespace, which means it has no subcommand
    like git does.
    """
    __metaclass__ = abc.ABCMeta

    @abc.abstractmethod
    def name():
        """Name of command, class method
        :rtype: string, name of the command
        """
        pass

    @abc.abstractmethod
    def usage():
        """Usage of command, class method
        :rtype: string, usage of command
        """
        pass

    @abc.abstractmethod
    def parse(tokens):
        """Parse command from tokens
        :param tokens: list of strings, user inputted string splitted to list
        :rtype: Command
        """
        pass

    @abc.abstractmethod
    def run(self):
        """Run command
        """
        pass


class Noop(Command):

    def name():
        return 'noop'

    def usage():
        return ''

    def parse(tokens):
        pass

    def run(self):
        pass


noop = Noop()


def parse_cmd(tokens):
    """Parse tokens into Command"""
    if len(tokens) == 0:
        return noop
    if tokens[0].startswith('#'):
        return noop
    for name, command in commands.items():
        if name == tokens[0]:
            return command.parse(tokens)

    raise ParseError


def register_cmd(command):
    """Register command class"""
    if command.name() not in commands:
        commands[command.name()] = command
