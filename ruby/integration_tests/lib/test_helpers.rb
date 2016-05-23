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

require 'net/ssh'
require 'rspec/expectations'

RSpec::Matchers.define :be_blank do
  match do |actual|
    actual.nil? || actual.strip.empty?
  end
end

module EsxCloud
  module TestHelpers

    def self.reset_flavor
      puts "Reset flavors"
      reset_flavors_in_db
    end

    def self.reset_flavors_in_db
      path = File.join(File.dirname(__FILE__), "../../common/test_files/flavors")
      delete_all_flavors
      create_flavors_from_path(path)
    end

    def self.create_flavors_from_path(path)
      files = Dir[File.join(path, "*.yml")]
      if files.empty?
        puts "No flavor files found"
        exit
      end
      files.each do |file|
        Flavor.upload_flavor(file, false)
      end
    end

    def self.delete_all_flavors
      flavor_list = Flavor.find_all
      flavor_list.items.each do |f|
        f.delete
      end
    end

    # Wait until system is ready to accept requests (timeout is half an hour if not specified)
    def self.await_system_ready(timeout_s = 1800)
      timeout_s = timeout_s.to_i

      puts "Waiting on system to be ready with timeout of #{timeout_s} seconds"
      deadline = Time.now + timeout_s
      while true
        begin
          system_status = EsxCloud::Config.client.get_status
        rescue Faraday::Error::TimeoutError => e
          puts "get_status http request timeout: " + e.to_s
        end

        if system_status and system_status.status == "READY"
          puts "System is ready:"
          puts system_status
          return
        end

        if Time.now > deadline
          puts "Timed out while waiting for system to be ready:"
          puts system_status
          abort
        end

        sleep(1)
      end
    end

    def self.get_esx_ip
      ENV["ESX_IP"]
    end

    def self.get_mgmt_port_group
      ENV["ESX_MGMT_PORT_GROUP"]
    end

    def self.get_vm_port_group
      ENV["ESX_VM_PORT_GROUP"]
    end

    def self.get_vm_port_group2
      ENV["ESX_VM_PORT_GROUP2"]
    end

    def self.get_vm_port_groups
      [
          self.get_vm_port_group,
          self.get_vm_port_group2
      ].compact
    end

    def self.get_all_port_groups
      [
          self.get_mgmt_port_group,
          self.get_vm_port_group,
          self.get_vm_port_group2
      ].compact
    end

    def self.get_datastore_name
      ENV["ESX_DATASTORE"] || "datastore1"
    end

    def self.get_datastore_names
      [get_datastore_name]
    end

    def self.get_datastore_id
      ENV["ESX_DATASTORE_ID"]
    end

    def self.get_mgmt_vm_dns_server
      ENV["MGMT_VM_DNS_SERVER"]
    end

    def self.get_mgmt_vm_gateway
      ENV["MGMT_VM_GATEWAY"]
    end

    def self.get_mgmt_vm_ip
      ENV["MGMT_VM_IP"]
    end

    def self.get_mgmt_vm_netmask
      ENV["MGMT_VM_NETMASK"]
    end

    def self.get_mgmt_vm_ip_for_add_mgmt_host
      ENV["MGMT_VM_IP_FOR_ADD_MGMT_HOST"]
    end

    def self.get_esx_username
      ENV["ESX_USERNAME"]
    end

    def self.get_esx_password
      ENV["ESX_PASSWORD"]
    end

    def self.get_upgrade_source_address
      ENV["UPGRADE_SOURCE_ADDRESS"]
    end

    def logger
      Config.logger
    end

    def client
      Config.client
    end

    def ignoring_api_errors(&block)
      block.call
    rescue ApiError => e
      STDERR.puts "  ignoring API error: #{e.message}"
    end

    def ignoring_all_errors(&block)
      block.call
    rescue ApiError => e
      STDERR.puts "  ignoring API error: #{e.message}"
    rescue Exception => e
      STDERR.puts "  ignoring error: #{e}"
    end

    def create_host(deployment, vm)
      host_ip = client.find_vm_by_id(vm.id).host
      fail EsxCloud::Error, "vm #{vm} does not have host" unless host_ip

      EsxCloud::Host.find_all.items.each do |host|
        return host if host.address == host_ip
      end

      spec = EsxCloud::HostCreateSpec.new("u", "p", ["CLOUD"], host_ip, {}, "z")
      EsxCloud::Host.create(deployment.id, spec)
    end

    def delete_host(id)
      EsxCloud::Host.enter_suspended_mode(id)
      EsxCloud::Host.enter_maintenance_mode(id)
      EsxCloud::Host.get_host_vms(id).items.each { |vm| vm.delete }
      expect(EsxCloud::Host.delete(id)).to eq(true)
    end

    def host_set_availability_zone(id, spec)
      EsxCloud::Host.host_set_availability_zone(id, spec)
    end

    # @param[AvailabilityZoneCreateSpec] spec
    # @return [AvailabilityZone]
    def create_availability_zone(spec)
      AvailabilityZone.create(spec)
    end

    def find_availability_zone_by_id(id)
      AvailabilityZone.find_by_id(id)
    end

    def find_all_availability_zones
      AvailabilityZone.find_all
    end

    def delete_availability_zone_by_id(id)
      AvailabilityZone.delete(id)
    end

    # @param[FlavorCreateSpec] spec
    # @return [Flavor]
    def create_flavor(spec)
      Flavor.create(spec)
    end

    def create_vm(project, override_options = {})
      default_create_vm_spec = { image_id: EsxCloud::SystemSeeder.instance.image!.id,
                                 name: random_name("vm-"),
                                 flavor: EsxCloud::SystemSeeder.instance.vm_flavor!.name,
                                 disks: create_ephemeral_disks([random_name("disk-")]) }
      project.create_vm(default_create_vm_spec.merge(override_options))
    end

    def find_flavors_by_name_kind(name, kind)
      Flavor.find_by_name_kind(name, kind)
    end

    def find_all_flavors
      Flavor.find_all
    end

    def find_flavor_by_id(id)
      Flavor.find_by_id(id)
    end

    def parse_id_set(payload)
      unless payload.is_a?(Hash) && payload["documentLinks"].is_a?(Enumerable)
        raise UnexpectedFormat, "Invalid items list: #{payload}"
      end

      result = Set.new
      items = payload["documentLinks"].map do |item|
        id = item.downcase.match("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")
        result.add(id.to_s.strip)
      end
      result
    end

    def delete_flavor_by_id(id)
      client.delete_flavor(id)
    end

    def delete_flavor_by_name_kind(name, kind)
      client.delete_flavor_by_name_kind(name, kind)
    end

    def create_tenant(options = {})
      Tenant.create(TenantCreateSpec.new(options[:name], options[:security_groups]))
    end

    def find_tenants_by_name(name)
      Tenant.find_by_name(name)
    end

    def find_tenant_by_id(id)
      Tenant.find_by_id(id)
    end

    def find_project_by_id(id)
      Project.find_by_id(id)
    end

    def find_resource_ticket_by_id(id)
      ResourceTicket.find_by_id(id)
    end

    def create_random_tenant
      create_tenant(name: random_name("tenant-"))
    end

    def get_project_vms(project)
      client.get_project_vms(project)
    end

    def create_limit(key, value, unit)
      QuotaLineItem.new(key, value, unit)
    end

    def create_small_limits
      [
          QuotaLineItem.new("vm.memory", 375.0, "GB"),
          QuotaLineItem.new("vm", 1000.0, "COUNT")
      ]
    end

    def create_ephemeral_disks(names)
      kinds = ["ephemeral-disk", "ephemeral"]

      flavor_name = EsxCloud::SystemSeeder.instance.ephemeral_disk_flavor!.name
      disks = [VmDisk.new(names.delete_at(0), kinds[0], flavor_name, nil, true)]
      disks + names.map.with_index(1) do |name, i|
        EsxCloud::VmDisk.new(name, kinds[i % kinds.length], flavor_name, 1, false) }
      end
    end

    def create_subdivide_limit(percent)
      QuotaLineItem.new("subdivide.percent", Float(percent), "COUNT")
    end

    def random_name(prefix = "rn")
      prefix + SecureRandom.base64(12).tr("+/", "ab")
    end

    def get_vm_flavor(flavor_name)
      flavor_list = find_flavors_by_name_kind(flavor_name, "vm")
      return flavor_list.items[0]
    end

    def get_vm_flavor_cost_key(flavor_name, cost_key)
      vm_flavor = get_vm_flavor(flavor_name)
      vm_flavor.cost.find { |cost| cost.key == cost_key }.to_hash
    end

    def convert_units(units1, value1, units2, value2)
      units = { "GB" => 3, "MB" => 2, "KB" => 1 }
      units_to_use = units1
      if units1 != units2
        units_to_use = [units[units1], units[units2]].max
        value1 = value1 / (1024 ** (units_to_use - units[units1]))
        value2 = value2 / (1024 ** (units_to_use - units[units2]))
      end
      return units.key(units_to_use), value1, value2
    end

    def get_esx_ip
      TestHelpers.get_esx_ip
    end

    def get_mgmt_port_group
      TestHelpers.get_mgmt_port_group
    end

    def get_vm_port_group
      TestHelpers.get_vm_port_group
    end

    def get_vm_port_group2
      TestHelpers.get_vm_port_group2
    end

    def get_datastore_name
      TestHelpers.get_datastore_name
    end

    def get_datastore_id
      TestHelpers.get_datastore_id
    end

    def get_esx_username
      TestHelpers.get_esx_username
    end

    def get_esx_password
      TestHelpers.get_esx_password
    end

    def assert_image_does_not_exist(image_id)
      return unless ENV["REAL_AGENT"]

      cmd = "ssh-keygen -f \"#{ENV['HOME']}/.ssh/known_hosts\" -R #{get_esx_ip} > /dev/null 2>&1"
      `#{cmd}`

      Net::SSH.start(get_esx_ip, get_esx_username, password: get_esx_password) do |session|
        image_path = get_image_path_dir(image_id)
        output = session.exec!("[ -e #{image_path} ] && echo Y || echo N").strip
        puts "Image #{image_id} still exists: #{get_esx_ip} #{image_path}" if output == "Y"
        expect(output).to eq("N")
      end
    end

    #wait for image seeding progress to be done, timeout 90 mins
    def wait_for_image_seeding_progress_is_done
      1080.times do
        done = image_seeding_progress_is_done EsxCloud::SystemSeeder.instance.image!.id
        if done
          return
        end

        puts "waiting for image #{EsxCloud::SystemSeeder.instance.image!.id} seeding to be done..."
        sleep 5
      end

      raise TimeoutError.new
    end

    private

    def get_image_path_dir(image_id)
      image_path = File.expand_path(get_datastore_name, "/vmfs/volumes/")
      image_path = File.expand_path("images", image_path)
      image_path = File.expand_path(image_id[0,2], image_path)
      File.expand_path(image_id, image_path)
    end

    def image_seeding_progress_is_done(image_id)
      image = EsxCloud::Image.find_by_id(image_id)
      logger.debug "image #{image.id} seeding progress #{image.seeding_progress}"
      image.seeding_progress.eql? "100%"
    end

  end
end
