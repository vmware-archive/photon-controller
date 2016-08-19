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

require "support/api_route"

module EsxCloud
  class ApiRoutesHelper
    def self.all_routes
      [
        *auth_routes
      ].concat(all_routes_excluding_auth_routes)
    end

    def self.all_routes_excluding_auth_routes
      seeder = EsxCloud::SystemSeeder.instance
      if seeder.deployment.network_configuration.sdn_enabled
        nw_routes = virtual_networks_routes
        proj_routes = projects_routes_with_virtual_networks_routes
      else
        nw_routes = networks_routes
        proj_routes = projects_routes
      end
      [
        *clusters_routes,
        *datastores_routes,
        *deployments_routes,
        *disks_routes,
        *flavors_routes,
        *hosts_routes,
        *nw_routes,
        *images_routes,
        *proj_routes,
        *resource_tickets_routes,
        *tasks_routes,
        *tenants_routes,
        *vms_routes,
        *status_routes
      ]
    end

    def self.all_routes_using_seeder(seeder)
      if seeder.deployment.network_configuration.sdn_enabled
        nw_routes = virtual_networks_routes
        proj_routes = projects_routes_with_virtual_networks_routes(seeder.project!.id)
      else
        nw_routes = networks_routes
        proj_routes = projects_routes(seeder.project!.id)
      end
      [
          *auth_routes,
          *clusters_routes,
          *datastores_routes,
          *deployments_routes(seeder.deployment!.id),
          *disks_routes(seeder.persistent_disk!.id),
          *hosts_routes,
          *nw_routes,
          *vms_routes(seeder.vm!.id),
          *images_routes(seeder.image!.id),
          *flavors_routes(seeder.vm_flavor!.id),
          *proj_routes,
          *resource_tickets_routes,
          *tasks_routes,
          *tenants_routes(seeder.tenant!.id),
          *status_routes
      ]
    end

    def self.auth_routes
      [ EsxCloud::ApiRoute.new(:get, "/auth", 200, 200, 200, 200, 200) ]
    end

    def self.clusters_routes(id = SecureRandom.uuid)
      [
        EsxCloud::ApiRoute.new(:get, "/clusters/#{id}", 404, 403, 403, 403, 403),
        EsxCloud::ApiRoute.new(:post, "/clusters/#{id}/resize", 400, 403, 403, 403, 403),
        EsxCloud::ApiRoute.new(:get, "/clusters/#{id}/vms", 404, 403, 403, 403, 403),
        EsxCloud::ApiRoute.new(:delete, "/clusters/#{id}", 404, 403, 403, 403, 403)
      ]
    end

    def self.datastores_routes(id = SecureRandom.uuid)
      [
        EsxCloud::ApiRoute.new(:get, "/datastores", 200, 200, 403, 403, 403),
        EsxCloud::ApiRoute.new(:get, "/datastores/#{id}", 404, 404, 403, 403, 403)
      ]
    end

    def self.deployments_routes(id = SecureRandom.uuid)
      [
        EsxCloud::ApiRoute.new(:get, "/deployments", 200, 200, 403, 403, 403),
        EsxCloud::ApiRoute.new(:post, "/deployments", 400, 400, 403, 403, 403),
        EsxCloud::ApiRoute.new(:get, "/deployments/#{id}", 404, 200, 403, 403, 403),
        EsxCloud::ApiRoute.new(:get, "/deployments/#{id}/hosts", 404, 200, 403, 403, 403),
        EsxCloud::ApiRoute.new(:post, "/deployments/#{id}/pause_system", 404, 201, 403, 403, 403),
        EsxCloud::ApiRoute.new(:post, "/deployments/#{id}/pause_background_tasks", 404, 201, 403, 403, 403),
        EsxCloud::ApiRoute.new(:post, "/deployments/#{id}/resume_system", 404, 201, 403, 403, 403),
        EsxCloud::ApiRoute.new(:post, "/deployments/#{id}/set_security_groups", 400, 400, 403, 403, 403),
        EsxCloud::ApiRoute.new(:get, "/deployments/#{id}/vms", 404, 200, 403, 403, 403),
        EsxCloud::ApiRoute.new(:post, "/deployments/#{id}/hosts", 400, 400, 403, 403, 403),
        EsxCloud::ApiRoute.new(:delete, "/deployments/#{SecureRandom.uuid}", 404, 404, 403, 403, 403),
      ]
    end

    def self.disks_routes(id = SecureRandom.uuid)
      [
        EsxCloud::ApiRoute.new(:get, "/disks/#{id}", 404, 200, 200, 200, 403),
        EsxCloud::ApiRoute.new(:get, "/disks/#{id}/tasks", 404, 200, 200, 200, 403),
        EsxCloud::ApiRoute.new(:delete, "/disks/#{id}", 404, 201, 201, 201, 403),
      ]
    end

    def self.flavors_routes(id = SecureRandom.uuid)
      [
        EsxCloud::ApiRoute.new(:get, "/flavors", 200, 200, 200, 200, 200),
        EsxCloud::ApiRoute.new(:post, "/flavors", 400, 400, 403, 403, 403),
        EsxCloud::ApiRoute.new(:get, "/flavors/#{id}", 404, 200, 200, 200, 404),
        EsxCloud::ApiRoute.new(:get, "/flavors/#{id}/tasks", 404, 200, 200, 200, 404),
        EsxCloud::ApiRoute.new(:delete, "/flavors/#{SecureRandom.uuid}", 404, 404, 403, 403, 403),
      ]
    end

    def self.hosts_routes(id = SecureRandom.uuid)
      [
        EsxCloud::ApiRoute.new(:get, "/hosts/#{id}", 404, 404, 403, 403, 403),
        EsxCloud::ApiRoute.new(:post, "/hosts/#{id}/enter_maintenance", 404, 404, 403, 403, 403),
        EsxCloud::ApiRoute.new(:post, "/hosts/#{id}/exit_maintenance", 404, 404, 403, 403, 403),
        EsxCloud::ApiRoute.new(:post, "/hosts/#{id}/resume", 404, 404, 403, 403, 403),
        EsxCloud::ApiRoute.new(:post, "/hosts/#{id}/suspend", 404, 404, 403, 403, 403),
        EsxCloud::ApiRoute.new(:get, "/hosts/#{id}/tasks", 404, 404, 403, 403, 403),
        EsxCloud::ApiRoute.new(:get, "/hosts/#{id}/vms", 404, 404, 403, 403, 403),
        EsxCloud::ApiRoute.new(:delete, "/hosts/#{SecureRandom.uuid}", 404, 404, 403, 403, 403),
      ]
    end

    def self.networks_routes(id = SecureRandom.uuid)
      [
        EsxCloud::ApiRoute.new(:get, "/subnets", 200, 200, 200, 200, 200),
        EsxCloud::ApiRoute.new(:post, "/subnets", 400, 400, 403, 403, 403),
        EsxCloud::ApiRoute.new(:get, "/subnets/#{id}", 404, 404, 404, 404, 404),
        EsxCloud::ApiRoute.new(:post, "/subnets/#{id}/set_portgroups", 400, 400, 403, 403, 403),
        EsxCloud::ApiRoute.new(:delete, "/subnets/#{SecureRandom.uuid}", 404, 404, 403, 403, 403),
        EsxCloud::ApiRoute.new(:post, "/subnets/#{id}/set_default", 404, 404, 403, 403, 403)
      ]
    end

    def self.virtual_networks_routes(id = SecureRandom.uuid)
      [
        EsxCloud::ApiRoute.new(:get, "/subnets/#{id}", 404, 404, 404, 404, 404),
        EsxCloud::ApiRoute.new(:delete, "/subnets/#{SecureRandom.uuid}", 404, 404, 403, 403, 403),
        EsxCloud::ApiRoute.new(:post, "/subnets/#{id}/set_default", 404, 404, 403, 403, 403)
      ]
    end

    def self.images_routes(id = SecureRandom.uuid)
      [
        EsxCloud::ApiRoute.new(:get, "/images", 200, 200, 200, 200, 200),
        EsxCloud::ApiRoute.new(:upload, "/images", 400, 400, 400, 400, 400),
        EsxCloud::ApiRoute.new(:get, "/images/#{id}", 404, 200, 200, 200, 404),
        EsxCloud::ApiRoute.new(:get, "/images/#{id}/tasks", 404, 200, 200, 200, 404),
        EsxCloud::ApiRoute.new(:delete, "/images/#{id}", 404, 201, 201, 201, 404),
      ]
    end

    def self.projects_routes(id = SecureRandom.uuid)
      [
        EsxCloud::ApiRoute.new(:get, "/projects/#{id}", 404, 200, 200, 200, 403),
        EsxCloud::ApiRoute.new(:get, "/projects/#{id}/clusters", 200, 200, 200, 200, 403), # implementation does not verify the existence of project
        EsxCloud::ApiRoute.new(:post, "/projects/#{id}/clusters", 400, 400, 400, 400, 403),
        EsxCloud::ApiRoute.new(:get, "/projects/#{id}/disks", 404, 200, 200, 200, 403),
        EsxCloud::ApiRoute.new(:post, "/projects/#{id}/disks", 400, 400, 400, 400, 403),
        EsxCloud::ApiRoute.new(:get, "/projects/#{id}/tasks", 404, 200, 200, 200, 403),
        EsxCloud::ApiRoute.new(:get, "/projects/#{id}/vms", 404, 200, 200, 200, 403),
        EsxCloud::ApiRoute.new(:post, "/projects/#{id}/vms", 400, 400, 400, 400, 403),
        EsxCloud::ApiRoute.new(:post, "/projects/#{id}/set_security_groups", 400, 400, 400, 403, 403),
        EsxCloud::ApiRoute.new(:delete, "/projects/#{id}", 404, 201, 201, 403, 403)
      ]
    end

    def self.projects_routes_with_virtual_networks_routes(id = SecureRandom.uuid)
      projects_routes.concat(
        [
          EsxCloud::ApiRoute.new(:get, "/projects/#{id}/subnets", 404, 200, 200, 200, 403),
          EsxCloud::ApiRoute.new(:post, "/projects/#{id}/subnets", 400, 400, 400, 400, 403),
        ]
      )
    end

    def self.resource_tickets_routes(id = SecureRandom.uuid)
      [
        EsxCloud::ApiRoute.new(:get, "/resource-tickets/#{id}", 404, 403, 403, 403, 403),
        EsxCloud::ApiRoute.new(:get, "/resource-tickets/#{id}/tasks", 404, 403, 403, 403, 403)
      ]
    end

    def self.tasks_routes(id = SecureRandom.uuid)
      [
        EsxCloud::ApiRoute.new(:get, "/tasks", 200, 200, 200, 200, 200),
        EsxCloud::ApiRoute.new(:get, "/tasks/#{id}", 404, 404, 404, 404, 404)
      ]
    end

    def self.tenants_routes(id = SecureRandom.uuid)
      [
        EsxCloud::ApiRoute.new(:get, "/tenants", 200, 200, 200, 200, 200),
        EsxCloud::ApiRoute.new(:post, "/tenants", 400, 400, 403, 403, 403),
        EsxCloud::ApiRoute.new(:get, "/tenants/#{id}", 404, 200, 200, 200, 404),
        EsxCloud::ApiRoute.new(:get, "/tenants/#{id}/projects", 404, 200, 200, 200, 404),
        EsxCloud::ApiRoute.new(:post, "/tenants/#{id}/projects", 400, 400, 400, 403, 403),
        EsxCloud::ApiRoute.new(:get, "/tenants/#{id}/resource-tickets", 404, 200, 200, 200, 404),
        EsxCloud::ApiRoute.new(:post, "/tenants/#{id}/resource-tickets", 400, 400, 403, 403, 403),
        EsxCloud::ApiRoute.new(:get, "/tenants/#{id}/tasks", 404, 200, 200, 200, 404),
        EsxCloud::ApiRoute.new(:post, "/tenants/#{id}/set_security_groups", 400, 400, 403, 403, 403),
        EsxCloud::ApiRoute.new(:delete, "/tenants/#{SecureRandom.uuid}", 404, 404, 403, 403, 403)
      ]
    end

    def self.vms_routes(id = SecureRandom.uuid)
      [
        EsxCloud::ApiRoute.new(:get, "/vms/#{id}", 404, 200, 200, 200, 403),
        EsxCloud::ApiRoute.new(:post, "/vms/#{id}/attach_disk", 400, 400, 400, 400, 403),
        EsxCloud::ApiRoute.new(:post, "/vms/#{id}/detach_disk", 400, 400, 400, 400, 403),
        EsxCloud::ApiRoute.new(:upload, "/vms/#{id}/attach_iso", 404, 201, 201, 201, 403),
        EsxCloud::ApiRoute.new(:post, "/vms/#{id}/detach_iso", 404, 201, 201, 201, 403),
        EsxCloud::ApiRoute.new(:get, "/vms/#{id}/mks_ticket", 404, 400, 400, 400, 403),
        EsxCloud::ApiRoute.new(:get, "/vms/#{id}/networks", 404, 201, 201, 201, 403),
        EsxCloud::ApiRoute.new(:post, "/vms/#{id}/start", 404, 201, 201, 201, 403),
        EsxCloud::ApiRoute.new(:post, "/vms/#{id}/restart", 404, 201, 201, 201, 403),
        EsxCloud::ApiRoute.new(:post, "/vms/#{id}/suspend", 404, 201, 201, 201, 403),
        EsxCloud::ApiRoute.new(:post, "/vms/#{id}/resume", 404, 201, 201, 201, 403),
        EsxCloud::ApiRoute.new(:post, "/vms/#{id}/stop", 404, 201, 201, 201, 403),
        EsxCloud::ApiRoute.new(:post, "/vms/#{id}/set_metadata", 400, 400, 400, 400, 403),
        EsxCloud::ApiRoute.new(:post, "/vms/#{id}/tags", 400, 400, 400, 400, 403),
        EsxCloud::ApiRoute.new(:get, "/vms/#{id}/tasks", 404, 200, 200, 200, 403),
        EsxCloud::ApiRoute.new(:delete, "/vms/#{id}", 404, 201, 201, 201, 403),
      ]
    end

    def self.status_routes
      [
        EsxCloud::ApiRoute.new(:get, "/status", 200, 200, 403, 403, 403),
      ]
    end
  end
end
