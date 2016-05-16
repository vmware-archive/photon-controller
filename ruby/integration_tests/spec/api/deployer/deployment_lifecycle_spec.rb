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

require "spec_helper"
require 'dcp/cloud_store/cloud_store_client'
require 'dcp/deployer_client'
require 'thrift/thrift_helper'

describe "deployment lifecycle", order: :defined, deployer: true do

  let(:api_client) { ApiClientHelper.management(protocol: "http", port: "9000") }

  let(:host_metadata) do
    {
      "ALLOWED_NETWORKS" => EsxCloud::TestHelpers.get_mgmt_port_group,
      "MANAGEMENT_NETWORK_IP" => EsxCloud::TestHelpers.get_mgmt_vm_ip,
      "MANAGEMENT_DATASTORE" => EsxCloud::TestHelpers.get_datastore_name,
      "MANAGEMENT_NETWORK_DNS_SERVER" => EsxCloud::TestHelpers.get_mgmt_vm_dns_server,
      "MANAGEMENT_NETWORK_GATEWAY" => EsxCloud::TestHelpers.get_mgmt_vm_gateway,
      "MANAGEMENT_NETWORK_NETMASK" => EsxCloud::TestHelpers.get_mgmt_vm_netmask,
      "MANAGEMENT_PORTGROUP" => EsxCloud::TestHelpers.get_mgmt_port_group
    }
  end

  let(:host_spec) do
    EsxCloud::HostCreateSpec.new(
        EsxCloud::TestHelpers.get_esx_username,
        EsxCloud::TestHelpers.get_esx_password,
        ["MGMT", "CLOUD"],
        EsxCloud::TestHelpers.get_esx_ip,
        host_metadata)
  end

  let(:deployment_spec) do
    EsxCloud::DeploymentCreateSpec.new(
        EsxCloud::TestHelpers.get_datastore_names,
        EsxCloud::AuthConfigurationSpec.new(false),
        EsxCloud::StatsInfo.new(false),
        ENV["SYSLOG_ENDPOINT"],
        ENV["NTP_ENDPOINT"],
        true)
  end

  let(:host_spec_with_wrong_credentials) do
    EsxCloud::HostCreateSpec.new(
        "fake-user-name",
        EsxCloud::TestHelpers.get_esx_password,
        ["MGMT", "CLOUD"],
        EsxCloud::TestHelpers.get_esx_ip,
        host_metadata)
  end

  after(:each) do
    destroy_created_deployment
  end

  it 'should fail deployment for invalid login credentials of host' do
    deployment = EsxCloud::Deployment.create(deployment_spec)
    begin
      EsxCloud::Host.create(deployment.id, host_spec_with_wrong_credentials)
      fail "deploy with bad host username should have failed"
    rescue EsxCloud::ApiError => e
      expect(e.response_code).to eq(200)
      expect(e.errors.size).to eq(1)
      expect(e.errors.first.size).to eq(1)
      step_error = e.errors.first.first
      expect(step_error.code).to eq("InvalidLoginCredentials")
      expect(step_error.message).to eq("Invalid Username or Password")
      expect(step_error.step["operation"]).to eq("CREATE_HOST")
    end
  end

  it 'should deploy esxcloud successfully' do
    deployment = EsxCloud::Deployment.create(deployment_spec)
    host = EsxCloud::Host.create(deployment.id, host_spec)
    api_client.deploy_deployment(deployment.id)

    # Verify that deployment succeeded
    task_list = api_client.find_tasks(deployment.id, "deployment", "COMPLETED")
    tasks = task_list.items
    expect(tasks.size).to eq(2)

    perform_deployment_task = tasks.select {|task| task.operation == "PERFORM_DEPLOYMENT" }.first
    expect(perform_deployment_task).not_to be_nil

    # Verify deployment has no errors and warnings
    expect(perform_deployment_task.errors).to be_empty
    expect(perform_deployment_task.warnings).to be_empty

    load_balancer_ip = nil

    # Verify deployment summarize info by getting vm with load balancer
    vms = api_client.get_deployment_vms deployment.id
    vms.items.each do |vm|
      if vm.metadata.values.include?("LoadBalancer")
        load_balancer_ip = get_vm_ip(vm.id)
        break
      end
    end

    expect(load_balancer_ip).not_to be_nil

    # Verify the system status of all the deployed components
    lb_client = ApiClientHelper.management(address: load_balancer_ip)
    system_status = lb_client.get_status
    expect(system_status.status).to eq("READY")
    expect(system_status.components.size).to eq(4)

    system_status.components.each do |component|
      expect(component.name).not_to be_nil
      expect(component.status).to eq("READY")
    end
  end

  private

  def get_vm_ip(id)
    port_group = EsxCloud::TestHelpers.get_mgmt_port_group
    vm_networks = api_client.get_vm_networks(id)
    vm_networks.network_connections.each do |network|
      if network.network == port_group
        return network.ip_address
      end
    end
    nil
  end

  def destroy_created_deployment
    deployments = api_client.find_all_api_deployments.items
    deployment_id = deployments.first.id
    # Destroy the created deployment
    api_client.destroy_deployment deployment_id

    # Delete the created hosts
    hosts = api_client.get_deployment_hosts(deployment_id).items
    hosts.each { |host| api_client.mgmt_delete_host(host.id)}

    # Delete the created availability zones
    availability_zones = api_client.find_all_availability_zones.items
    availability_zones.each { |az| api_client.delete_availability_zone(az.id)}

    # Delete the deployment
    api_client.delete_api_deployment deployment_id

    verify_deployment_destroyed_successfuly deployment_id
  end

  def verify_deployment_destroyed_successfuly(deployment_id)
    # Verify there are no cloud store services
    verify_cloud_store_servies_do_not_exist

    # Verify there are no deployer services
    verify_deployer_servies_do_not_exist

    # Verify there are no agent on the host
    verify_agent_do_not_exist_on_host

    # Verify there are no vms on the host
    verify_vm_do_not_exist_on_host

    # Verify availabilityZone is deleted
    availability_zones = api_client.find_all_availability_zones.items
    availability_zones.each { |az| expect(az.state).to eq("PENDING_DELETE")}

    # Verify deployment is deleted
    deployments = api_client.find_all_api_deployments.items
    expect(deployments.size).to eq(0)

    task_list = api_client.find_tasks(deployment_id, "deployment", "COMPLETED")
    tasks = task_list.items

    # Verify that destroy deployment succeeded
    destroy_deployment_task = tasks.select {|task| task.operation == "DESTROY_DEPLOYMENT" }.first
    expect(destroy_deployment_task).not_to be_nil

    # Verify destroy deployment has no errors and warnings
    expect(destroy_deployment_task.errors).to be_empty
    expect(destroy_deployment_task.warnings).to be_empty


    # Verify that delete deployment succeeded
    delete_deployment_task = tasks.select {|task| task.operation == "DELETE_DEPLOYMENT" }.first
    expect(delete_deployment_task).not_to be_nil

    # Verify delete deployment has no errors and warnings
    expect(delete_deployment_task.errors).to be_empty
    expect(delete_deployment_task.warnings).to be_empty
  end

  def verify_cloud_store_servies_do_not_exist
    cloud_store = EsxCloud::Dcp::CloudStore::CloudStoreClient.connect_to_endpoint(nil, nil)

    exclusion_list = ["/photon/cloudstore/availabilityzones",
                      "/photon/cloudstore/groomers/availability-zone-entity-cleaners",
                      "/photon/cloudstore/groomers/entity-lock-cleaners",
                      "/photon/cloudstore/groomers/tombstone-entity-cleaners",
                      "/photon/cloudstore/tasks",
                      "/photon/cloudstore/tombstones",
                      "/photon/task-triggers",
                      "/photon/cloudstore/datastores",
                      "/photon/cloudstore/images-to-image-datastore-mapping",
                      "/photon/cloudstore/entity-locks",]

    get_cloudstore_services(cloud_store).each do |service_factory|
      if !exclusion_list.include?(service_factory)
        checkNoServiceExist(service_factory, cloud_store)
      end
    end
  end

  def verify_deployer_servies_do_not_exist
    deployer_store = EsxCloud::Dcp::DeployerClient.connect_to_endpoint(nil, nil)
    # Verify there are no container services
    checkNoServiceExist("/photon/containers", deployer_store)

    # Verify there are no container template services
    checkNoServiceExist("/photon/container-templates", deployer_store)

    # Verify there are no vm services
    checkNoServiceExist("/photon/vms", deployer_store)

    # Verify there are no vib services
    checkNoServiceExist("/photon/deployer/entities/vibs", deployer_store)
  end

  def verify_agent_do_not_exist_on_host
    begin
      protocol = Photon::ThriftHelper.get_protocol(EsxCloud::TestHelpers.get_esx_ip, 8835, "AgentControl")
      fail "Connection to agent on the host should have failed"
    rescue StandardError => e
      expect(e.message).to include("Could not connect")
    end
  end

  def verify_vm_do_not_exist_on_host
    Net::SSH.start(EsxCloud::TestHelpers.get_esx_ip,
                   EsxCloud::TestHelpers.get_esx_username,
                   {password: EsxCloud::TestHelpers.get_esx_password, user_known_hosts_file: "/dev/null"}) do |ssh|
    vm_count = ssh.exec!("vim-cmd vmsvc/getallvms | tail -n+2 | awk '{print $1, $2}' | wc -l")
    expect(vm_count.to_i).to eq(0)
    end
  end

  def get_cloudstore_services cloud_store
    json = cloud_store.get "/"
    result = Set.new
    json["documentLinks"].map do |item|
      if item.include? "photon"
        result.add(item)
      end
    end
    result
  end

  def checkNoServiceExist(service_factory, store)
    begin
      service_json = store.get service_factory
    rescue StandardError => e
      if !e.message.include? "404"
        raise e
      end
    end
    docs_count = service_json["documentCount"].to_i
    expect(docs_count).to eq(0)
  end
end
