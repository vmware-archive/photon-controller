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

describe "Harbor cluster-service lifecycle", cluster: true do

before(:all) do
  @seeder = EsxCloud::SystemSeeder.instance
  @cleaner = EsxCloud::SystemCleaner.new(client)

  @deployment = @seeder.deployment!
  @harbor_image = EsxCloud::ClusterHelper.upload_harbor_image(client)
  EsxCloud::ClusterHelper.enable_cluster_type(client, @deployment, @harbor_image, "HARBOR")
end

after(:all) do
  puts "Staring to clean up Harbor Cluster lifecycle tests Env"
  EsxCloud::ClusterHelper.disable_cluster_type(client, @deployment, "HARBOR")
  @cleaner.delete_image(@harbor_image)
end

it 'should create/delete Harbor cluster successfully' do
  fail("MESOS_ZK_DNS is not defined") unless ENV["MESOS_ZK_DNS"]
  fail("MESOS_ZK_GATEWAY is not defined") unless ENV["MESOS_ZK_GATEWAY"]
  fail("MESOS_ZK_NETMASK is not defined") unless ENV["MESOS_ZK_NETMASK"]
  fail("KUBERNETES_MASTER_IP is not defined") unless ENV["KUBERNETES_MASTER_IP"]

  puts "Starting to create a Harbor cluster"
  begin
    project = @seeder.project!
    props = {
        "dns" => ENV["MESOS_ZK_DNS"],
        "gateway" => ENV["MESOS_ZK_GATEWAY"],
        "netmask" => ENV["MESOS_ZK_NETMASK"],
        "master_ip" => ENV["KUBERNETES_MASTER_IP"]
    }

    cluster = project.create_cluster(
        name: random_name("harbor-"),
        type: "HARBOR",
        vm_flavor: @seeder.vm_flavor!.name,
        disk_flavor: @seeder.ephemeral_disk_flavor!.name,
        network_id: @seeder.network!.id,
        batch_size: nil,
        extended_properties: props
    )

    cid = cluster.id
    cluster = client.find_cluster_by_id(cid)
    expect(cluster.name).to start_with("harbor-")
    expect(cluster.type).to eq("HARBOR")
    expect(cluster.worker_count).to eq 0
    expect(cluster.state).to eq "READY"

    puts "Starting to delete a Harbor cluster"
    client.delete_cluster(cid)
    begin
      client.find_cluster_by_id(cid)
      fail("HARBOR Cluster #{cid} should be deleted")
    rescue EsxCloud::ApiError => e
      e.response_code.should == 404
    rescue EsxCloud::CliError => e
      e.output.should match("not found")
    end
  rescue EsxCloud::Error => e
    EsxCloud::ClusterHelper.show_logs(@seeder.project, client)
    fail "HARBOR cluster integration Test failed. Error: #{e.message}"
  end
end
end
