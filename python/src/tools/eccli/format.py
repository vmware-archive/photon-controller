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
from pprint import pformat

from datetime import datetime


def print_request(request):
    print("\n\033[32m***** %s (%s) *****\033[37m" %(type(request).__name__, datetime.now()))
    print(pformat(vars(request), indent=2))
    print("\n")


def print_response(response):
    print("\n\033[34m***** %s (%s) *****\033[37m" % (type(response).__name__, datetime.now()))
    print(pformat(vars(response), indent=2))
    print("\n")


def print_result(result, margin=True):
    if margin:
        print("\n")
    print("\033[33m***** %s *****\033[37m" % result)
    if margin:
        print("\n")
