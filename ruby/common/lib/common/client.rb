# Copyright 2015 VMware, Inc. All Rights Reserved.
#
# Licensed under the Apache License, Version 2.0 (the "License"); you may not
# use this file except in compliance with the License. You may obtain a copy of
# the License at http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software distributed
# under the License is distributed on an "AS IS" BASIS, without warranties or
# conditions of any kind, EITHER EXPRESS OR IMPLIED. See the License for the
# specific language governing permissions and limitations under the License.

require_relative "api_client/auth_api"
require_relative "api_client/project_api"
require_relative "api_client/resource_ticket_api"
require_relative "api_client/tenant_api"
require_relative "api_client/vm_api"
require_relative "api_client/host_api"
require_relative "api_client/network_api"
require_relative "api_client/storage_api"
require_relative "api_client/import_api"
require_relative "api_client/flavor_api"
require_relative "api_client/image_api"
require_relative "api_client/deployment_api"
require_relative "api_client/cluster_api"
require_relative "api_client/availability_zone_api"

module EsxCloud
  #noinspection RubyUnusedLocalVariable
  class Client

    # @param [Hash] payload
    # @return [Tenant]
    def create_tenant(payload)
    end

    # @param [String] id
    # @return [Boolean]
    def delete_tenant(id)
    end

    # @param [String] name
    # @return [Boolean]
    def delete_tenant_by_name(name)
    end

    # @param [String] id
    # @return [Tenant]
    def find_tenant_by_id(id)
    end

    # @return [TenantList]
    def find_all_tenants
    end

    # @param [String] name
    # @return [TenantList]
    def find_tenants_by_name(name)
    end

    # @return [AuthInfo] auth information
    def get_auth_info
    end

    # @param [String] id
    # @return [TaskList]
    def get_tenant_tasks(id, state = nil)
    end

    # @param [String] id
    # @return [TaskList]
    def get_resource_ticket_tasks(id, state = nil)
    end

    # @param [String] tenant_id
    # @param [Hash] payload
    # @return [ResourceTicket]
    def create_resource_ticket(tenant_id, payload)
    end

    # @param [String] tenant_id
    # @return [ResourceTicketList]
    def find_all_resource_tickets(tenant_id)
    end

    # @param [String] tenant_id
    # @param [String] name
    # @return [ResourceTicketList]
    def find_resource_tickets_by_name(tenant_id, name)
    end

    # @param [String] id
    # @return [ResourceTicket]
    def find_resource_ticket_by_id(id)
    end

    # @param [String] tenant_id
    # @param [Hash] payload
    # @return [Project]
    def create_project(tenant_id, payload)
    end

    # @param [String] id
    # @return [Project]
    def find_project_by_id(id)
    end

    # @param [String] tenant_id
    # @return [ProjectList]
    def find_all_projects(tenant_id)
    end

    # @param [String] name
    # @return [ProjectList]
    def find_projects_by_name(tenant_id, name)
    end

    # @param [String] id
    # @return [Boolean]
    def delete_project(id)
    end

    # @param [String] id
    # @return [VmList]
    def get_project_vms(id)
    end


    # @param [String] id
    # @return [DiskList]
    def get_project_disks(id)
    end

    # @param [String] id
    # @return [TaskList]
    def get_project_tasks(id, state = nil, kind = nil)
    end

    # @param [String] id
    # @return [ClusterList]
    def get_project_clusters(id)
    end

    # @param [String] id
    # @return [VirtualNetworkList]
    def get_project_networks(id)
    end

    # @param [String] id
    # @param [Hash] payload
    def set_project_security_groups(id, payload)
    end

    # @param [String] project_id
    # @param [Hash] payload
    # @return [Cluster]
    def create_cluster(project_id, payload)
    end

    # @param [String] id
    # @return [Cluster]
    def find_cluster_by_id(id)
    end

    # @param [String] id
    # @return [VmList]
    def get_cluster_vms(id)
    end

    # @param [String] id
    # @param [int] new_slave_count
    # @return [Boolean]
    def resize_cluster(id, new_slave_count)
    end

    # @param [String] id
    # @return [Boolean]
    def delete_cluster(id)
    end

    # @param [String] project_id
    # @param [Hash] payload
    # @return [Vm]
    def create_vm(project_id, payload)
    end

    # @param [String] id
    # @return [Boolean]
    def delete_vm(id)
    end

    # @param [String] project_id
    # @return [VmList]
    def find_all_vms(project_id)
    end

    # @param [String] id
    # @return [Vm]
    def find_vm_by_id(id)
    end

    # @param [String] project_id
    # @param [String] name
    # @return [VmList]
    def find_vms_by_name(project_id, name)
    end

    # @param [String] id
    # @param [String] state
    # @return [TaskList]
    def get_vm_tasks(id, state = nil)
    end

    # @param [String] id
    # @return [VmNetworks]
    def get_vm_networks(id)
    end

    # @param [String] id
    # @return [MksTicket]
    def get_vm_mks_ticket(id)
    end

    # @param [String] id
    # @return [Vm]
    def start_vm(id)
    end

    # @param [String] id
    # @return [Vm]
    def stop_vm(id)
    end

    # @param [String] id
    # @return [Vm]
    def restart_vm(id)
    end

    # @param [String] id
    # @return [Vm]
    def resume_vm(id)
    end

    # @param [String] id
    # @return [Vm]
    def suspend_vm(id)
    end

    # @param [String] id
    # @param [String] operation
    # @param [Hash] args
    # @return [Vm]
    def perform_vm_disk_operation(id, operation, disk_id, args = {})
    end

    # @param [String] project_id
    # @param [Hash] payload
    # @return [Disk]
    def create_disk(project_id, payload)
    end

    # @param [String] id
    # @return [Boolean]
    def delete_disk(id)
    end

    # @param [String] project_id
    # @return [DiskList]
    def find_all_disks(project_id)
    end

    # @param [String] id
    # @return [Disk]
    def find_disk_by_id(id)
    end

    # @param [String] project_id
    # @param [String] name
    # @return [DiskList]
    def find_disks_by_name(project_id, name)
    end

    # @param [String] id
    # @param [String] state
    # @return [TaskList]
    def get_disk_tasks(id, state = nil)
    end

    # @param [String] path
    # @param [String] name
    # @return [Image]
    def create_image(path, name = nil, image_replication = nil)
    end

    # @param [String] id
    # @param [Hash] payload
    # @return [Image]
    def create_image_from_vm(id, payload)
    end

    # @param [String] id
    # @return [Image]
    def find_image_by_id(id)
    end

    # @return [ImageList]
    def find_all_images
    end

    # @return [ImageList]
    def find_images_by_name(name)
    end

    # @param [String] id
    # @return [Boolean]
    def delete_image(id)
    end

    # @param [String] id
    # @param [String] state
    # @return [TaskList]
    def get_image_tasks(id, state = nil)
    end

    # @param [Hash] payload
    # @return [Deployment]
    def create_api_deployment(payload)
    end

    # @param [String] id
    # @param [Hash] payload
    # @return [Deployment]
    def deploy_deployment(id, payload = {})
    end

    # @return [DeploymentList]
    def find_all_api_deployments
    end

    # @param [String] deployment_id
    # @return [Boolean]
    def pause_system(deployment_id)
    end

    # @param [String] deployment_id
    # @return [Boolean]
    def pause_background_tasks(deployment_id)
    end

    # @param [String] deployment_id
    # @return [Boolean]
    def resume_system(deployment_id)
    end

    # @param [String] id
    # @return [Deployment]
    def find_deployment_by_id(id)
    end

    # @param [String] id
    # @return [HostList]
    def get_deployment_hosts(id)
    end

    # @param [String] id
    # @return [VmList]
    def get_deployment_vms(id)
    end

    # @param [String] id
    # @return [Hash] security_groups
    def update_security_groups(id, security_groups)
    end

    # @param [Hash] payload
    # @return [Network]
    def create_network(payload)
    end

    # @param [String] id
    # @return [Boolean]
    def delete_network(id)
    end

    # @param [String] id
    # @param [Array<String>] portgroups
    # @return [Network]
    def set_portgroups(id, portgroups)
    end

    # @param [String] id
    # @return [Network]
    def find_network_by_id(id)
    end

    # @param [String] name
    # @return [NetworkList]
    def find_networks_by_name(name)
    end

    # @return [NetworkList]
    def find_all_networks
    end

    # @param [String] id
    # @return [Task]
    def find_task_by_id(id)
    end

    # @param [String] entity_id
    # @param [String] entity_kind
    # @param [String] state
    # @return [TaskList]
    def find_tasks(entity_id = nil, entity_kind = nil, state = nil)
    end

    # @param [String] deployment_id
    # @param [Hash] payload
    # @return [Host]
    def create_host(deployment_id, payload)
    end

    # @param [String] id
    # @return [Host]
    def mgmt_find_host_by_id(id)
    end


    # @param [String] id
    # @return [Metadata]
    def find_host_metadata_by_id(id)
    end

    # @param [String] host_id
    # @return [TaskList]
    def find_tasks_by_host_id(id)
    end

    # @param [String] id
    # @param [String] property
    # @param [String] value
    # @return [Metadata]
    def update_host_metadata_property(id, property, value)
    end

    # @param [String] id
    # @return [Boolean]
    def mgmt_delete_host(id)
    end

    # @param [String] id
    # @return [VmList]
    def mgmt_get_host_vms(id)
    end

    # @param [String] id
    # @return [PortGroup]
    def find_portgroup_by_id(id)
    end

    # @param [String] name
    # @param [String] usage_tag
    # @return [PortGroupList]
    def find_portgroups(name, usage_tag)
    end

    # @param [String] id
    # @return [Host]
    def host_enter_maintenance_mode(id)
    end

    # @param [String] id
    # @return [Host]
    def host_enter_suspended_mode(id)
    end

    # @param [String] id
    # @return [Host]
    def host_exit_maintenance_mode(id)
    end

    # @param [String] id
    # @return [Host]
    def host_resume(id)
    end

    # @param [String] source_deployment_address
    # @param [String] id
    def initialize_deployment_migration(source_deployment_address, id)
    end

    # @param [String] source_deployment_address
    # @param [String] id
    def finalize_deployment_migration(source_deployment_address, id)
    end

    # @param [String] id
    # @return [Boolean]
    def delete_api_deployment(id)
    end

    # @param [String] id
    # @return [Deployment]
    def destroy_deployment(id)
    end

    # @param [String] deployment_id
    # @param [String] payload
    # @return [ClusterConfiguration]
    def enable_cluster_type(deployment_id, payload)
    end

    # @param [String] deployment_id
    # @param [String] payload
    # @return [Boolean]
    def disable_cluster_type(deployment_id, payload)
    end

    # @param [Hash] payload
    # @return [AvailabilityZone]
    def create_availability_zone(payload)
    end

    # @param [String] id
    # @return AvailabilityZone
    def find_availability_zone_by_id(id)
    end

    # @return [AvailabilityZoneList]
    def find_all_availability_zones()
    end

    # @param [String] id
    # @return [Boolean]
    def delete_availability_zone(id)
    end

    # @param [String] id
    # @param [String] state
    # @return [TaskList]
    def get_availability_zone_tasks(id, state = nil)
    end

    # @param [String] project_id
    # @param [VirtualNetworkCreateSpec] payload
    # @return [VirtualNetwork]
    def create_virtual_network(project_id, payload)
    end

    # @param [String] network_id
    # @return [Boolean]
    def delete_virtual_network(network_id)
    end

    # @return [VirtualNetworkList]
    def find_all_virtual_networks
    end

    # @param [String] network_id
    # @return [VirtualNetwork]
    def find_virtual_network_by_id(network_id)
    end

    # @param [String] name
    # @return [VirtualNetwork]
    def find_virtual_networks_by_name(name)
    end
  end
end
