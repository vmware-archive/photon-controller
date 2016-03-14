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

  describe "#data_check" do
    it "should destination contain all the cloudstore content of the source" do
      upgrade_cloudstore_map = {
          "/esxcloud/cloudstore/datastores" => "/photon/cloudstore/datastores",
          "/esxcloud/cloudstore/hosts" => "/photon/cloudstore/hosts",
          "/esxcloud/cloudstore/attached-disks" => "/photon/cloudstore/attached-disks",
          "/esxcloud/cloudstore/flavors" => "/photon/cloudstore/flavors",
          "/esxcloud/cloudstore/images" => "/photon/cloudstore/images",
          "/esxcloud/cloudstore/networks" => "/photon/cloudstore/networks",
          "/provisioning/esxcloud/portgroups" => "/photon/cloudstore/portgroups",
          "/esxcloud/cloudstore/projects" => "/photon/cloudstore/projects",
          "/esxcloud/cloudstore/tenants" => "/photon/cloudstore/tenants",
          "/esxcloud/cloudstore/resource-tickets" => "/photon/cloudstore/resource-tickets",
          "/esxcloud/cloudstore/vms" => "/photon/cloudstore/vms",
          "/esxcloud/cloudstore/disks" => "/photon/cloudstore/disks",
          "/photon/cloudstore/datastores" => "/photon/cloudstore/datastores",
          "/photon/cloudstore/hosts" => "/photon/cloudstore/hosts",
          "/photon/cloudstore/attached-disks" => "/photon/cloudstore/attached-disks",
          "/photon/cloudstore/flavors" => "/photon/cloudstore/flavors",
          "/photon/cloudstore/images" => "/photon/cloudstore/images",
          "/photon/cloudstore/networks" => "/photon/cloudstore/networks",
          "/photon/cloudstore/portgroups" => "/photon/cloudstore/portgroups",
          "/photon/cloudstore/projects" => "/photon/cloudstore/projects",
          "/photon/cloudstore/tenants" => "/photon/cloudstore/tenants",
          "/photon/cloudstore/resource-tickets" => "/photon/cloudstore/resource-tickets",
          "/photon/cloudstore/vms" => "/photon/cloudstore/vms",
          "/photon/cloudstore/disks" => "/photon/cloudstore/disks"}
      uri = URI.parse(EsxCloud::TestHelpers.get_upgrade_source_address)
      source_cloud_store =  EsxCloud::Dcp::CloudStore::CloudStoreClient.connect_to_endpoint(uri.host, nil)

      destination_uri = URI.parse(ApiClientHelper.endpoint(nil, nil, nil))
      destination_cloud_store =  EsxCloud::Dcp::CloudStore::CloudStoreClient.connect_to_endpoint(
          destination_uri.host, nil)

      upgrade_cloudstore_map.each do |k, v|
        puts k
        begin
          source_json = source_cloud_store.get k
        rescue StandardError => e
          next if e.message.include? "404"
          raise e
        end
        source_set = parse_id_set(source_json)
        destination_json = destination_cloud_store.get v
        destination_set = parse_id_set(destination_json)
        expect(destination_set.superset?(source_set)).to eq true
      end
    end

    it "should have created new entities for each factory" do
      exclude_factories = %w(/photon/cloudstore/entity-locks /photon/cloudstore/cluster-configurations)
      destination_uri = URI.parse(ApiClientHelper.endpoint(nil, nil, nil))

      cs =  EsxCloud::Dcp::CloudStore::CloudStoreClient.connect_to_endpoint(
          destination_uri.host, nil)
      cs_installer = EsxCloud::Dcp::CloudStore::CloudStoreClient.connect_to_endpoint(
          ENV["OVA_IP"] || fail("OVA_IP not set"), nil)

      empty_factories = []

      get_cloudstore_factories(cs).each do |factory|
        next if exclude_factories.include? factory
        installer_set = parse_id_set(cs_installer.get factory)
        managemet_set = parse_id_set(cs.get factory)

        migrated_set = managemet_set.select { |id| !installer_set.include? id }

        empty_factories << factory if migrated_set.count == 0
      end

      if empty_factories.count != 0
        puts "factories without data:"
        puts empty_factories
      end
      expect(empty_factories.count).to be == 0
    end
  end

  describe "#old plane state" do
    ["/esxcloud/cloudstore/hosts", "/photon/cloudstore/hosts"].each do |host_uri|
      it "should not be running housekeeper [host_uri: #{host_uri}]" do
        uri = URI.parse(EsxCloud::TestHelpers.get_upgrade_source_address)
        source_cloud_store =  EsxCloud::Dcp::CloudStore::CloudStoreClient.connect_to_endpoint(uri.host, nil)
        get_all_hosts(source_cloud_store, host_uri) do |host|
          if host.usage_tags.include? "MGMT"
            vm_ip = host.metadata["MANAGEMENT_NETWORK_IP"]
            uri = URI("http://#{vm_ip}:#{DOCKER_PORT}/containers/json")
            uri.query = URI.encode_www_form({ :all => false })
            res = Net::HTTP.get(uri)
            fail("HouseKeeper container on #{vm_ip} still running") unless JSON.parse(res)[0]["Names"].select { |name| name.downcase.include? "housekeeper" }.empty?
          end
        end
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
        expect(res.version).to eq "0.1.2"
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
            vm.start!
            vm.stop!
          end
        end
      end
    end
  end

  describe "creating new entities" do
    it "should be able to create new tenant with entities" do
      EsxCloud::ManagementPlaneSeeder.populate
    end
  end

  def get_cloudstore_factories(cloud_store)
    response = cloud_store.get("/")
    response["documentLinks"].select { |link|  link.include? "/photon/" }
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
end
