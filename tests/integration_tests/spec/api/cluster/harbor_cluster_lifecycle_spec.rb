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

require "net/ssh"
require "spec_helper"
require "test_helpers"

describe "Harbor cluster-service lifecycle", cluster: true do

  before(:all) do
    @seeder = EsxCloud::SystemSeeder.instance
    @cleaner = EsxCloud::SystemCleaner.new(client)
    # Create another seeder to create a new tenant because a seeder is attached to a tenant.
    @seeder2 = EsxCloud::SystemSeeder.new(
        [
            EsxCloud::QuotaLineItem.new("vm.memory", 15000.0, "GB"),
            EsxCloud::QuotaLineItem.new("vm", 1000.0, "COUNT")
        ])
    @deployment = @seeder.deployment!
    @harbor_image = EsxCloud::ClusterHelper.upload_harbor_image(client)
    EsxCloud::ClusterHelper.enable_cluster_type(client, @deployment, @harbor_image, "HARBOR")
    EsxCloud::ClusterHelper.generate_temporary_ssh_key
  end

  after(:all) do
    puts "Starting to clean up Harbor Cluster lifecycle tests Env"
    system_cleaner = EsxCloud::SystemCleaner.new(ApiClientHelper.management)
    ignoring_all_errors { system_cleaner.delete_tenant(@seeder2.tenant) }
    EsxCloud::ClusterHelper.disable_cluster_type(client, @deployment, "HARBOR")
    @cleaner.delete_image(@harbor_image)
    EsxCloud::ClusterHelper.remove_temporary_ssh_key
  end

  it 'should create/delete Harbor cluster successfully' do
    fail("MESOS_ZK_DNS is not defined") unless ENV["MESOS_ZK_DNS"]
    fail("MESOS_ZK_GATEWAY is not defined") unless ENV["MESOS_ZK_GATEWAY"]
    fail("MESOS_ZK_NETMASK is not defined") unless ENV["MESOS_ZK_NETMASK"]
    fail("KUBERNETES_MASTER_IP is not defined") unless ENV["KUBERNETES_MASTER_IP"]
    fail("KUBERNETES_ETCD_1_IP is not defined") unless ENV["KUBERNETES_ETCD_1_IP"]

    begin
      # We don't have IP addresses set aside for Harbor, so we re-use the Kubernetes IP addresses
      harbor1_ip = ENV["KUBERNETES_MASTER_IP"]
      harbor2_ip = ENV["KUBERNETES_ETCD_1_IP"]

      puts "Create a Harbor cluster"
      props = construct_properties(harbor1_ip)
      project = @seeder.project!
      cluster1 = create_harbor_cluster(project, props)
      validate_harbor_cluster_info(cluster1.id)
      EsxCloud::ClusterHelper.validate_ssh(harbor1_ip)

      puts "Test that a single tenant can have multiple clusters"
      props = construct_properties(harbor2_ip)
      cluster2 = create_harbor_cluster(project, props)
      validate_harbor_cluster_info(cluster2.id)
      EsxCloud::ClusterHelper.delete_cluster(client, cluster2.id, "HARBOR")
      
      puts "Test that two tenants can create and operate on their own Harbor cluster in parallel"
      project = @seeder2.project!
      cluster3 = create_harbor_cluster(project, props)
      validate_harbor_cluster_info(cluster3.id)
      validate_harbor_response(harbor1_ip)
      validate_harbor_response(harbor2_ip)
      EsxCloud::ClusterHelper.delete_cluster(client, cluster1.id, "HARBOR")
      EsxCloud::ClusterHelper.delete_cluster(client, cluster3.id, "HARBOR")
    rescue EsxCloud::Error => e
      EsxCloud::ClusterHelper.show_logs(@seeder.project, client)
      fail "HARBOR cluster integration Test failed. Error: #{e.message}"
    end
  end

  private

  def construct_properties(master_ip)
    public_key_contents = File.read("/tmp/test_rsa.pub")
    props = {
        "dns" => ENV["MESOS_ZK_DNS"],
        "gateway" => ENV["MESOS_ZK_GATEWAY"],
        "netmask" => ENV["MESOS_ZK_NETMASK"],
        "master_ip" => master_ip,
        "ssh_key" => public_key_contents,
        "admin_password" => SecureRandom.urlsafe_base64(16)
    }
  end

  def validate_harbor_cluster_info(cluster_id)
    cluster = client.find_cluster_by_id(cluster_id)
    expect(cluster.name).to start_with("harbor-")
    expect(cluster.type).to eq("HARBOR")
    expect(cluster.worker_count).to eq 0
    expect(cluster.state).to eq "READY"
    expect(cluster.extended_properties.size).to eq(6)
    expect(cluster.extended_properties["ca_cert"]).to include("BEGIN CERTIFICATE")
    expect(cluster.extended_properties["ca_cert"]).to include("END CERTIFICATE")
  end

  def create_harbor_cluster(project, props)
    project.create_cluster(
        name: random_name("harbor-"),
        type: "HARBOR",
        vm_flavor: @seeder.vm_flavor!.name,
        disk_flavor: @seeder.ephemeral_disk_flavor!.name,
        network_id: @seeder.network!.id,
        batch_size: nil,
        extended_properties: props
    )
  end

  def validate_harbor_response(master_ip)
    endpoint = "https://" + master_ip + ":443"
    http_client = EsxCloud::HttpClient.new endpoint
    response = http_client.get("/")
    expect(response.code).to be 200
  end
end
