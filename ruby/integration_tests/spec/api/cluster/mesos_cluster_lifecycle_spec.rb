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

describe "Mesos cluster-service lifecycle", cluster: true do

  before(:all) do
    @seeder = EsxCloud::SystemSeeder.instance
    @cleaner = EsxCloud::SystemCleaner.new(client)

    @deployment = @seeder.deployment!
    @mesos_image = EsxCloud::ClusterHelper.upload_mesos_image(client)
    @default_network = @seeder.network!
    EsxCloud::ClusterHelper.enable_cluster_type(client, @deployment, @mesos_image, "MESOS")
  end

  after(:all) do
    puts "Staring to clean up Mesos Cluster lifecycle tests Env"
    EsxCloud::ClusterHelper.disable_cluster_type(client, @deployment, "MESOS")
    @cleaner.delete_image(@mesos_image)
    @cleaner.delete_network(@default_network)
  end

  it 'should create, delete and resize Mesos cluster successfully' do
    fail("MESOS_ZK_DNS is not defined") unless ENV["MESOS_ZK_DNS"]
    fail("MESOS_ZK_GATEWAY is not defined") unless ENV["MESOS_ZK_GATEWAY"]
    fail("MESOS_ZK_NETMASK is not defined") unless ENV["MESOS_ZK_NETMASK"]
    fail("MESOS_ZK_1_IP is not defined") unless ENV["MESOS_ZK_1_IP"]

    begin
      puts "Starting to create a Mesos cluster"
      project = @seeder.project!
      props = {
        "dns" => ENV["MESOS_ZK_DNS"],
        "gateway" => ENV["MESOS_ZK_GATEWAY"],
        "netmask" => ENV["MESOS_ZK_NETMASK"],
        "zookeeper_ip1" => ENV["MESOS_ZK_1_IP"]
      }
      expected_zk_count = 1
      if ENV["MESOS_ZK_2_IP"] != ""
        props["zookeeper_ip2"] = ENV["MESOS_ZK_2_IP"]
        expected_zk_count += 1
        if ENV["MESOS_ZK_3_IP"] != ""
          props["zookeeper_ip3"] = ENV["MESOS_ZK_3_IP"]
          expected_zk_count += 1
        end
      end
      cluster = project.create_cluster(
        name: random_name("mesos-"),
        type: "MESOS",
        vm_flavor: @seeder.vm_flavor!.name,
        disk_flavor: @seeder.ephemeral_disk_flavor!.name,
        network_id: @seeder.network!.id,
        slave_count: 1,
        batch_size: nil,
        extended_properties: props
      )

      cid = cluster.id
      cluster = client.find_cluster_by_id(cid)
      expect(cluster.name).to start_with("mesos-")
      expect(cluster.type).to eq("MESOS")
      expect(cluster.slave_count).to eq 1
      expect(cluster.state).to eq "READY"

      N_SLAVES = (ENV["N_SLAVES"] || 2).to_i

      puts "Starting to resize a Mesos cluster"
      client.resize_cluster(cid, N_SLAVES)
      cluster = client.find_cluster_by_id(cid)
      expect(cluster.slave_count).to eq N_SLAVES

      puts "Waiting for cluster to become READY after resize"
      EsxCloud::ClusterHelper.wait_for_cluster_state(cid, "READY", 5, 120, client)

      puts "Getting Cluster VM list"
      master_count = 0
      slave_count = 0
      zk_count = 0
      marathon_count = 0
      client.get_cluster_vms(cid).items.each do |i|
        puts i.inspect
        if i.name.start_with?("zookeeper")
          zk_count += 1
        elsif i.name.start_with?("master")
          master_count += 1
        elsif i.name.start_with?("marathon")
          marathon_count += 1
        elsif i.name.start_with?("slave")
            slave_count += 1
        else
            fail("Find an unknown vm #{i.name} in the cluster")
        end
      end
      expect(zk_count).to eq expected_zk_count
      expect(master_count).to eq 3
      expect(marathon_count).to eq 1
      expect(slave_count).to eq N_SLAVES

      puts "Starting to delete a Mesos cluster"
      client.delete_cluster(cid)
      begin
        client.find_cluster_by_id(cid)
        fail("MESOS Cluster #{cid} should be deleted")
      rescue EsxCloud::ApiError => e
        e.response_code.should == 404
      rescue EsxCloud::CliError => e
        e.output.should match("not found")
      end
    rescue EsxCloud::Error => e
      EsxCloud::ClusterHelper.show_logs(@seeder.project, client)
      fail "MESOS cluster integration Test failed. Error: #{e.message}"
    end
  end
end
