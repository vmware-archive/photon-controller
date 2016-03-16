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
import os
from optparse import OptionParser, BadOptionError


class KnownOptionParser(OptionParser):
    """ Hack of OptionParser, so that it ignores unknown options
    """

    def _process_args(self, largs, rargs, values):
        while rargs:
            try:
                OptionParser._process_args(self, largs, rargs, values)
            except BadOptionError:
                pass


def default_parser(usage, add_help=True):
    default_host = "localhost"
    if "ECCLI_HOST" in os.environ:
        default_host = os.environ["ECCLI_HOST"]

    parser = KnownOptionParser(usage, add_help_option=add_help)
    parser.add_option("-H", "--host", default=default_host,
                      action="store", type="string", dest="host",
                      help="host address; load default from env($ECCLI_HOST)")
    parser.add_option("-p", "--port", default="8835",
                      action="store", type="int", dest="port",
                      help="host port [default: %default]")
    parser.add_option("-n", "--requests",
                      action="store", type="int", dest="requests",
                      help="Number of requests [default: %default]")
    parser.add_option("-c", "--concurrency",
                      action="store", type="int", dest="concurrency",
                      help="Number of multiple requests [default: %default]")
    if not add_help:
        parser.add_option("-h", "--help",
                          action="store_true", dest="help")

    return parser
