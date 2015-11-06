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

from thrift.Thrift import TType

_deep_types = frozenset([TType.STRUCT, TType.LIST, TType.MAP])


def deep_validate(message):
    """Deep validation of thrift messages.

    :type message: object
    """
    message.validate()

    for spec in message.thrift_spec:
        if spec:
            ttype, name, args = spec[1:4]

            if ttype not in _deep_types:
                continue

            value = getattr(message, name)
            if value is None:
                continue

            if ttype == TType.STRUCT:
                deep_validate(value)
            elif ttype == TType.LIST:
                if args[0] in _deep_types:
                    for msg in value:
                        deep_validate(msg)
            elif ttype == TType.MAP:
                if args[2] in _deep_types:
                    for msg in value.values():
                        deep_validate(msg)
