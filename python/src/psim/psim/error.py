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


class ParseError(Exception):
    pass


class PreconditionError(Exception):
    pass


class ConfigError(Exception):
    pass


class SystemError(Exception):
    pass


class ConstraintError(Exception):
    def __init__(self, vm_id, constraint_id, host_id):
        super(ConstraintError, self).__init__("Constraint failed:")
        self.vm_id = vm_id
        self.constraint_id = constraint_id
        self.host_id = host_id

    def __str__(self):
        return "VM %s on host %s does not satisfy constrain %s" % \
               (self.vm_id, self.host_id, self.constraint_id)
