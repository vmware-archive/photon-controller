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

from thrift.TSerialization import deserialize
from thrift.TSerialization import serialize

from gen.common.ttypes import ServerAddress


def create_address(host, port):
    """Serialize the given address to thrift ServerAddress.

    :type host: str
    :type port: int
    :rtype: str
    """
    address = ServerAddress(host, port)
    return serialize(address)


def parse_address(value):
    """Deserialize the given thrift encoded string into a address tuple.

    :type value: str
    :rtype: tuple
    """
    address = ServerAddress()
    deserialize(address, value)
    return address.host, address.port
