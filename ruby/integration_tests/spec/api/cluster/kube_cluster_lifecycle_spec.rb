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

describe "Kubernetes cluster-service lifecycle", cluster: true do

  before(:all) do
    @seeder = EsxCloud::SystemSeeder.instance
    @cleaner = EsxCloud::SystemCleaner.new(client)
    # This seeder2 is created to create tenant 2
    # It is an exact copy of the seeder config

    @deployment = @seeder.deployment!
    @kubernetes_image = EsxCloud::ClusterHelper.upload_kubernetes_image(client)
    EsxCloud::ClusterHelper.enable_cluster_type(client, @deployment, @kubernetes_image, "KUBERNETES")
    EsxCloud::ClusterHelper.generate_temporary_ssh_key()
  end

  after(:all) do
    puts "Staring to clean up Kubernetes Cluster lifecycle tests Env"
    EsxCloud::ClusterHelper.disable_cluster_type(client, @deployment, "KUBERNETES")
    @cleaner.delete_image(@kubernetes_image)
    EsxCloud::ClusterHelper.remove_temporary_ssh_key()
  end

  it 'should create/resize/delete Kubernetes cluster successfully' do
    fail("MESOS_ZK_DNS is not defined") unless ENV["MESOS_ZK_DNS"]
    fail("MESOS_ZK_GATEWAY is not defined") unless ENV["MESOS_ZK_GATEWAY"]
    fail("MESOS_ZK_NETMASK is not defined") unless ENV["MESOS_ZK_NETMASK"]
    fail("KUBERNETES_1_IP is not defined") unless ENV["KUBERNETES_ETCD_1_IP"]
    fail("KUBERNETES_MASTER_IP is not defined") unless ENV["KUBERNETES_MASTER_IP"]

    puts "Starting to create a Kubernetes cluster"
    begin
      project = @seeder.project!
      props = construct_props(ENV["KUBERNETES_MASTER_IP"],ENV["KUBERNETES_ETCD_1_IP"])
      expected_etcd_count = 1
      if ENV["KUBERNETES_ETCD_2_IP"] != ""
        props["etcd_ip2"] = ENV["KUBERNETES_ETCD_2_IP"]
        expected_etcd_count += 1
        if ENV["KUBERNETES_ETCD_3_IP"] != ""
          props["etcd_ip3"] = ENV["KUBERNETES_ETCD_3_IP"]
          expected_etcd_count += 1
        end
      end
      cluster = project.create_cluster(
        name: random_name("kubernetes-"),
        type: "KUBERNETES",
        vm_flavor: @seeder.vm_flavor!.name,
        disk_flavor: @seeder.ephemeral_disk_flavor!.name,
        network_id: @seeder.network!.id,
        worker_count: 1,
        batch_size: nil,
        extended_properties: props
      )

      validate_cluster_responding(cluster, 1)
      validate_kube_api_responding(ENV["KUBERNETES_MASTER_IP"], 2 )

      validate_ssh()

      N_WORKERS = (ENV["N_SLAVES"] || 2).to_i
      resize_cluster(cluster, N_WORKERS, expected_etcd_count)

      puts "Create cluster 2 to test that two clusters can exist at the same time"
      # Cluster 2 uses SWARM_ETCD_1_IP for master and MESOS_ZK_1_IP for stcd
      props2 = construct_props(ENV["SWARM_ETCD_1_IP"],ENV["MESOS_ZK_1_IP"])
      cluster2 = project.create_cluster(
          name: random_name("kubernetes-"),
          type: "KUBERNETES",
          vm_flavor: @seeder.vm_flavor!.name,
          disk_flavor: @seeder.ephemeral_disk_flavor!.name,
          network_id: @seeder.network!.id,
          worker_count: 1,
          batch_size: nil,
          extended_properties: props2
      )
      validate_cluster_responding(cluster2, 1)
      validate_kube_api_responding(ENV["SWARM_ETCD_1_IP"], 2 )
      delete_cluster(cluster2)

      puts "validate that two tenants and two clusters situation"
      # TODO: add code here for two tenats case

      delete_cluster(cluster)

    rescue EsxCloud::Error => e
      EsxCloud::ClusterHelper.show_logs(@seeder.project, client)
      fail "KUBERNETES cluster integration Test failed. Error: #{e.message}"
    end
  end

  private

  # Curl to the Kubernetes api using env_master_ip to make sure that it is responding.
  # Check that there is exactly expect_node number of nodes from the api response
  # Node that the expected_node includes the master node for kubernetes
  def validate_kube_api_responding(env_master_ip, expected_node)
    puts "Check Kubernetes api is responding"
    get_node_count_cmd = "http://" + env_master_ip + ":8080/api/v1/nodes | grep nodeInfo | wc -l"
    total_node_count = `curl #{get_node_count_cmd} `
    expect(total_node_count.to_i).to eq expected_node
  end

  # This function validate that our cluster manager api is working properly
  # It also checks that the extended properties added later were put it properly
  def validate_cluster_responding(cluster_current, expected_worker_count)
    cid_current = cluster_current.id
    cluster_current = client.find_cluster_by_id(cid_current)
    expect(cluster_current.name).to start_with("kubernetes-")
    expect(cluster_current.type).to eq("KUBERNETES")
    expect(cluster_current.worker_count).to eq expected_worker_count
    expect(cluster_current.state).to eq "READY"
    expect(cluster_current.extended_properties["cluster_version"]).to eq ("v1.3.5")
    expect(cluster_current.extended_properties.length).to be > 12
  end

  def validate_ssh()
    puts "Check that host can ssh successfully"
    # Disabling strict_host_key_checking (:paranoid => false) and setting user_known_hosts_file to null to not validate
    # the host key as it will change with each lifecycle run.
    Net::SSH.start(ENV["KUBERNETES_MASTER_IP"], "root",
                   :keys => ["/tmp/test_rsa"],
                   :paranoid => false,
                   :user_known_hosts_file => ["/dev/null"]) do |ssh|
      # Getting here without an exception means we can connect with ssh successfully. If SSH failed, we would get an
      # exception like Authentication Failed or Connection Timeout and our tests will fail as we are catching the
      # exception and failing the test below.
      puts "SSH successful"
    end
  end

  def resize_cluster(cluster, expected_worker_count, expected_etcd_count)
    puts "Starting to resize a Kubernetes cluster"
    cid = cluster.id
    client.resize_cluster(cid, expected_worker_count)
    cluster = client.find_cluster_by_id(cid)
    expect(cluster.worker_count).to eq expected_worker_count

    puts "Waiting for cluster to become READY after resize"
    EsxCloud::ClusterHelper.wait_for_cluster_state(cid, "READY", 5, 120, client)

    puts "Getting Cluster VM list"
    etcd_count = 0
    master_count = 0
    worker_count = 0
    client.get_cluster_vms(cid).items.each do |i|
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
    expect(worker_count).to eq expected_worker_count
  end

  def delete_cluster(cluster)
    puts "Starting to delete a Kubernetes cluster"
    cluster_id = cluster.id
    client.delete_cluster(cluster_id)
    begin
      client.find_cluster_by_id(cluster_id)
      fail("KUBERNETES Cluster #{cluster_id} should be deleted")
    rescue EsxCloud::ApiError => e
      e.response_code.should == 404
    rescue EsxCloud::CliError => e
      e.output.should match("not found")
    end
  end

  def construct_props(master_ip, etcd_ip)
    public_key_contents = File.read("/tmp/test_rsa.pub")
    props = {
        "dns" => ENV["MESOS_ZK_DNS"],
        "gateway" => ENV["MESOS_ZK_GATEWAY"],
        "netmask" => ENV["MESOS_ZK_NETMASK"],
        "master_ip" => master_ip,
        "container_network" => "10.2.0.0/16",
        "etcd_ip1" => etcd_ip,
        "ssh_key" => public_key_contents
    }
    return props
  end
end
