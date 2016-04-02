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

""" Implements the interfaces that are defined in the thrift Host service."""

import datetime
import logging
import os
import sys
import threading
import uuid

import common
from common.lock import AlreadyLocked
from common.lock import lock_with
from common.lock_vm import lock_vm
from common.log import log_duration
from common.mode import MODE
from common.photon_thrift.decorators import error_handler
from common.photon_thrift.decorators import log_request
from common.service_name import ServiceName
from gen.common.ttypes import ServerAddress
from gen.host import Host
from gen.host.ttypes import AttachISOResponse
from gen.host.ttypes import AttachISOResultCode
from gen.host.ttypes import CopyImageResponse
from gen.host.ttypes import CopyImageResultCode
from gen.host.ttypes import CreateDiskError
from gen.host.ttypes import CreateDiskResultCode
from gen.host.ttypes import CreateDisksResponse
from gen.host.ttypes import CreateDisksResultCode
from gen.host.ttypes import CreateImageFromVmResponse
from gen.host.ttypes import CreateImageFromVmResultCode
from gen.host.ttypes import CreateImageResponse
from gen.host.ttypes import CreateImageResultCode
from gen.host.ttypes import CreateVmResponse
from gen.host.ttypes import CreateVmResultCode
from gen.host.ttypes import DeleteDirectoryResponse
from gen.host.ttypes import DeleteDirectoryResultCode
from gen.host.ttypes import DeleteDiskError
from gen.host.ttypes import DeleteDiskResultCode
from gen.host.ttypes import DeleteDisksResponse
from gen.host.ttypes import DeleteDisksResultCode
from gen.host.ttypes import DeleteImageResponse
from gen.host.ttypes import DeleteImageResultCode
from gen.host.ttypes import DeleteVmResponse
from gen.host.ttypes import DeleteVmResultCode
from gen.host.ttypes import DetachISOResponse
from gen.host.ttypes import DetachISOResultCode
from gen.host.ttypes import FinalizeImageResponse
from gen.host.ttypes import FinalizeImageResultCode
from gen.host.ttypes import GetConfigResponse
from gen.host.ttypes import GetConfigResultCode
from gen.host.ttypes import GetDatastoresResponse
from gen.host.ttypes import GetDatastoresResultCode
from gen.host.ttypes import GetDeletedImagesResponse
from gen.host.ttypes import GetHostModeResponse
from gen.host.ttypes import GetHostModeResultCode
from gen.host.ttypes import GetImagesResponse
from gen.host.ttypes import GetImagesResultCode
from gen.host.ttypes import GetInactiveImagesResponse
from gen.host.ttypes import GetMonitoredImagesResultCode
from gen.host.ttypes import GetNetworksResponse
from gen.host.ttypes import GetNetworksResultCode
from gen.host.ttypes import GetResourcesResponse
from gen.host.ttypes import GetResourcesResultCode
from gen.host.ttypes import GetVmNetworkResponse
from gen.host.ttypes import GetVmNetworkResultCode
from gen.host.ttypes import HostConfig
from gen.host.ttypes import HostMode
from gen.host.ttypes import HttpTicketResponse
from gen.host.ttypes import HttpTicketResultCode
from gen.host.ttypes import ImageInfoResponse
from gen.host.ttypes import ImageInfoResultCode
from gen.host.ttypes import MksTicketResponse
from gen.host.ttypes import MksTicketResultCode
from gen.host.ttypes import PowerVmOp
from gen.host.ttypes import PowerVmOpResponse
from gen.host.ttypes import PowerVmOpResultCode
from gen.host.ttypes import ReceiveImageResponse
from gen.host.ttypes import ReceiveImageResultCode
from gen.host.ttypes import ReserveResponse
from gen.host.ttypes import ReserveResultCode
from gen.host.ttypes import ServiceTicketResponse
from gen.host.ttypes import ServiceTicketResultCode
from gen.host.ttypes import ServiceType
from gen.host.ttypes import SetHostModeResponse
from gen.host.ttypes import SetHostModeResultCode
from gen.host.ttypes import StartImageOperationResultCode
from gen.host.ttypes import StartImageScanResponse
from gen.host.ttypes import StartImageSweepResponse
from gen.host.ttypes import StopImageOperationResponse
from gen.host.ttypes import StopImageOperationResultCode
from gen.host.ttypes import TransferImageResponse
from gen.host.ttypes import TransferImageResultCode
from gen.host.ttypes import VmDiskOpResultCode
from gen.host.ttypes import VmDisksOpResponse
from gen.resource.ttypes import CloneType
from gen.resource.ttypes import Datastore
from gen.resource.ttypes import ImageInfo
from gen.resource.ttypes import InactiveImageDescriptor
from gen.roles.ttypes import Roles
from gen.scheduler import Scheduler
from gen.scheduler.ttypes import ConfigureResponse
from gen.scheduler.ttypes import ConfigureResultCode
from gen.scheduler.ttypes import FindResponse
from gen.scheduler.ttypes import FindResultCode
from gen.scheduler.ttypes import PlaceResponse
from gen.scheduler.ttypes import PlaceResultCode
from gen.scheduler.ttypes import Score
from host.host_configuration import HostConfiguration
from host.hypervisor.datastore_manager import DatastoreNotFoundException
from host.hypervisor.image_manager import DirectoryNotFound
from host.hypervisor.image_manager import ImageNotFoundException
from host.hypervisor.image_scanner import DatastoreImageScanner
from host.hypervisor.image_sweeper import DatastoreImageSweeper
from host.hypervisor.placement_manager import InvalidReservationException
from host.hypervisor.placement_manager import \
    MissingPlacementDescriptorException
from host.hypervisor.placement_manager import NoSuchResourceException
from host.hypervisor.placement_manager import NotEnoughCpuResourceException
from host.hypervisor.placement_manager import \
    NotEnoughDatastoreCapacityException
from host.hypervisor.placement_manager import NotEnoughMemoryResourceException
from host.hypervisor.resources import AgentResourcePlacementList
from host.hypervisor.resources import Disk
from host.hypervisor.resources import State
from host.hypervisor.resources import Vm
from host.hypervisor.task_runner import TaskAlreadyRunning

from hypervisor.disk_manager import DiskAlreadyExistException
from hypervisor.image_manager import ImageInUse
from hypervisor.image_manager import InvalidImageState
from hypervisor.vm_manager import DiskNotFoundException
from hypervisor.vm_manager import IsoNotAttachedException
from hypervisor.vm_manager import OperationNotAllowedException
from hypervisor.vm_manager import VmAlreadyExistException
from hypervisor.vm_manager import VmNotFoundException
from hypervisor.vm_manager import VmPowerStateException


class HypervisorNotConfigured(Exception):
    pass


class UnknownFlavor(Exception):
    pass


