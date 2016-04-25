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

require 'spec_helper'
require 'thrift/thrift_helper'

require 'uri'
require 'net/http'

require 'agent_control'

require 'dcp/cloud_store/cloud_store_client'
require_relative '../../lib/management_plane_seeder'
require_relative '../../lib/test_helpers'

describe "migrate finalize", upgrade: true do

  DOCKER_PORT = 2375

  let(:source_api_client) {
    uri = URI.parse(EsxCloud::TestHelpers.get_upgrade_source_address)
    ApiClientHelper.management(protocol: uri.scheme, address: uri.host, port: uri.port.to_s)
  }

  let(:destination_deployment) do
    client.find_all_api_deployments.items.first
  end

  let(:source_deployment) do
    source_api_client.find_all_api_deployments.items.first
  end

  let(:upgrade_cloudstore_map) do
    get_upgrade_cloudstore_map
  end

  describe "#data_check" do
    let(:uri) { URI.parse(EsxCloud::TestHelpers.get_upgrade_source_address) }
    let(:source_cloud_store) { EsxCloud::Dcp::CloudStore::CloudStoreClient.connect_to_endpoint(uri.host, nil) }

    let(:destination_uri) { URI.parse(ApiClientHelper.endpoint(nil, nil, nil)) }
    let(:destination_cloud_store) { EsxCloud::Dcp::CloudStore::CloudStoreClient.connect_to_endpoint(
        destination_uri.host, nil) }

    def self.get_service_map(uri)
      source_cloud_store =  EsxCloud::Dcp::CloudStore::CloudStoreClient.connect_to_endpoint(uri.host, nil)
      json = source_cloud_store.get "/"
      result = {}
      json["documentLinks"].map do |item|
        result[item] = item
      end
      result
    end

    def self.get_upgrade_cloudstore_map
      uri = URI.parse(EsxCloud::TestHelpers.get_upgrade_source_address)
      map = self.get_service_map uri
      map.select { |key,_| key.include? "photon" }
    end

    it "should have all expected factories" do
      destination_map = get_service_map URI.parse(ApiClientHelper.endpoint(nil, nil, nil))
      destination_map = destination_map.select { |key,_| key.include? "photon" }

      expected_new_services_at_destination = ["/photon/cloudstore/virtual-networks"]

      # the two lists should be equal
      uri = URI.parse(EsxCloud::TestHelpers.get_upgrade_source_address)
      map = get_service_map uri
      source_map = map.select { |key,_| key.include? "photon" }
      source_services = source_map.keys
      destination_services = destination_map.keys
      expect(destination_services).to include(*source_services)
      expect((destination_services - source_services - expected_new_services_at_destination).size).to be 0
    end

    self.get_upgrade_cloudstore_map.each do |k, v|
      it "should destination contain all the cloudstore content of the source" do

        exclusion_list = ["/photon/cloudstore/deployments",
                          "/photon/cloudstore/availabilityzones",
                          "/photon/cloudstore/cluster-configurations",
                          "/photon/cloudstore/clusters",
                          "/photon/cloudstore/entity-locks",
                          "/photon/cloudstore/groomers/availability-zone-entity-cleaners",
                          "/photon/cloudstore/groomers/entity-lock-cleaners",
                          "/photon/cloudstore/groomers/tombstone-entity-cleaners",
                          "/photon/task-triggers",
                          "/photon/cloudstore/virtual-networks"]
        puts k
        if !exclusion_list.include?(k)
          begin
            source_json = source_cloud_store.get k
          rescue StandardError => e
            next if e.message.include? "404"
            raise e
          end
          source_service_docs = parse_id_set(source_json).to_a
          destination_json = destination_cloud_store.get v
          destination_service_docs = parse_id_set(destination_json).to_a
          expect(destination_service_docs).to include(*source_service_docs)
        end
      end
    end
  end

  describe "#old plane state" do
    def self.get_service_map(uri)
      source_cloud_store =  EsxCloud::Dcp::CloudStore::CloudStoreClient.connect_to_endpoint(uri.host, nil)
      json = source_cloud_store.get "/"
      result = {}
      json["documentLinks"].map do |item|
        result[item] = item
      end
      result
    end

    def self.get_upgrade_cloudstore_map
      uri = URI.parse(EsxCloud::TestHelpers.get_upgrade_source_address)
      map = self.get_service_map uri
      map.select { |key,_| key.include? "photon" }
    end

    it "should be paused" do
      uri = URI.parse(EsxCloud::TestHelpers.get_upgrade_source_address)
      zk_address = uri.host
      zk = Zookeeper.new("#{zk_address}:2181")
      value = zk.get(path: "/config/apife/status")
      expect(value).to_not be_nil
      expect(value[:data]).to eq "PAUSED"
    end

    self.get_upgrade_cloudstore_map.each do |k, v|
      it "all cloudstore factories should have entities" do
        puts k
        begin
          source_json = source_cloud_store.get k
        rescue StandardError => e
          next if e.message.include? "404"
          raise e
        end
        source_service_docs = parse_id_set(source_json).to_a
        expect(source_service_docs.size).to be > 0
      end
    end
  end

  describe "agent roll out" do
    it "should have rolled out the new agent to all hosts" do
      client.get_deployment_hosts(destination_deployment.id).items.each do |host|
        protocol = Photon::ThriftHelper.get_protocol(host.address, 8835, "AgentControl")
        agent_client = AgentControl::Client.new(protocol)

        req = VersionRequest.new
        res = agent_client.get_version req
        puts host.address
        expect(res.version).to eq "0.9.0"
      end
    end
  end

  describe "interaction with old entities" do
    it "should created entities under existing entities" do
      # find existing image to use for vm creation
      image = client.find_all_images.items[0]
      seeder = EsxCloud::ManagementPlaneSeeder.new
      client.find_all_tenants.items.each do |tenant|
        # ignoring the management tenant as it has tighter resource constraints
        next if tenant.name == "mgmt-tenant"
        client.find_all_projects(tenant.id).items.each do |project|
          # create vm under each project
          seeder.create_vm(project, seeder.random_name("vm-new-"), image.id)
        end
        client.find_all_resource_tickets(tenant.id).items.each do |ticket|
          # create project under each resource ticket
          tenant.create_project(name: seeder.random_name("project-new-"), resource_ticket_name: ticket.name, limits: [create_limit("vm", 1.0, "COUNT"), create_limit("vm.count", 1.0, "COUNT"), create_limit("vm.memory", 1.0, "GB")])
        end
        # create resource ticket under each tenant
        tenant.create_resource_ticket(:name => seeder.random_name("rt-"), :limits => create_small_limits)
      end
    end

    it "should be able to interact with created vms" do
      client.find_all_tenants.items.each do |tenant|
        # ignoring the management tenant to avoid shutting down the old plane
        next if tenant.name == "mgmt-tenant"
        client.find_all_projects(tenant.id).items.each do |project|
          client.find_all_vms(project.id).items.each do |vm|
            ignoring_all_errors { vm.stop! }
            #verifying vm power ops operations
            vm.start!
            vm.stop!

            #verifying image creation from vm
            replication_type = "ON_DEMAND"
            image_name = "image-" + vm.id
            image_create_spec = EsxCloud::ImageCreateSpec.new(image_name, replication_type)

            image = EsxCloud::Image.create_from_vm(vm.id, image_create_spec)
            expect(image.name).to eq image_create_spec.name
            expect(image.state).to eq "READY"
            expect(image.replication).to eq replication_type

            verify_image_cloned_from_vm(project, image.id)

            image_list = EsxCloud::Image.find_all.items.select { |i| i.name == image_name }
            image_list.each { |i| ignoring_all_errors { i.delete } }

            stop_vm(vm)

            #verifying disk detaching, then iso detaching and vm deletion
            vm.disks.each do |disk|
              detach_disk(vm, disk)
            end
            detach_iso vm
            vm.delete

            vm = client.find_all_vms(project.id).items.find {|v| v.id = vm.id}
            expect(vm).to be_nil
          end
        end
      end
    end

    private

    def verify_image_cloned_from_vm(project, image_id)
      new_vm = create_vm(project, image_id)
      expect(new_vm.state).to eq "STOPPED"
      new_vm.start!
      expect(new_vm.state).to eq "STARTED"
      new_vm.stop!
      expect(new_vm.state).to eq "STOPPED"
    ensure
      return unless new_vm
      ignoring_all_errors do
        new_vm.stop! if new_vm.state == "STARTED"
        new_vm.delete
      end
    end

    def detach_disk(vm, disk)
      if ["persistent-disk", "persistent"].include? disk.kind
        vm.detach_disk disk.id
      end
    end

    def detach_iso(vm)
      begin
        vm.detach_iso
      rescue
      end
    end

    def stop_vm(vm)
      begin
        vm.stop!
      rescue
      end
    end
  end

  describe "creating new entities" do
    it "should be able to create new tenant with entities" do
      EsxCloud::ManagementPlaneSeeder.populate
    end
  end

  def get_all_hosts(cloud_store_client, uri)
    hosts = []
    begin
      json = cloud_store_client.get uri
      json["documentLinks"].map do |link|
        hosts << cloud_store_client.get(link)
      end
    rescue StandardError => _
    end
    hosts
  end

  def get_service_map(uri)
    source_cloud_store =  EsxCloud::Dcp::CloudStore::CloudStoreClient.connect_to_endpoint(uri.host, nil)
    json = source_cloud_store.get "/"
    result = {}
    json["documentLinks"].map do |item|
      result[item] = item
    end
    result
  end
end
