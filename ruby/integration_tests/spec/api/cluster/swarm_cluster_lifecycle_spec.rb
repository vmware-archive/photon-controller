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

describe "Swarm cluster-service lifecycle", cluster: true do

  before(:all) do
    @seeder = EsxCloud::SystemSeeder.instance
    @cleaner = EsxCloud::SystemCleaner.new(client)

    @deployment = @seeder.deployment!
    @swarm_image = EsxCloud::ClusterHelper.upload_swarm_image(client)
    EsxCloud::ClusterHelper.enable_cluster_type(client, @deployment, @swarm_image, "SWARM")
  end

  after(:all) do
    puts "Staring to clean up Swarm Cluster lifecycle tests Env"
    EsxCloud::ClusterHelper.disable_cluster_type(client, @deployment, "SWARM")
    @cleaner.delete_image(@swarm_image)
  end

  it 'should create, delete and resize Swarm cluster successfully' do
    fail("MESOS_ZK_DNS is not defined") unless ENV["MESOS_ZK_DNS"]
    fail("MESOS_ZK_GATEWAY is not defined") unless ENV["MESOS_ZK_GATEWAY"]
    fail("MESOS_ZK_NETMASK is not defined") unless ENV["MESOS_ZK_NETMASK"]
    fail("SWARM_ETCD_1_IP is not defined") unless ENV["SWARM_ETCD_1_IP"]

    begin
      puts "Starting to create a Swarm cluster"
      project = @seeder.project!
      props = {
        "dns" => ENV["MESOS_ZK_DNS"],
        "gateway" => ENV["MESOS_ZK_GATEWAY"],
        "netmask" => ENV["MESOS_ZK_NETMASK"],
        "etcd_ip1" => ENV["SWARM_ETCD_1_IP"]
      }
      expected_etcd_count = 1
      if ENV["SWARM_ETCD_2_IP"] != ""
        props["etcd_ip2"] = ENV["SWARM_ETCD_2_IP"]
        expected_etcd_count += 1
        if ENV["SWARM_ETCD_3_IP"] != ""
          props["etcd_ip3"] = ENV["SWARM_ETCD_3_IP"]
          expected_etcd_count += 1
        end
      end
      cluster = project.create_cluster(
        name: random_name("swarm-"),
        type: "SWARM",
        vm_flavor: @seeder.vm_flavor!.name,
        disk_flavor: @seeder.ephemeral_disk_flavor!.name,
        network_id: @seeder.network!.id,
        worker_count: 1,
        batch_size: nil,
        extended_properties: props
      )

      cid = cluster.id
      cluster = client.find_cluster_by_id(cid)
      expect(cluster.name).to start_with("swarm-")
      expect(cluster.type).to eq("SWARM")
      expect(cluster.worker_count).to eq 1
      expect(cluster.state).to eq "READY"

      N_WORKERS = (ENV["N_SLAVES"] || 2).to_i

      puts "Starting to resize a Swarm cluster"
      client.resize_cluster(cid, N_WORKERS)
      cluster = client.find_cluster_by_id(cid)
      expect(cluster.worker_count).to eq N_WORKERS

      puts "Waiting for cluster to become READY after resize"
      EsxCloud::ClusterHelper.wait_for_cluster_state(cid, "READY", 5, 120, client)

      puts "Getting Cluster VM list"
      master_count = 0
      worker_count = 0
      etcd_count = 0
      client.get_cluster_vms(cid).items.each do |i|
        puts i.inspect
        if i.name.start_with?("etcd")
          etcd_count += 1
        elsif i.name.start_with?("master")
          master_count += 1
        elsif i.name.start_with?("worker")
            worker_count += 1
        else
            fail("Find an unknown vm #{i.name} in the cluster")
        end
      end
      expect(etcd_count).to eq expected_etcd_count
      expect(master_count).to eq 1
      expect(worker_count).to eq N_WORKERS

      puts "Starting to delete a Swarm cluster"
      client.delete_cluster(cid)
      begin
        client.find_cluster_by_id(cid)
        fail("SWARM Cluster #{cid} should be deleted")
      rescue EsxCloud::ApiError => e
        e.response_code.should == 404
      rescue EsxCloud::CliError => e
        e.output.should match("not found")
      end
    rescue EsxCloud::Error => e
      EsxCloud::ClusterHelper.show_logs(@seeder.project, client)
      fail "SWARM cluster integration Test failed. Error: #{e.message}"
    end
  end
end
