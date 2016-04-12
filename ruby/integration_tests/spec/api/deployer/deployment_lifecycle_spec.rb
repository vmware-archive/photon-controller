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

  context "should destroy failed deployment successfully" do
    it 'should fail deployment on Create host step ' do
      let(:host_spec) do
        EsxCloud::HostCreateSpec.new(
            "fake_userName",
            EsxCloud::TestHelpers.get_esx_password,
            ["MGMT", "CLOUD"],
            EsxCloud::TestHelpers.get_esx_ip,
            host_metadata)
      end

      deployment = EsxCloud::Deployment.create(deployment_spec)

      begin
        EsxCloud::Host.create(deployment.id, host_spec)
        fail "deploy with bad host username should have failed"
      rescue EsxCloud::ApiError => e
        puts e.errors[0].code
      end
      destroy_created_deployment deployment.id
    end

    it 'fail deployment on perform deployment step ' do
      let(:host_metadata) do
        {
            "ALLOWED_NETWORKS" => EsxCloud::TestHelpers.get_mgmt_port_group,
            "MANAGEMENT_NETWORK_IP" => EsxCloud::TestHelpers.get_mgmt_vm_ip,
            "MANAGEMENT_DATASTORE" => EsxCloud::TestHelpers.get_datastore_name,
            "MANAGEMENT_NETWORK_DNS_SERVER" => EsxCloud::TestHelpers.get_mgmt_vm_dns_server,
            "MANAGEMENT_NETWORK_GATEWAY" => "10.121.10.18",
            "MANAGEMENT_NETWORK_NETMASK" => EsxCloud::TestHelpers.get_mgmt_vm_netmask,
            "MANAGEMENT_PORTGROUP" => EsxCloud::TestHelpers.get_mgmt_port_group
        }
      end

        begin
          EsxCloud::Host.create(deployment.id, host_spec)
          fail "deploy with bad host metadata should have failed"
        rescue EsxCloud::ApiError => e
          puts e.errors[0].code
        end
        destroy_created_deployment deployment.id
    end
  end

  it 'should deploy and destroy esxcloud successfully' do
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

    destroy_created_deployment deployment.id
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

  def destroy_created_deployment deploymentID
    # Destroy the created deployment
    api_client.destroy_deployment(deploymentID)

    hosts = api_client.get_deployment_hosts(deploymentID).items
    hosts.each { |host| api_client.mgmt_delete_host(host.id)}

    availabilityZones = api_client.find_all_availability_zones().items
    availabilityZones.each { |aZone| api_client.delete_availability_zone(aZone.id)}

    api_client.delete_api_deployment(deployment.id)
    verify_deployment_destroyed_successfuly()
  end

  def verify_deployment_destroyed_successfuly()
    # Verify that destroy deployment succeeded
    task_list = api_client.find_tasks(deployment.id, "deployment", "COMPLETED")
    tasks = task_list.items

    destroy_deployment_task = tasks.select {|task| task.operation == "DESTROY_DEPLOYMENT" }.first
    expect(destroy_deployment_task).not_to be_nil

    # Verify destroy deployment has no errors and warnings
    expect(destroy_deployment_task.errors).to be_empty
    expect(destroy_deployment_task.warnings).to be_empty

    delete_deployment_task = tasks.select {|task| task.operation == "DELETE_DEPLOYMENT" }.first
    expect(delete_deployment_task).not_to be_nil

    # Verify delete deployment has no errors and warnings
    expect(delete_deployment_task.errors).to be_empty
    expect(delete_deployment_task.warnings).to be_empty

    # Verify host is deleted
    hosts = api_client.find_all_hosts().items
    expect(hosts.size).to eq(0)

    # Verify availabilityZone is deleted
    availabilityZones = api_client.find_all_availability_zones().items
    availabilityZones.each { |aZone| expect(aZone.state).to eq("PENDING_DELETE")}

    # Verify deployment is deleted
    deployments = api_client.find_all_api_deployments().items
    expect(deployments.size).to eq(0)
  end
end
