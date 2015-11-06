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

from psim.command import Command, ParseError

COMMMAND_NAME = "log"


class LogCmd(Command):
    """Set log file and log level

        Usage:
        > log <log_file> <log_level>
        log_file: the log file name. stdout if specified as console.
        log_level: CRITICAL|ERROR|WARNING|INFO|DEBUG|NOTSET
    """

    def __init__(self, log_file, log_level):
        self.log_file = log_file
        self.log_level = log_level

    def run(self):
        if self.log_file == 'console':
            handler = logging.StreamHandler()
        else:
            handler = logging.FileHandler(self.log_file)
        level = getattr(logging, self.log_level)

        logger = logging.getLogger()
        logger.setLevel(level)

        formatter = logging.Formatter('%(asctime)s - %(name)s -' +
                                      '%(levelname)s - %(message)s')
        handler.setFormatter(formatter)
        logger.addHandler(handler)

    @staticmethod
    def name():
        return COMMMAND_NAME

    @staticmethod
    def parse(tokens):
        if len(tokens) != 3:
            raise ParseError

        return LogCmd(tokens[1], tokens[2])

    @staticmethod
    def usage():
        return "%s log_file log_level" % COMMMAND_NAME
