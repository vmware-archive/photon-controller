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
import uuid

from gen.flavors.ttypes import QuotaUnit, QuotaLineItem, Flavor
from gen.resource.ttypes import Disk, DiskImage, CloneType, Vm, VmPowerState


def createVmResource(image):
    disk = Disk()
    disk.flavor = "some-disk-flavor"
    disk.id = str(uuid.uuid4())
    disk.persistent = False
    disk.new_disk = True
    disk.capacity_gb = 0
    disk.image = DiskImage()
    disk.image.id = image
    disk.image.clone_type = CloneType.COPY_ON_WRITE
    disk.flavor_info = Flavor()
    disk.flavor_info.name = "some-disk-flavor"
    disk.flavor_info.cost = []

    vm = Vm()
    vm.id = str(uuid.uuid4())
    vm.flavor = "some-vm-flavor"
    vm.state = VmPowerState.STOPPED
    vm.flavor_info = Flavor()
    vm.flavor_info.name = "some-vm-flavor"
    vm.flavor_info.cost = [
        QuotaLineItem("vm.cpu", "1", QuotaUnit.COUNT),
        QuotaLineItem("vm.memory", "0.5", QuotaUnit.GB)]
    vm.disks = [disk]

    return vm


def createDisksResource():
    disk = Disk()
    disk.flavor = "some-disk-flavor"
    disk.id = str(uuid.uuid4())
    disk.persistent = True
    disk.new_disk = True
    disk.capacity_gb = 2
    disk.flavor_info = Flavor()
    disk.flavor_info.name = "some-disk-flavor"
    disk.flavor_info.cost = []
    return [disk]
