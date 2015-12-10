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

  let(:add_mgmt_host_spec) do
    EsxCloud::HostCreateSpec.new(
        EsxCloud::TestHelpers.get_esx_username,
        EsxCloud::TestHelpers.get_esx_password,
        ["MGMT"],
        EsxCloud::TestHelpers.get_esx_ip2,
        host_metadata)
  end

  let(:deployment_spec) do
    EsxCloud::DeploymentCreateSpec.new(
        EsxCloud::TestHelpers.get_datastore_names,
        EsxCloud::AuthInfo.new(false),
        ENV["SYSLOG_ENDPOINT"],
        ENV["NTP_ENDPOINT"],
        true)
  end

  it 'should deploy esxcloud successfully' do
    deployment = EsxCloud::Deployment.create(deployment_spec)
    host = EsxCloud::Host.create(deployment.id, host_spec)

    # Verify that deployment succeeded
    task_list = api_client.find_tasks(deployment.id, "deployment", "COMPLETED")
    tasks = task_list.items
    expect(tasks.size).to eq(1)
    task = tasks.first

    # Verify deployment has no errors and warnings
    expect(task.errors).to be_empty
    expect(task.warnings).to be_empty

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
    expect(system_status.components.size).to eq(5)

    system_status.components.each do |component|
      expect(component.name).not_to be_nil
      expect(component.status).to eq("READY")
    end

    if (!EsxCloud::TestHelpers.get_esx_ip2.nil?)
      host = EsxCloud::Host.create deployment.id, add_mgmt_host_spec
      hosts = EsxCloud::Host.find_all.items
      expect(hosts.size).to eq(2)
      task_list = api_client.find_tasks(host.id, "host", "COMPLETED")
      tasks = task_list.items
      expect(tasks.size).to eq(1)
      task = tasks.first
      expect(task.errors).to be_empty
      expect(task.warnings).to be_empty
    end
  end

  it 'should delete deployment successfully' do
    # Delete deployment
    deployments = EsxCloud::Deployment.find_all.items
    deployment = deployments[0]
    EsxCloud::Deployment.delete deployment.id

    # Verify that delete deployment succeeded
    task_list = api_client.find_tasks(deployment.id, "deployment", "COMPLETED")
    tasks = task_list.items
    delete_tasks = tasks.select {|task| task.operation == "DELETE_DEPLOYMENT" }
    expect(delete_tasks.size).to eq(1)
    task = delete_tasks.first

    # Verify that delete deployment has no errors and warnings
    expect(task.errors).to be_empty
    expect(task.warnings).to be_empty

    # Delete hosts
    hosts = EsxCloud::Host.find_all.items
    host = hosts[0]
    EsxCloud::Host.delete host.id

    # Verify that delete deployment has cleaned up everything
    system_cleaner = EsxCloud::SystemCleaner.new(api_client)
    stat = system_cleaner.clean_system

    fail EsxCloud::Error, "Expect no garbage to be cleaned but found some: #{stat.inspect}" unless stat.empty?
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
end