class HostHandler(Host.Iface):
    """A class for handling Host service requests."""

    GENERATION_GAP = 10

    VMINFO_TENANT_KEY = "tenant"
    VMINFO_PROJECT_KEY = "project"

    def __init__(self, hypervisor):
        """Constructor for the host handler

        :param hypervisor: the hypervisor instance
        """
        self._configure_lock = threading.Lock()
        self._logger = logging.getLogger(__name__)

        agent_config = common.services.get(ServiceName.AGENT_CONFIG)
        self._agent_id = agent_config.host_id
        self._address = ServerAddress(host=agent_config.hostname,
                                      port=agent_config.host_port)

        self._hypervisor = hypervisor
        self._generation = 0
        self._configuration_observers = []

    @property
    def hypervisor(self):
        if not self._hypervisor:
            raise HypervisorNotConfigured()
        else:
            return self._hypervisor

    @hypervisor.setter
    def hypervisor(self, value):
        self._hypervisor = value

    def add_configuration_observer(self, observer):
        """Add configuration observer

        :param observer: configuration observer to add
        :type observer: function
        """
        self._configuration_observers.append(observer)

    def remove_configuration_observer(self, observer):
        """Remove configuration observer

        :param observer: configuration observer to remove
        :type observer: function
        """
        try:
            self._configuration_observers.remove(observer)
        except ValueError:
            pass  # observer not found

    def configure_host(self, leaf_scheduler, roles=Roles(), host_id=None):
        # Call registered observers
        config = HostConfiguration(leaf_scheduler,
                                   roles,
                                   host_id)
        for observer in self._configuration_observers:
            observer(config)

        # Configure the scheduler plugin if it's registered.
        scheduler = common.services.get(Scheduler.Iface)
        if scheduler:
            scheduler.configure(roles.schedulers or [])

    @log_request
    @error_handler(ConfigureResponse, ConfigureResultCode)
    @lock_with("_configure_lock")
    def configure(self, request):
        """Configure Host.

        :type request: ConfigureRequest
        :rtype: ConfigureResponse
        """
        self.configure_host(request.scheduler,
                            request.roles,
                            request.host_id)
        return ConfigureResponse(ConfigureResultCode.OK)

    @error_handler(GetConfigResponse, GetConfigResultCode)
    def get_host_config_no_logging(self, request):
        """Host config.

        :type request: GetConfigRequest
        :rtype: GetConfigResponse
        """
        response = GetConfigResponse()
        response.result = GetConfigResultCode.OK

        config = HostConfig()
        agent_config = common.services.get(ServiceName.AGENT_CONFIG)
        config.agent_id = agent_config.host_id
        config.deployment_id = agent_config.deployment_id
        config.management_only = agent_config.management_only
        config.address = ServerAddress(host=agent_config.hostname,
                                       port=agent_config.host_port)
        if not self._hypervisor:
            raise HypervisorNotConfigured()
        networks = self._hypervisor.network_manager.get_networks()
        vm_network_names = self._hypervisor.network_manager.get_vm_networks()
        config.networks = [network for network in networks
                           if network.id in vm_network_names]
        dm = self._hypervisor.datastore_manager
        config.datastores = dm.get_datastores()
        config.image_datastore_ids = dm.image_datastores()

        config.memory_mb = self._hypervisor.system.total_vmusable_memory_mb()
        config.cpu_count = self._hypervisor.system.num_physical_cpus()
        config.esx_version = self._hypervisor.system.host_version()
        response.hostConfig = config
        return response

    @log_request
    def get_host_config(self, request):
        """get_host_config wrapper that adds request/response logging."""
        return self.get_host_config_no_logging(request)

    @log_request
    @error_handler(GetHostModeResponse, GetHostModeResultCode)
    def get_host_mode(self, request):
        """
        :type request: GetHostModeRequest
        :rtype: GetHostModeResponse
        """
        mode = common.services.get(ServiceName.MODE).get_mode()
        agent_mode = HostMode._NAMES_TO_VALUES[mode.name]
        return GetHostModeResponse(GetHostModeResultCode.OK,
                                   mode=agent_mode)

    @log_request
    @error_handler(SetHostModeResponse, SetHostModeResultCode)
    def set_host_mode(self, request):
        """
        :type request: SetHostModeRequest
        :rtype: SetHostModeResponse
        """
        mode = common.services.get(ServiceName.MODE)
        mode_name = HostMode._VALUES_TO_NAMES[request.mode]
        mode.set_mode(getattr(MODE, mode_name))
        return SetHostModeResponse(GetHostModeResultCode.OK)

    @log_request
    @error_handler(ReserveResponse, ReserveResultCode)
    def reserve(self, request):
        """Reserve resources.

        :type request: ReserveRequest
        :rtype: ReserveResponse
        """
        mode = common.services.get(ServiceName.MODE).get_mode()

        if mode == MODE.ENTERING_MAINTENANCE or mode == MODE.MAINTENANCE:
            return self._error_response(
                ReserveResultCode.OPERATION_NOT_ALLOWED,
                "reserve not allowed in {0} mode".format(mode.name),
                ReserveResponse())

        if self._generation - request.generation > HostHandler.GENERATION_GAP:
            return ReserveResponse(ReserveResultCode.STALE_GENERATION,
                                   "stale generation")

        try:
            disks, vm = self._parse_resource_request(request.resource)
        except UnknownFlavor:
            return ReserveResponse(ReserveResultCode.SYSTEM_ERROR,
                                   "unknown flavor")

        reservation = self._hypervisor.placement_manager.reserve(vm, disks)
        self._generation += 1
        return ReserveResponse(ReserveResultCode.OK,
                               reservation=reservation)

    def try_delete_vm(self, vm_id):
        """Try and delete VMs that might be stale.
        Makes a single attempt at deleting the orphan VM and logs any failures.
        :type string: vm id
        """
        try:
            self.hypervisor.vm_manager.delete_vm(vm_id)
        except:
            self._logger.warning("Failed to delete stale vm %s" % vm_id)

    def _datastore_freespace(self, datastore):
        datastore_info = self.hypervisor.datastore_manager.datastore_info(
            datastore)
        return datastore_info.total - datastore_info.used

    def _datastores_for_image(self, vm):
        image_id = self.hypervisor.image_manager.get_image_id_from_disks(
            vm.disks)
        return self.hypervisor.image_manager.datastores_with_image(
            image_id, self.hypervisor.datastore_manager.vm_datastores())

    """
    Datastore selection is now performed during the
    placement phase. The placement descriptor is
    returned during the placement phase and forwarded
    by the FE. For now we assume all ephemeral disks reside
    on the same datastore.
    """
    def _select_datastore_for_vm_create(self, vm):
        if not vm.placement:
            """
            Return an error if the placement descriptor
            is missing. The FE should re-try placement
            """
            raise MissingPlacementDescriptorException()

        return vm.placement.container_id

    def _get_create_vm_metadata(self, vm):
        # The image, if used, may contain addition metadata describing further
        # customization of the VM being created.
        image_id = self.hypervisor.image_manager.get_image_id_from_disks(
            vm.disks)
        if image_id is not None:
            return self.hypervisor.image_manager.image_metadata(
                image_id, self.hypervisor.datastore_manager.vm_datastores())

    @log_request
    @error_handler(CreateVmResponse, CreateVmResultCode)
    def create_vm(self, request):
        """Create a VM.

        :type request: CreateVmRequest
        :rtype: CreateVmResponse
        """
        mode = common.services.get(ServiceName.MODE).get_mode()

        if mode == MODE.ENTERING_MAINTENANCE or mode == MODE.MAINTENANCE:
            return self._error_response(
                CreateVmResultCode.OPERATION_NOT_ALLOWED,
                "create_vm not allowed in {0} mode".format(mode.name),
                CreateVmResponse())

        pm = self.hypervisor.placement_manager

        try:
            vm = pm.consume_vm_reservation(request.reservation)
        except InvalidReservationException:
            return CreateVmResponse(CreateVmResultCode.INVALID_RESERVATION,
                                    "Invalid VM reservation")

        try:
            return self._do_create_vm(request, vm)
        finally:
            pm.remove_vm_reservation(request.reservation)

    def _do_create_vm(self, request, vm):

        try:
            datastore_id = self._select_datastore_for_vm_create(vm)
        except MissingPlacementDescriptorException, e:
            self._logger.error("Cannot create VM as reservation is "
                               "missing placement information, %s" % vm.id)
            return CreateVmResponse(
                CreateVmResultCode.PLACEMENT_NOT_FOUND, type(e).__name__)

        self._logger.info("Creating VM %s in datastore %s" % (vm.id,
                                                              datastore_id))

        # Step 0: Lazy copy image to datastore
        image_id = self.hypervisor.image_manager.get_image_id_from_disks(
            vm.disks)

        if image_id and not self.hypervisor.image_manager.\
                check_and_validate_image(image_id, datastore_id):
            self._logger.info("Lazy copying image %s to %s" % (image_id,
                                                               datastore_id))
            try:
                image_datastore = self._find_datastore_by_image(image_id)
                self.hypervisor.image_manager.copy_image(image_datastore,
                                                         image_id,
                                                         datastore_id,
                                                         image_id)
            except DiskAlreadyExistException:
                self._logger.info("Image already exists. Use existing image.")

        # Step 1: Create the base VM create spec.
        # Doesn't throw
        vm_meta = self._get_create_vm_metadata(vm)
        # Create CreateVm spec. image_id is only useful in fake agent.
        spec = self.hypervisor.vm_manager.create_vm_spec(
            vm.id, datastore_id, vm.flavor, vm_meta, request.environment,
            image_id=image_id)

        # Step 1b: Perform non-device-related customizations as specified by
        # the metadata
        self.hypervisor.vm_manager.customize_vm(spec)

        # Step 1c: Set extra vminfo
        vminfo = {}
        if vm.tenant_id:
            vminfo[self.VMINFO_TENANT_KEY] = vm.tenant_id
        if vm.project_id:
            vminfo[self.VMINFO_PROJECT_KEY] = vm.project_id

        self._logger.debug("Setting vminfo: %s" % vminfo)
        self.hypervisor.vm_manager.set_vminfo(spec, vminfo)

        self._logger.debug(
            "VM create, done creating vm spec, vm-id: %s" % vm.id)

        # Step 2: Add the nics to the create spec of the VM.
        networks = self._hypervisor.network_manager.get_vm_networks()

        # TODO(shchang): request.network_connection_spec will be obsolete.
        if request.network_connection_spec:
            for nic_spec in request.network_connection_spec.nic_spec:
                # Check if this esx has that network.
                if (nic_spec.network_name and
                        nic_spec.network_name not in networks):
                    self._logger.info("Unknown a non provisioned network %s"
                                      % nic_spec.network_name)
                self.hypervisor.vm_manager.add_nic(
                    spec,
                    nic_spec.network_name)
        elif len(vm.networks) != 0 and networks:
            placement_networks = set(vm.networks)
            host_networks = set(networks)

            if not placement_networks.issubset(host_networks):
                intersected_networks = placement_networks & host_networks
                missing_networks = placement_networks - intersected_networks

                return CreateVmResponse(
                    CreateVmResultCode.NETWORK_NOT_FOUND,
                    "Unknown non provisioned networks: {0}".format(
                        missing_networks))
            else:
                self._logger.debug("Using the placement networks: {0}".format(
                    placement_networks))
                for network_name in placement_networks:
                    self.hypervisor.vm_manager.add_nic(
                        spec,
                        network_name)
        elif networks:
            # Pick a default network.
            self._logger.debug("Using the default network: %s" %
                               networks[0])
            self.hypervisor.vm_manager.add_nic(spec, networks[0])
        else:
            self._logger.warning("VM %s created without a NIC" % vm.id)

        self._logger.debug(
            "VM create, done creating nics, vm-id: %s" % vm.id)

        # Step 4: Add created_disk to create spec of the VM.
        try:
            self._create_disks(spec, datastore_id, vm.disks)
        except ImageNotFoundException, e:
            return CreateVmResponse(CreateVmResultCode.IMAGE_NOT_FOUND,
                                    "Invalid image id %s"
                                    % e.args[:1])
        except Exception:
            return CreateVmResponse(CreateVmResultCode.SYSTEM_ERROR,
                                    "Failed to create disk spec")

        self._logger.debug("VM create, done creating disks, vm-id: %s" % vm.id)

        # Step 5: Actually create the VM
        try:
            self.hypervisor.vm_manager.create_vm(vm.id, spec)
        except VmAlreadyExistException:
            self._logger.error("vm with id %s already exists" % vm.id)
            return CreateVmResponse(CreateVmResultCode.VM_ALREADY_EXIST,
                                    "Failed to create VM")
        except Exception:
            self._logger.exception("error creating vm with id %s" % vm.id)
            return CreateVmResponse(CreateVmResultCode.SYSTEM_ERROR,
                                    "Failed to create VM")

        self._logger.debug("VM create, done creating vm, vm-id: %s" % vm.id)

        # Set the guest properties for ip address.
        # We have to do this as a separate reconfigure call as we need to know
        # the device to network mapping and the vm uuid for mac address
        # generation.
        if request.network_connection_spec:
            try:
                spec = self.hypervisor.vm_manager.update_vm_spec()
                info = self.hypervisor.vm_manager.get_vm_config(vm.id)
                if self.hypervisor.vm_manager.set_guestinfo_ip(
                        spec, info, request.network_connection_spec):
                    self.hypervisor.vm_manager.update_vm(vm.id, spec)
            except Exception:
                self._logger.exception(
                    "error to set the ip/mac address of vm with id %s" % vm.id)
                self.try_delete_vm(vm.id)
                return CreateVmResponse(
                    CreateVmResultCode.SYSTEM_ERROR,
                    "Failed to set the ip/mac address of the VM %s"
                    % sys.exc_info()[1])

        self._logger.debug(
            "VM create, done updating network spec vm, vm-id: %s" % vm.id)

        # Step 6: touch the timestamp file for the image
        if image_id is not None:
            try:
                response = self._touch_image_timestamp(uuid.UUID(vm.id),
                                                       datastore_id, image_id)
                if response.result != CreateVmResultCode.OK:
                    self.try_delete_vm(vm.id)
                    return response
            except ValueError as e:
                return CreateVmResponse(CreateVmResultCode.SYSTEM_ERROR,
                                        str(e))

        self._logger.debug(
            "VM create, done touching timestamp file, vm-id: %s" % vm.id)

        vm.datastore = datastore_id
        vm.datastore_name = \
            self.hypervisor.datastore_manager.datastore_name(datastore_id)
        response = CreateVmResponse()
        response.result = CreateVmResultCode.OK
        response.vm = vm.to_thrift()

        return response

    def _find_datastore_by_image(self, image_id):
        image_datastores = self.hypervisor.datastore_manager.image_datastores()
        if not image_datastores:
            return None

        for image_ds in image_datastores:
            if self.hypervisor.image_manager.check_and_validate_image(
                    image_id, image_ds):
                return image_ds

        self._logger.warning("Failed to find image %s in all datastores %s." %
                             (image_id, image_datastores))
        return list(image_datastores)[0]

    @log_duration
    def _touch_image_timestamp(self, vm_id, ds_id, image_id):
        """
        This methods calls into the ImageManager to update
        the mod time on an existing image timestamp file
        :param vm_id: id of the vm being created,
        :param ds_id: id of the target datastore,
        :param image_id: id of the target image
        :return: CreateVm Response
        """
        rc = CreateVmResultCode
        dm = self.hypervisor.datastore_manager
        try:
            ds_id = dm.normalize(ds_id)
        except DatastoreNotFoundException as e:
            self._logger.exception("Datastore %s not found" % ds_id)
            return CreateVmResponse(rc.SYSTEM_ERROR, str(e))

        im = self.hypervisor.image_manager
        try:
            im.touch_image_timestamp(ds_id, image_id)
            return CreateVmResponse(rc.OK)
        except InvalidImageState:
            # tombstone
            self._logger.debug(sys.exc_info()[1])
            return CreateVmResponse(rc.IMAGE_TOMBSTONED,
                                    "%s has been tombstoned" % image_id)
        except Exception as e:
            self._logger.exception("Failed to touch image timestamp %s, vm %s"
                                   % (e, vm_id))
            return CreateVmResponse(rc.IMAGE_NOT_FOUND, str(e))

    @log_request
    @error_handler(DeleteVmResponse, DeleteVmResultCode)
    @lock_vm
    def delete_vm(self, request):
        """Delete a VM.

        :type request: DeleteVmRequest
        :rtype: DeleteVmResponse
        """
        rc = DeleteVmResultCode
        response = DeleteVmResponse(rc.OK)

        try:
            self.hypervisor.vm_manager.delete_vm(request.vm_id,
                                                 force=request.force)
            return DeleteVmResponse(rc.OK)
        except ValueError:
            return self._error_response(
                rc.SYSTEM_ERROR, "Invalid VM ID: %s" % request.vm_id, response)
        except VmNotFoundException:
            return self._error_response(
                rc.VM_NOT_FOUND, "VM %s not found" % request.vm_id, response)
        except OperationNotAllowedException as e:
            return self._error_response(
                rc.OPERATION_NOT_ALLOWED,
                "VM %s cannot be deleted, %s" % (request.vm_id, str(e)),
                response)
        except VmPowerStateException:
            return self._error_response(
                rc.VM_NOT_POWERED_OFF,
                "VM %s not powered off" % request.vm_id,
                response)

    @log_request
    @error_handler(GetResourcesResponse, GetResourcesResultCode)
    def get_resources(self, request):
        """Return VM state.

        :type request: GetResourcesRequest
        :rtype: GetResourcesResponse
        """
        resources = []

        if request.locators:
            for locator in request.locators:
                if locator.vm:
                    try:
                        resource = self.hypervisor.get_vm_resource(
                            locator.vm.id)
                    except VmNotFoundException:
                        continue
                elif locator.disk:
                    try:
                        resource = self.hypervisor.disk_manager.get_resource(
                            locator.disk.id)
                    except DiskNotFoundException:
                        continue

                resources.append(resource.to_thrift())
        else:
            for resource in self.hypervisor.get_resources():
                resources.append(resource.to_thrift())

        return GetResourcesResponse(GetResourcesResultCode.OK,
                                    resources=resources)

    @log_request
    @error_handler(PowerVmOpResponse, PowerVmOpResultCode)
    @lock_vm
    def power_vm_op(self, request):
        """Power VM operation.

        :type request: PowerVmOpRequest
        :rtype: PowerVmOpResponse
        """
        response = PowerVmOpResponse()

        mode = common.services.get(ServiceName.MODE).get_mode()

        if mode == MODE.ENTERING_MAINTENANCE or mode == MODE.MAINTENANCE:
            return self._error_response(
                PowerVmOpResultCode.OPERATION_NOT_ALLOWED,
                "power_vm_op not allowed in {0} mode".format(mode.name),
                response)

        try:
            if request.op is PowerVmOp.ON:
                self.hypervisor.vm_manager.power_on_vm(request.vm_id)
            elif request.op is PowerVmOp.OFF:
                self.hypervisor.vm_manager.power_off_vm(request.vm_id)
            elif request.op is PowerVmOp.RESET:
                self.hypervisor.vm_manager.reset_vm(request.vm_id)
            elif request.op is PowerVmOp.SUSPEND:
                self.hypervisor.vm_manager.suspend_vm(request.vm_id)
            elif request.op is PowerVmOp.RESUME:
                self.hypervisor.vm_manager.resume_vm(request.vm_id)
            else:
                raise ValueError("Unknown power op: %s" % request.op)

            response.result = PowerVmOpResultCode.OK
            return response
        except VmNotFoundException:
            return self._error_response(
                PowerVmOpResultCode.VM_NOT_FOUND,
                "VM %s not found" % request.vm_id,
                response)
        except VmPowerStateException, e:
            return self._error_response(
                PowerVmOpResultCode.INVALID_VM_POWER_STATE,
                str(e),
                response)

    @log_request
    @error_handler(CreateDisksResponse, CreateDisksResultCode)
    def create_disks(self, request):
        """Create disks.

        :type request: CreateDisksRequest
        :rtype: CreateDisksResponse
        """
        response = CreateDisksResponse(disks=[], disk_errors={})

        dm = self.hypervisor.disk_manager
        pm = self.hypervisor.placement_manager
        try:
            disks = pm.consume_disk_reservation(request.reservation)
        except InvalidReservationException:
            return CreateDisksResponse(
                CreateDisksResultCode.INVALID_RESERVATION,
                "Invalid VM reservation")

        for disk in disks:
            thrift_disk = disk.to_thrift()
            response.disks.append(thrift_disk)

            disk_error = CreateDiskError()
            response.disk_errors[disk.id] = disk_error

            cow = False
            if disk.image:
                cow = disk.image.clone_type == CloneType.COPY_ON_WRITE

                if disk.image.id is None:
                    disk_error.result = CreateDiskResultCode.SYSTEM_ERROR
                    disk_error.error = "Invalid image id"
                    continue

            try:
                datastore = self._select_datastore_for_disk_create(disk)
                if disk.image is None:
                    dm.create_disk(datastore, disk.id, disk.capacity_gb)
                else:
                    if cow:
                        # Linked clone
                        # dm.create_child_disk(datastore, source, target)
                        disk_error.result = CreateDiskResultCode.SYSTEM_ERROR
                        disk_error.error = "Not Implemented"
                        continue
                    else:
                        # Full clone
                        dm.copy_disk(datastore, disk.image.id, datastore,
                                     disk.id)
                datastore_name = \
                    self.hypervisor.datastore_manager.datastore_name(datastore)
                thrift_disk.datastore = Datastore(id=datastore,
                                                  name=datastore_name)
                disk_error.result = CreateDiskResultCode.OK
            except MissingPlacementDescriptorException, e:
                self._logger.error("Cannot create Disk as reservation is "
                                   "missing placement information, %s" %
                                   disk.id)
                disk_error.result = CreateDiskResultCode.PLACEMENT_NOT_FOUND
                disk_error.error = type(e).__name__
                continue
            except Exception, e:
                self._logger.error("Unexpected exception %s, %s" %
                                   (disk.id, e), exc_info=True)
                disk_error.result = CreateDiskResultCode.SYSTEM_ERROR
                disk_error.error = type(e).__name__
                continue

        response.result = CreateDisksResultCode.OK
        pm.remove_disk_reservation(request.reservation)
        return response

    @log_duration
    def _update_disks(self, spec, info, disks, method):
        """Attach or Detach disks.

        :type spec: vim.Vm.ConfigSpec
        :type info: vim.Vm.ConfigInfo, none for new VMs otherwise the VMs
                    current config
        :type disks list of AttachedDisk
        :type method: function
        """
        if disks is None:
            return
        for disk in disks:
            disk_id = disk.id if hasattr(disk, "id") else disk
            datastore = self._datastore_for_disk(disk_id)
            method(spec, datastore, disk_id, info)

    @log_duration
    def _create_disks(self, spec, datastore, disks):
        """Update vm spec to Create and attach disks.

        :type spec: vim.Vm.ConfigSpec
        :type datastore: id of datastore VM will be in
        :type disks list of Disks
        """
        if disks is None:
            return

        vm_manager = self.hypervisor.vm_manager

        for disk in disks:
            cow = False

            if disk.image:
                cow = disk.image.clone_type == CloneType.COPY_ON_WRITE

                if disk.image.id is None:
                    self._logger.warning("Image id not found %s" %
                                         disk.image.id)
                    raise ImageNotFoundException(disk.image.id)

            try:
                if disk.image is None:
                    vm_manager.create_empty_disk(spec, datastore, disk.id,
                                                 disk.capacity_gb * 1024)
                else:
                    if cow:
                        vm_manager.create_child_disk(spec, datastore, disk.id,
                                                     disk.image.id)
                    # else full clone: ignore.
            except Exception, e:
                self._logger.warning("Unexpected exception %s" % (e),
                                     exc_info=True)
                raise e

    @log_request
    @error_handler(DeleteDisksResponse, DeleteDisksResultCode)
    def delete_disks(self, request):
        """Delete disks.

        :type request: DeleteDisksRequest
        :rtype: DeleteDisksResponse
        """
        response = DeleteDisksResponse(disk_errors={})

        for disk_id in request.disk_ids:
            disk_error = DeleteDiskError()
            response.disk_errors[disk_id] = disk_error

            datastore = self._datastore_for_disk(disk_id)
            try:
                self.hypervisor.disk_manager.delete_disk(datastore, disk_id)
                disk_error.result = DeleteDiskResultCode.OK
            except Exception, e:
                self._logger.warning("Unexpected exception %s" % (e),
                                     exc_info=True)
                disk_error.result = DeleteDiskResultCode.SYSTEM_ERROR
                disk_error.error = str(e)
                continue

        response.result = DeleteDisksResultCode.OK
        return response

    @log_request
    @error_handler(VmDisksOpResponse, VmDiskOpResultCode)
    @lock_vm
    def attach_disks(self, request):
        response = VmDisksOpResponse(disks=[], disk_errors={})
        response.result = VmDiskOpResultCode.OK
        self._update_disks_with_response(
            request.vm_id, request.disk_ids,
            response, self.hypervisor.vm_manager.add_disk)
        return response

    @log_request
    @error_handler(VmDisksOpResponse, VmDiskOpResultCode)
    @lock_vm
    def detach_disks(self, request):
        response = VmDisksOpResponse(disks=[], disk_errors={})
        response.result = VmDiskOpResultCode.OK
        self._update_disks_with_response(
            request.vm_id, request.disk_ids,
            response, self.hypervisor.vm_manager.remove_disk)
        return response

    def _update_disks_with_response(self, vm_id, disks, response, method):
        """Attach or Detach disks and report per disk error

        :type vm_id: str
        :type disks list of AttachedDisk
        :type per disk response
        :type method: function
        """
        if disks is None:
            return

        try:
            vm = self.hypervisor.vm_manager.get_resource(vm_id)
            if vm.state == State.SUSPENDED:
                response.result = VmDiskOpResultCode.INVALID_VM_POWER_STATE
                response.error = "Vm in suspended state"
                return

        except VmNotFoundException, e:
            self._logger.warning(
                "_update_disks_with_response: %s" % (e),
                exc_info=True)
            response.result = VmDiskOpResultCode.VM_NOT_FOUND
            response.error = str(e)
            return

        for disk in disks:
            try:
                disk_id = disk.id if hasattr(disk, "id") else disk

                disk_error = CreateDiskError()
                response.disk_errors[disk_id] = disk_error
                # get resource
                disk_resource = \
                    self.hypervisor.disk_manager.get_resource(disk_id)
                thrift_disk = disk_resource.to_thrift()
                response.disks.append(thrift_disk)
                datastore = disk_resource.datastore

                info = self.hypervisor.vm_manager.get_vm_config(vm_id)
                spec = self.hypervisor.vm_manager.update_vm_spec()
                method(spec, datastore, disk_id, info)
                self.hypervisor.vm_manager.update_vm(vm_id, spec)
                disk_error.result = VmDiskOpResultCode.OK
            except DiskNotFoundException, e:
                self._logger.warning(
                    "_update_disks_with_response %s" % (e),
                    exc_info=True)
                response.result = VmDiskOpResultCode.DISK_NOT_FOUND
                disk_error.result = VmDiskOpResultCode.DISK_NOT_FOUND
                disk_error.error = str(e)
                continue
            except ValueError, e:
                self._logger.warning(
                    "_update_disks_with_response %s" % (e),
                    exc_info=True)
                remove_method = self.hypervisor.vm_manager.remove_disk
                if (method == remove_method and str(e) == 'ENOENT'):
                    response.result = VmDiskOpResultCode.DISK_DETACHED
                    disk_error.result = VmDiskOpResultCode.DISK_DETACHED
                    disk_error.error = "Disk not attached"
                else:
                    response.result = VmDiskOpResultCode.SYSTEM_ERROR
                    disk_error.result = VmDiskOpResultCode.SYSTEM_ERROR
                    disk_error.error = str(e)
                continue
            except Exception, e:
                self._logger.warning(
                    "_update_disks_with_response %s" % (e),
                    exc_info=True)
                response.result = VmDiskOpResultCode.SYSTEM_ERROR
                disk_error.result = VmDiskOpResultCode.SYSTEM_ERROR
                disk_error.error = str(e)
                continue

    @log_request
    @error_handler(CopyImageResponse, CopyImageResultCode)
    def copy_image(self, request):
        """Copy image between datastores.

        :type request: CopyImageRequest
        :rtype: CopyImageResponse
        """
        src_image = request.source
        dst_image = request.destination
        im = self.hypervisor.image_manager
        dm = self.hypervisor.datastore_manager

        try:
            src_ds_id = dm.normalize(src_image.datastore.id)
        except DatastoreNotFoundException as e:
            self._logger.exception("Datastore %s not found" %
                                   src_image.datastore.id)
            return DeleteVmResponse(CopyImageResultCode.SYSTEM_ERROR, str(e))
        try:
            dst_ds_id = dm.normalize(dst_image.datastore.id)
        except DatastoreNotFoundException as e:
            self._logger.exception("Datastore %s not found" %
                                   dst_image.datastore.id)
            return DeleteVmResponse(CopyImageResultCode.SYSTEM_ERROR, str(e))

        if src_ds_id == dst_ds_id and src_image.id == dst_image.id:
            return CopyImageResponse(result=CopyImageResultCode.OK)

        if not im.check_image(src_image.id, src_ds_id):
            return CopyImageResponse(
                result=CopyImageResultCode.IMAGE_NOT_FOUND)

        try:
            im.copy_image(
                src_ds_id,
                src_image.id,
                dst_ds_id,
                dst_image.id
            )
        except DiskAlreadyExistException as e:
            return CopyImageResponse(
                result=CopyImageResultCode.DESTINATION_ALREADY_EXIST)
        except Exception as e:
            self._logger.exception("Unexpected exception %s" % (e))
            return self._error_response(
                CopyImageResultCode.SYSTEM_ERROR,
                "Copy image %s->%s error: %s" % (src_image.id, dst_image.id,
                                                 e),
                CopyImageResponse())

        return CopyImageResponse(result=CopyImageResultCode.OK)

    @log_request
    @error_handler(ImageInfoResponse, ImageInfoResultCode)
    def get_image_info(self, request):
        """Get image info for an image in specific datastore

        :type request: ImageInfoRequest
        :rtype: ImageInfoResponse
        """
        im = self.hypervisor.image_manager
        dm = self.hypervisor.datastore_manager

        try:
            datastore_id = dm.normalize(request.datastore_id)
        except DatastoreNotFoundException:
            return ImageInfoResponse(
                result=ImageInfoResultCode.DATASTORE_NOT_FOUND)

        if not im.check_image(request.image_id, datastore_id):
            return ImageInfoResponse(
                result=ImageInfoResultCode.IMAGE_NOT_FOUND)

        image_path = im.get_image_path(datastore_id, request.image_id)
        ctime = os.stat(image_path).st_ctime
        created_time = datetime.datetime.fromtimestamp(ctime).isoformat('T')

        try:
            image_type, replication = im.get_image_manifest(request.image_id)
        except Exception as e:
            self._logger.warning("Cannot get type and replication",
                                 exc_info=True)
            return ImageInfoResponse(
                result=ImageInfoResultCode.SYSTEM_ERROR,
                error=str(e)
            )

        try:
            image_dir = os.path.dirname(image_path)
            timestamp_exists, timestamp_mod_time = \
                im.get_timestamp_mod_time_from_dir(image_dir)
            tombstone_exists, tombstone_mod_time = \
                im.get_tombstone_mod_time_from_dir(image_dir)

            if not timestamp_exists:
                # Try the renamed timestamp file
                timestamp_exists, timestamp_mod_time = \
                    im.get_timestamp_mod_time_from_dir(image_dir, True)

            if timestamp_exists:
                last_updated_time = \
                    datetime.datetime.fromtimestamp(
                        timestamp_mod_time).isoformat('T')
            else:
                last_updated_time = "unknown"

            return ImageInfoResponse(
                result=ImageInfoResultCode.OK,
                image_info=ImageInfo(
                    type=image_type,
                    replication=replication,
                    last_updated_time=last_updated_time,
                    created_time=created_time,
                    tombstone=tombstone_exists,
                    ref_count=0,
                    vm_ids=[]
                )
            )
        except Exception as e:
            self._logger.warning("Unexpected exception %s" % (e),
                                 exc_info=True)
            return ImageInfoResponse(
                result=ImageInfoResultCode.SYSTEM_ERROR,
                error=str(e)
            )

    @log_request
    @error_handler(DeleteImageResponse, DeleteImageResultCode)
    def delete_image(self, request):
        """Delete an image from datastore

        :param request: DeleteImageRequest
        :return: DeleteImageResponse
        """

        image = request.image
        im = self.hypervisor.image_manager
        datastore_id = self.hypervisor.datastore_manager.normalize(
            image.datastore.id)
        ds_type = self.hypervisor.datastore_manager.datastore_type(
            datastore_id)
        try:
            im.delete_image(datastore_id, image.id,
                            ds_type, request.force)

        except ImageNotFoundException as e:
            return self._error_response(
                DeleteImageResultCode.IMAGE_NOT_FOUND,
                "Delete image %s error: %s" % (image.id, e),
                DeleteImageResponse())

        except ImageInUse as e:
            return self._error_response(
                DeleteImageResultCode.IMAGE_IN_USE,
                "Delete image %s error: %s" % (image.id, e),
                DeleteImageResponse())

        except Exception as e:
            return self._error_response(
                DeleteImageResultCode.SYSTEM_ERROR,
                "Delete image %s error: %s" % (image.id, e),
                DeleteImageResponse())

        return DeleteImageResponse(result=DeleteImageResultCode.OK)

    @log_request
    @error_handler(GetImagesResponse, GetImagesResultCode)
    def get_images(self, request):
        """ Get image list from datastore

        :param request: GetImagesRequest
        :return: GetImagesResponse
        """
        try:
            image_ids = self.hypervisor.image_manager.get_images(
                request.datastore_id)
        except DatastoreNotFoundException:
            return self._error_response(
                GetImagesResultCode.DATASTORE_NOT_FOUND,
                "Datastore not found",
                GetImagesResponse())

        return GetImagesResponse(GetImagesResultCode.OK, image_ids=image_ids)

    @log_request
    @error_handler(StartImageScanResponse, StartImageOperationResultCode)
    def start_image_scan(self, request):
        """ Start image scan to locate unused images

        :param request: StartImageScanRequest
        :return: StartImageScanResponse
        """
        try:
            self._logger.info("Starting image scan on: %s" %
                              request.datastore_id)
            image_scanner = self._hypervisor.image_monitor.\
                get_image_scanner(request.datastore_id)
            image_scanner.start(request.timeout,
                                request.scan_rate,
                                request.scan_rate)
        except DatastoreNotFoundException:
            return self._error_response(
                StartImageOperationResultCode.DATASTORE_NOT_FOUND,
                "Datastore not found",
                StartImageScanResponse())
        except TaskAlreadyRunning:
            return self._error_response(
                StartImageOperationResultCode.SCAN_IN_PROGRESS,
                "Scan in Progress",
                StartImageScanResponse())

        return StartImageScanResponse(StartImageOperationResultCode.OK)

    @log_request
    @error_handler(StopImageOperationResponse, StopImageOperationResultCode)
    def stop_image_scan(self, request):
        """ Stop image scan to remove unused images

        :param request: StopImageOperationRequest
        :return: StopImageOperationResponse
        """
        try:
            self._logger.info("Stopping image scanner on: %s" %
                              request.datastore_id)
            image_sweeper = self.hypervisor.image_monitor.\
                get_image_scanner(request.datastore_id)

            image_sweeper.stop()
        except DatastoreNotFoundException:
            return self._error_response(
                StopImageOperationResultCode.DATASTORE_NOT_FOUND,
                "Datastore not found",
                StopImageOperationResponse())

        return StopImageOperationResponse(StopImageOperationResultCode.OK)

    @log_request
    @error_handler(StartImageSweepResponse, StartImageOperationResultCode)
    def start_image_sweep(self, request):
        """ Start image sweep to remove unused images

        :param request: StartImageSweepRequest
        :return: StartImageSweepResponse
        """
        try:
            self._logger.info("Starting image sweeper on: %s" %
                              request.datastore_id)
            image_sweeper = self.hypervisor.image_monitor.\
                get_image_sweeper(request.datastore_id)
            # Copy image list from the request
            image_list = list()
            for image_desc in request.image_descs:
                image_list.append(image_desc.image_id)
            image_sweeper.start(image_list,
                                request.timeout,
                                request.sweep_rate,
                                request.grace_period)
        except DatastoreNotFoundException:
            return self._error_response(
                StartImageOperationResultCode.DATASTORE_NOT_FOUND,
                "Datastore not found",
                StartImageSweepResponse())
        except TaskAlreadyRunning:
            return self._error_response(
                StartImageOperationResultCode.SWEEP_IN_PROGRESS,
                "Scan in Progress",
                StartImageSweepResponse())

        return StartImageSweepResponse(StartImageOperationResultCode.OK)

    @log_request
    @error_handler(StartImageSweepResponse, StopImageOperationResultCode)
    def stop_image_sweep(self, request):
        """ Stop image sweep to remove unused images

        :param request: StopImageOperationRequest
        :return: StopImageOperationResponse
        """
        try:
            self._logger.info("Stopping image sweeper on: %s" %
                              request.datastore_id)
            image_sweeper = self.hypervisor.image_monitor.\
                get_image_sweeper(request.datastore_id)

            image_sweeper.stop()
        except DatastoreNotFoundException:
            return self._error_response(
                StopImageOperationResultCode.DATASTORE_NOT_FOUND,
                "Datastore not found",
                StopImageOperationResponse())

        return StopImageOperationResponse(StopImageOperationResultCode.OK)

    @error_handler(GetInactiveImagesResponse, GetMonitoredImagesResultCode)
    def get_inactive_images(self, request):
        """ Get unused images

        :param request: GetInactiveImagesRequest
        :return: GetInactiveImagesResponse
        """
        self._logger.info("Call to get_inactive_images")
        try:
            image_scanner = self._hypervisor.image_monitor.\
                get_image_scanner(request.datastore_id)
            response = GetInactiveImagesResponse()

            datastore_info = self._hypervisor.\
                datastore_manager.\
                datastore_info(request.datastore_id)

            response.totalMB = long(datastore_info.total)
            response.usedMB = long(datastore_info.used)

            self._logger.info("Datastore info, total: %d, used: %d" %
                              (datastore_info.total, datastore_info.used))

            # Get scanner state
            state = image_scanner.get_state()

            if state is not DatastoreImageScanner.State.IDLE:
                response.result = \
                    GetMonitoredImagesResultCode.OPERATION_IN_PROGRESS
                return response
            else:
                response.result = \
                    GetMonitoredImagesResultCode.OK

            # Get the list of images
            unused_images, end_time = \
                image_scanner.get_unused_images()

            self._create_unused_image_descriptors(
                response,
                unused_images)

            self._logger.info("Found %d inactive images" %
                              len(response.image_descs))
            return response

        except DatastoreNotFoundException:
            return self._error_response(
                GetMonitoredImagesResultCode.DATASTORE_NOT_FOUND,
                "Datastore not found",
                GetInactiveImagesResponse())

    def _create_unused_image_descriptors(self, response, images):
        # Get the list of ids
        response.image_descs = list()
        if not images:
            return
        ids = images.keys()
        ids.sort()
        for image_id in ids:
            image_descriptor = InactiveImageDescriptor()
            image_descriptor.image_id = image_id
            try:
                _, mod_time = self.hypervisor.image_manager.\
                    get_timestamp_mod_time_from_dir(images[image_id])
                image_descriptor.timestamp = long(mod_time)
            except Exception as ex:
                image_descriptor.timestamp = 0
                self._logger.exception(
                    "Unable to get mod time for %s, ex: %s" %
                    (images[image_id], ex))
            response.image_descs.append(image_descriptor)

    @log_request
    @error_handler(GetDeletedImagesResponse, GetMonitoredImagesResultCode)
    def get_deleted_images(self, request):
        """ Get deleted images

        :param request: GetDeletedImagesRequest
        :return: GetDeletedImagesResponse
        """
        self._logger.info("Call to get_deleted_images")
        try:
            image_sweeper = self.hypervisor.image_monitor.\
                get_image_sweeper(request.datastore_id)
            response = GetDeletedImagesResponse()

            if image_sweeper.get_state() is not \
                    DatastoreImageSweeper.State.IDLE:
                response.result = \
                    GetMonitoredImagesResultCode.OPERATION_IN_PROGRESS
            else:
                response.result = GetMonitoredImagesResultCode.OK

            image_ids, end_time = image_sweeper.get_deleted_images()
            response.image_descs = list()

            # Copy image list from the request
            for image_id in image_ids:
                image_descriptor = InactiveImageDescriptor()
                image_descriptor.image_id = image_id
                image_descriptor.timestamp = 0
                response.image_descs.append(image_descriptor)

            self._logger.info("Found %d deleted images" %
                              len(response.image_descs))
            return response
        except DatastoreNotFoundException:
            return self._error_response(
                GetMonitoredImagesResultCode.DATASTORE_NOT_FOUND,
                "Datastore not found",
                GetDeletedImagesResponse())

    @log_request
    @error_handler(FindResponse, FindResultCode)
    def find(self, request):
        """Find requested resource.

        :type request: FindRequest
        :rtype: FindResponse
        """
        if request.locator.disk:
            datastore_id = self.hypervisor.disk_manager.get_datastore(
                request.locator.disk.id)
            if datastore_id:
                datastore_name = \
                    self.hypervisor.datastore_manager.datastore_name(
                        datastore_id)
                return FindResponse(FindResultCode.OK,
                                    agent_id=self._agent_id,
                                    datastore=Datastore(id=datastore_id,
                                                        name=datastore_name),
                                    address=self._address)
        elif request.locator.vm:
            found = self.hypervisor.vm_manager.has_vm(request.locator.vm.id)
            if found:
                try:
                    config = self.hypervisor.vm_manager.get_vm_config(
                        request.locator.vm.id)
                    path = self.hypervisor.vm_manager.get_vm_path(config)
                    datastore_id = self.hypervisor.vm_manager.get_vm_datastore(
                        config)
                    datastore_name = \
                        self.hypervisor.datastore_manager.datastore_name(
                            datastore_id)
                except Exception, e:
                    self._logger.warning("failed to get vm information")
                    self._logger.exception(e)
                    return FindResponse(FindResponse.MISSING_DETAILS,
                                        agent_id=self._agent_id)

                return FindResponse(FindResultCode.OK,
                                    agent_id=self._agent_id,
                                    datastore=Datastore(id=datastore_id,
                                                        name=datastore_name),
                                    path=path, address=self._address)

        return FindResponse(FindResultCode.NOT_FOUND)

    @log_request
    @error_handler(PlaceResponse, PlaceResultCode)
    def place(self, request):
        """Place requested resource.

        :type request: PlaceRequest
        :rtype: PlaceResponse
        """
        mode = common.services.get(ServiceName.MODE).get_mode()
        if mode != MODE.NORMAL:
            self._logger.info("return INVALID_STATE when agent in %s" %
                              mode)
            return PlaceResponse(PlaceResultCode.INVALID_STATE,
                                 error="Host in %s mode" % mode)

        try:
            disks, vm = self._parse_resource_request(request.resource)
            score, placement_list = \
                self.hypervisor.placement_manager.place(vm, disks)
            thrift_score = Score(score.utilization, score.transfer)
            placement_list = \
                AgentResourcePlacementList(placement_list)
            thrift_placement_list = placement_list.to_thrift()

            return PlaceResponse(PlaceResultCode.OK,
                                 agent_id=self._agent_id,
                                 score=thrift_score,
                                 generation=self._generation,
                                 placementList=thrift_placement_list,
                                 address=self._address)
        except NoSuchResourceException as e:
            self._logger.warning(
                "NoSuchResourceException during place(). {0}".format(e))
            return PlaceResponse(PlaceResultCode.NO_SUCH_RESOURCE,
                                 agent_id=self._agent_id)

        except NotEnoughMemoryResourceException:
            self._logger.warning(
                "NotEnoughMemoryResourceException during place()")
            return PlaceResponse(PlaceResultCode.NOT_ENOUGH_MEMORY_RESOURCE,
                                 agent_id=self._agent_id)

        except NotEnoughCpuResourceException:
            self._logger.warning(
                "NotEnoughCpuResourceException during place()")
            return PlaceResponse(PlaceResultCode.NOT_ENOUGH_CPU_RESOURCE,
                                 agent_id=self._agent_id)

        except NotEnoughDatastoreCapacityException:
            self._logger.warning(
                "NotEnoughDatastoreCapacityException during place()")
            return PlaceResponse(PlaceResultCode.NOT_ENOUGH_DATASTORE_CAPACITY,
                                 agent_id=self._agent_id)
        except UnknownFlavor:
            self._logger.warning("UnknownFlavor exception during place()")
            return PlaceResponse(PlaceResultCode.NO_SUCH_RESOURCE,
                                 error="Unknown flavor",
                                 agent_id=self._agent_id)

    def _error_response(self, code, error, response):
        """Attach error information to response.

        :type code: int
        :type error: str or Exception
        :type response: object
        :rtype: object
        """
        self._logger.debug(error)
        response.result = code
        response.error = str(error)
        return response

    def _datastore_for_disk(self, disk_id):
        """Select data store id for disk creation.
        :type string (disk id)
        :rtype string (datastore id)
        """
        return self.hypervisor.disk_manager.get_datastore(disk_id)

    def _select_freest_datastore(self, datastores):
        if len(datastores) is 0:
            return None
        if len(datastores) is 1:
            return datastores[0]
        return sorted(datastores, key=self._datastore_freespace)[-1]

    """
    Datastore selection is now performed during the
    placement phase. The placement descriptor is
    returned during the placement phase and forwarded
    by the FE.
    """
    def _select_datastore_for_disk_create(self, disk):
        if not disk.placement:
            """
            If placement information is missing return
            an error. The FE should retry placement.
            """
            raise MissingPlacementDescriptorException()

        return disk.placement.container_id

    def _parse_resource_request(self, resource):
        vm = None
        disks = []
        if hasattr(resource, 'placement_list'):
            placement_list = AgentResourcePlacementList.\
                from_thrift(resource.placement_list)
            self._logger.info(placement_list)
        else:
            placement_list = None

        if resource.vm:
            vm = Vm.from_thrift(resource.vm)
            AgentResourcePlacementList.\
                unpack_placement_list_vm(placement_list, vm)

        if resource.disks:
            for thrift_disk in resource.disks:
                disk = Disk.from_thrift(thrift_disk)
                if disk.constraints:
                    self._logger.info(disk.constraints)
                disks.append(disk)
            AgentResourcePlacementList.\
                unpack_placement_list_disks(placement_list, disks)

        return disks, vm

    @log_request
    @error_handler(GetVmNetworkResponse, GetVmNetworkResultCode)
    def get_vm_networks(self, request):
        """
        Get the VM's network info
        :type request: GetVmNetworkRequest the vm network request.
        :rtype: GetVmNetworkResponse, the vms network config information.
        """

        response = GetVmNetworkResponse()
        try:
            response.network_info = \
                self.hypervisor.vm_manager.get_vm_network(request.vm_id)
            response.result = GetVmNetworkResultCode.OK
            return response
        except VmNotFoundException:
            return self._error_response(GetVmNetworkResultCode.VM_NOT_FOUND,
                                        "Vm %s not found" % request.vm_id,
                                        response)
        except:
            return self._error_response(GetVmNetworkResultCode.SYSTEM_ERROR,
                                        str(sys.exc_info()[1]),
                                        response)

    @log_request
    @error_handler(AttachISOResponse, AttachISOResultCode)
    @lock_vm
    def attach_iso(self, request):
        """
        Attach an ISO to a VirtualMachine.
        :type request: AttachISORequest
        :rtype AttachISORespose
        """
        spec = self.hypervisor.vm_manager.update_vm_spec()

        try:
            response = AttachISOResponse()

            # callee will modify spec
            # result: True if success, or False if fail
            result = self.hypervisor.vm_manager.attach_cdrom(
                spec,
                request.iso_file_path,
                request.vm_id)

            if result:
                self.hypervisor.vm_manager.update_vm(
                    request.vm_id,
                    spec)
                response.result = AttachISOResultCode.OK
            else:
                response.result = AttachISOResultCode.ISO_ATTACHED_ERROR

        except VmNotFoundException:
            response.result = AttachISOResultCode.VM_NOT_FOUND
        except TypeError:
            self._logger.info(sys.exc_info()[1])
            response.result = AttachISOResultCode.SYSTEM_ERROR
            response.error = str(sys.exc_info()[1])
        except Exception:
            self._logger.info(sys.exc_info()[1])
            response.result = AttachISOResultCode.SYSTEM_ERROR
            response.error = str(sys.exc_info()[1])

        return response

    @log_request
    @error_handler(DetachISOResponse, DetachISOResultCode)
    @lock_vm
    def detach_iso(self, request):
        """
        Detach an ISO to a VirtualMachine.
        :type request: DetachISORequest
        :rtype DetachISORespose
        """

        # TODO(vui) Until we properly track iso use, detaching an iso while not
        # deleting it is going leak the iso file in the file system. So we
        # mandate that the iso be always deleted in the process for now.
        if not request.delete_file:
            raise NotImplementedError()

        response = DetachISOResponse()

        try:
            spec = self.hypervisor.vm_manager.update_vm_spec()
            iso_path = self.hypervisor.vm_manager.disconnect_cdrom(
                spec, request.vm_id)
            self.hypervisor.vm_manager.update_vm(request.vm_id, spec)
        except VmNotFoundException:
            return self._error_response(
                DetachISOResultCode.VM_NOT_FOUND,
                "VM %s not found" % request.vm_id,
                response)
        except IsoNotAttachedException:
            return self._error_response(
                DetachISOResultCode.ISO_NOT_ATTACHED,
                "No ISO is attached to VM %s" % request.vm_id,
                response)
        except Exception:
            self._logger.info(sys.exc_info()[1])
            return self._error_response(
                DetachISOResultCode.SYSTEM_ERROR,
                str(sys.exc_info()[1]),
                response)

        try:
            if request.delete_file:
                self._logger.info("Deleting iso file %s" % iso_path)
                self.hypervisor.vm_manager.remove_iso(iso_path)
        except Exception:
            return self._error_response(
                DetachISOResultCode.CANNOT_DELETE,
                "Iso %s detached from VM, but not deleted" % iso_path,
                response)

        response.result = DetachISOResultCode.OK
        return response

    @log_request
    @error_handler(ServiceTicketResponse, ServiceTicketResultCode)
    def get_service_ticket(self, request):
        """ Get service ticket from host. Currently not NFC service ticket
        is supported. To get NFC service ticket, datastore_name must be
        provied.

        :param request: ServiceTicketRequest
        :return: ServiceTicketResponse
        """
        if request.service_type == ServiceType.NFC:
            if not request.datastore_name:
                return ServiceTicketResponse(
                    ServiceTicketResultCode.BAD_REQUEST,
                    "Missing datastore_name in request")

            try:
                dm = self.hypervisor.datastore_manager
                datastore_name = dm.normalize_to_name(request.datastore_name)
                ticket = dm.datastore_nfc_ticket(datastore_name)
                return ServiceTicketResponse(ServiceTicketResultCode.OK,
                                             ticket=ticket)
            except DatastoreNotFoundException:
                return ServiceTicketResponse(
                    ServiceTicketResultCode.NOT_FOUND,
                    "Datastore %s not found" % request.datastore_name)
        elif request.service_type == ServiceType.VIM:
            ticket = self.hypervisor.acquire_vim_ticket()
            return ServiceTicketResponse(ServiceTicketResultCode.OK,
                                         vim_ticket=ticket)
        else:
            return ServiceTicketResponse(
                ServiceTicketResultCode.BAD_REQUEST,
                "Operation not supported")

    @log_request
    @error_handler(HttpTicketResponse, HttpTicketResultCode)
    def get_http_ticket(self, request):
        """ Get HTTP CGI ticket from host.

        The ticket returned is only for performing the requested HTTP
        operation on the specified URL.

        :param request: HttpTicketRequest
        :return: HttpTicketResponse
        """
        try:
            ticket = self.hypervisor.acquire_cgi_ticket(request.url,
                                                        request.op)
            return HttpTicketResponse(HttpTicketResultCode.OK,
                                      ticket=ticket)
        except:
            return self._error_response(
                HttpTicketResultCode.SYSTEM_ERROR,
                str(sys.exc_info()[1]),
                HttpTicketResponse())

    @log_request
    @error_handler(MksTicketResponse, MksTicketResultCode)
    def get_mks_ticket(self, request):
        """ Get mks ticket for a vm. vm_id must be provided.

        :param request: MksTicketRequest
        :return: MksTicketResponse
        """
        try:
            ticket = self.hypervisor.vm_manager.get_mks_ticket(request.vm_id)
            return MksTicketResponse(result=MksTicketResultCode.OK,
                                     ticket=ticket)
        except VmNotFoundException as e:
            return MksTicketResponse(result=MksTicketResultCode.VM_NOT_FOUND,
                                     error=str(e))
        except OperationNotAllowedException as e:
            return MksTicketResponse(
                result=MksTicketResultCode.INVALID_VM_POWER_STATE,
                error=str(e))

    def get_placement(self, placement_list, resource_id):
        """
        :param resource_placement_list:
        :param resource_id:
        :return: one entry in the placement_list
        """
        return None

    @log_request
    @error_handler(GetDatastoresResponse, GetDatastoresResultCode)
    def get_datastores(self, request):
        response = GetDatastoresResponse()
        response.result = GetDatastoresResultCode.OK
        response.datastores = \
            self._hypervisor.datastore_manager.get_datastores()
        return response

    @log_request
    @error_handler(GetNetworksResponse, GetNetworksResultCode)
    def get_networks(self, request):
        response = GetNetworksResponse()
        response.result = GetNetworksResultCode.OK
        response.networks = self._hypervisor.network_manager.get_networks()
        return response

    @log_request
    @error_handler(CreateImageResponse, CreateImageResultCode)
    def create_image(self, request):
        """
        """
        try:
            datastore_id = self.hypervisor.datastore_manager.normalize(request.datastore)
        except DatastoreNotFoundException:
            return self._error_response(
                CreateImageResultCode.DATASTORE_NOT_FOUND,
                "Datastore not found",
                CreateImageResponse())

        try:
            tmp_image_path = self.hypervisor.image_manager.create_image(datastore_id)
            return CreateImageResponse(CreateImageResultCode.OK, tmp_image_path)
        except:
            return self._error_response(
                CreateImageResultCode.SYSTEM_ERROR,
                str(sys.exc_info()[1]),
                CreateImageResponse())

    @log_request
    @error_handler(FinalizeImageResponse, FinalizeImageResultCode)
    def finalize_image(self, request):
        """ Create an image by atomically moving it from a temp location
            to the specified image_id location.
        """
        try:
            datastore_id = \
                self.hypervisor.datastore_manager.normalize(request.datastore)
        except DatastoreNotFoundException:
            return self._error_response(
                FinalizeImageResultCode.DATASTORE_NOT_FOUND,
                "Datastore not found",
                FinalizeImageResponse())

        try:
            self.hypervisor.image_manager.finalize_image(datastore_id,
                                                         request.tmp_image_path,
                                                         request.image_id)
        except ImageNotFoundException:
            return self._error_response(
                FinalizeImageResultCode.IMAGE_NOT_FOUND,
                "Temp image dir not found",
                FinalizeImageResponse())
        except DiskAlreadyExistException:
            return self._error_response(
                FinalizeImageResultCode.DESTINATION_ALREADY_EXIST,
                "Image disk already exists",
                FinalizeImageResponse())
        except:
            return self._error_response(
                FinalizeImageResultCode.SYSTEM_ERROR,
                str(sys.exc_info()[1]),
                FinalizeImageResponse())

        return FinalizeImageResponse(FinalizeImageResultCode.OK)

    @log_request
    @error_handler(TransferImageResponse, TransferImageResultCode)
    def transfer_image(self, request):
        """ Transfer an image to another host via host-to-host transfer. """
        try:
            self.hypervisor.transfer_image(
                request.source_image_id,
                request.source_datastore_id,
                request.destination_image_id,
                request.destination_datastore_id,
                request.destination_host.host,
                request.destination_host.port)
        except AlreadyLocked:
            return self._error_response(
                TransferImageResultCode.TRANSFER_IN_PROGRESS,
                "Only one image transfer is allowed at any time",
                TransferImageResponse())
        except DiskAlreadyExistException:
            return self._error_response(
                TransferImageResultCode.DESTINATION_ALREADY_EXIST,
                "Image disk already exists",
                TransferImageResponse())
        except Exception as e:
            self._logger.exception(str(e))
            return self._error_response(
                TransferImageResultCode.SYSTEM_ERROR,
                str(sys.exc_info()[1]),
                TransferImageResponse())

        return TransferImageResponse(TransferImageResultCode.OK)

    @log_request
    @error_handler(ReceiveImageResponse, ReceiveImageResultCode)
    def receive_image(self, request):
        """ Receive an image by atomically moving it from a temp location
            to the specified image_id location.
        """
        try:
            datastore_id = self.hypervisor.datastore_manager.normalize(
                request.datastore_id)
            self.hypervisor.receive_image(request.image_id,
                                          datastore_id,
                                          request.transferred_image_id,
                                          request.metadata,
                                          request.manifest)
        except DiskAlreadyExistException:
            return self._error_response(
                ReceiveImageResultCode.DESTINATION_ALREADY_EXIST,
                "Image disk already exists",
                ReceiveImageResponse())
        except:
            return self._error_response(
                ReceiveImageResultCode.SYSTEM_ERROR,
                str(sys.exc_info()[1]),
                ReceiveImageResponse())

        return ReceiveImageResponse(ReceiveImageResultCode.OK)

    @log_request
    @error_handler(CreateImageFromVmResponse, CreateImageFromVmResultCode)
    @lock_vm
    def create_image_from_vm(self, request):
        """ Create an image by cloning it from a VM's disk. """

        vm_mgr = self.hypervisor.vm_manager

        if not vm_mgr.has_vm(request.vm_id):
            return self._error_response(
                CreateImageFromVmResultCode.VM_NOT_FOUND,
                "VM %s not found on host" % request.vm_id,
                CreateImageFromVmResponse())

        if vm_mgr.get_power_state(request.vm_id) != State.STOPPED:
            return self._error_response(
                CreateImageFromVmResultCode.INVALID_VM_POWER_STATE,
                "VM %s not powered off" % request.vm_id,
                CreateImageFromVmResponse())

        linked_clone_os_path = vm_mgr.get_linked_clone_path(request.vm_id)
        if not linked_clone_os_path:
            # TODO(vui) Add image_from_vm support for full cloned VM
            self._logger.info("Child disk not found for vm %s" % request.vm_id)
            raise NotImplementedError()

        try:
            datastore_id = self.hypervisor.datastore_manager.normalize(request.datastore)
        except DatastoreNotFoundException:
            return self._error_response(
                CreateImageFromVmResultCode.SYSTEM_ERROR,
                "Invalid datastore %s" % request.datastore,
                CreateImageFromVmResponse())

        try:
            self.hypervisor.image_manager.create_image_with_vm_disk(
                datastore_id, request.tmp_image_path, request.image_id,
                linked_clone_os_path)
        except DiskAlreadyExistException:
            return self._error_response(
                CreateImageFromVmResultCode.IMAGE_ALREADY_EXIST,
                "Image disk already exists",
                CreateImageFromVmResponse())
        except:
            return self._error_response(
                CreateImageFromVmResultCode.SYSTEM_ERROR,
                str(sys.exc_info()[1]),
                CreateImageFromVmResponse())

        return CreateImageFromVmResponse(CreateImageFromVmResultCode.OK)

    @log_request
    @error_handler(DeleteDirectoryResponse, DeleteDirectoryResultCode)
    def delete_directory(self, request):
        try:
            datastore_id = \
                self.hypervisor.datastore_manager.normalize(request.datastore)
        except DatastoreNotFoundException:
            return self._error_response(
                DeleteDirectoryResultCode.DATASTORE_NOT_FOUND,
                "Datastore %s not found" % request.datastore,
                DeleteDirectoryResponse())

        try:
            self.hypervisor.image_manager.delete_tmp_dir(
                datastore_id, request.directory_path)
        except DirectoryNotFound:
            return self._error_response(
                DeleteDirectoryResultCode.DIRECTORY_NOT_FOUND,
                "Directory %s on datastore %s not found" %
                (request.directory_path, request.datastore),
                DeleteDirectoryResponse())
        except:
            return self._error_response(
                DeleteDirectoryResultCode.SYSTEM_ERROR,
                str(sys.exc_info()[1]),
                DeleteDirectoryResponse())

        return DeleteDirectoryResponse(DeleteDirectoryResultCode.OK)
