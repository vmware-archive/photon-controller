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
require "json"
require "test_helpers"

describe "Kubernetes cluster-service lifecycle", cluster: true do

  before(:all) do
    @seeder = EsxCloud::SystemSeeder.instance
    @cleaner = EsxCloud::SystemCleaner.new(client)
    # This seeder2 is created to create tenant 2
    # It is an exact copy of the seeder config
    @seeder2 = EsxCloud::SystemSeeder.new(
        [
            EsxCloud::QuotaLineItem.new("vm.memory", 15000.0, "GB"),
            EsxCloud::QuotaLineItem.new("vm", 1000.0, "COUNT")
        ])

    @deployment = @seeder.deployment!
    @kubernetes_image = EsxCloud::ClusterHelper.upload_kubernetes_image(client)
    EsxCloud::ClusterHelper.enable_cluster_type(client, @deployment, @kubernetes_image, "KUBERNETES")
    EsxCloud::ClusterHelper.generate_temporary_ssh_key
  end

  after(:all) do
    puts "Starting to clean up Kubernetes Cluster lifecycle tests Env"
    # Deleting tenant2
    tmp_cleaner = EsxCloud::SystemCleaner.new(ApiClientHelper.management)
    ignoring_all_errors {
      tmp_cleaner.delete_tenant(@seeder2.tenant)
    }

    EsxCloud::ClusterHelper.disable_cluster_type(client, @deployment, "KUBERNETES")
    @cleaner.delete_image(@kubernetes_image)
    EsxCloud::ClusterHelper.remove_temporary_ssh_key
  end

  it 'should create/resize/delete Kubernetes cluster successfully' do
    fail("MESOS_ZK_DNS is not defined") unless ENV["MESOS_ZK_DNS"]
    fail("MESOS_ZK_GATEWAY is not defined") unless ENV["MESOS_ZK_GATEWAY"]
    fail("MESOS_ZK_NETMASK is not defined") unless ENV["MESOS_ZK_NETMASK"]
    fail("KUBERNETES_ETCD 1_IP is not defined") unless ENV["KUBERNETES_ETCD_1_IP"]
    fail("KUBERNETES_MASTER_IP is not defined") unless ENV["KUBERNETES_MASTER_IP"]

    puts "Starting to create a Kubernetes cluster"
    begin

      # Validate that the deployment API shows the cluster configurations.
      # We check both the "all deployments" and "deployment by ID" because there
      # was a bug in which they were returning different data
      deployment = client.find_all_api_deployments.items[0]
      expect(deployment.cluster_configurations.size).to eq 1
      expect(deployment.cluster_configurations[0].type).to eq "KUBERNETES"

      deployment = client.find_deployment_by_id(deployment.id)
      expect(deployment.cluster_configurations.size).to eq 1
      expect(deployment.cluster_configurations[0].type).to eq "KUBERNETES"

      project = @seeder.project!
      props = EsxCloud::ClusterHelper.construct_kube_properties(ENV["KUBERNETES_MASTER_IP"],ENV["KUBERNETES_ETCD_1_IP"])
      expected_etcd_count = 1
      if ENV["KUBERNETES_ETCD_2_IP"] != nil and ENV["KUBERNETES_ETCD_2_IP"] != ""
        props["etcd_ip2"] = ENV["KUBERNETES_ETCD_2_IP"]
        expected_etcd_count += 1
        if ENV["KUBERNETES_ETCD_3_IP"] != nil and ENV["KUBERNETES_ETCD_3_IP"] != ""
          props["etcd_ip3"] = ENV["KUBERNETES_ETCD_3_IP"]
          expected_etcd_count += 1
        end
      end
      puts "Creating Kubernetes cluster"
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

      validate_kube_cluster_info(cluster, 1, @seeder.vm_flavor!.name)
      validate_kube_api_responding(ENV["KUBERNETES_MASTER_IP"], 2)

      EsxCloud::ClusterHelper.validate_ssh(ENV["KUBERNETES_MASTER_IP"])

      N_WORKERS = (ENV["N_SLAVES"] || 2).to_i

      resize_cluster(cluster, N_WORKERS, expected_etcd_count)

      puts "Test that cluster background maintenance will restore deleted VMs"
      validate_trigger_maintenance(ENV["KUBERNETES_MASTER_IP"], N_WORKERS, cluster)

      puts "Test that a single tenant can have multiple clusters"
      # Cluster 2 uses SWARM_ETCD_1_IP for master and MESOS_ZK_1_IP for etcd
      kube2_master_ip = ENV["SWARM_ETCD_1_IP"]
      kube2_etcd_ip = ENV["MESOS_ZK_1_IP"]
      props2 = EsxCloud::ClusterHelper.construct_kube_properties(kube2_master_ip,kube2_etcd_ip)
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
      validate_kube_cluster_info(cluster2, 1, @seeder.vm_flavor!.name)
      validate_kube_api_responding(kube2_master_ip, 2 )

      # Validate that cluster 1 still works
      validate_kube_cluster_info(cluster, N_WORKERS, @seeder.vm_flavor!.name)
      validate_kube_api_responding(ENV["KUBERNETES_MASTER_IP"], N_WORKERS + 1 )

      EsxCloud::ClusterHelper.delete_cluster(client, cluster2.id, "KUBERNETES")

    rescue => e
      EsxCloud::ClusterHelper.show_logs(@seeder.project, client)
      fail "KUBERNETES cluster integration Test failed. Error: #{e.message}"
    end

    begin
      puts "Validate multi-tenancy for clusters: two tenants, and each has their own cluster"
      project2 = @seeder2.project!
      # cluster 3 has the same extended properties as cluster2
      # reuse the Swarmip and mesos ip for master ip and etcd ip.
      cluster3 = project2.create_cluster(
          name: random_name("kubernetes-"),
          type: "KUBERNETES",
          vm_flavor: @seeder2.vm_flavor!.name,
          disk_flavor: @seeder2.ephemeral_disk_flavor!.name,
          network_id: @seeder2.network!.id,
          worker_count: 1,
          batch_size: nil,
          extended_properties: props2
      )

      validate_kube_cluster_info(cluster3, 1, @seeder2.vm_flavor!.name)
      validate_kube_api_responding(kube2_master_ip, 2 )

      # Validate that cluster 1 is stilling responding
      validate_kube_cluster_info(cluster, N_WORKERS, @seeder.vm_flavor!.name)
      validate_kube_api_responding(ENV["KUBERNETES_MASTER_IP"], N_WORKERS + 1 )

      EsxCloud::ClusterHelper.delete_cluster(client, cluster3.id, "KUBERNETES")

      EsxCloud::ClusterHelper.delete_cluster(client, cluster.id, "KUBERNETES")

    rescue => e
      EsxCloud::ClusterHelper.show_logs(@seeder2.project, client)
      fail "KUBERNETES cluster integration Test failed. Error: #{e.message}"
    end
  end

  private

  # Curl to the Kubernetes api using env_master_ip to make sure that it is responding.
  # Check that there is exactly expect_node number of nodes from the api response
  # Node that the expected_node includes the master node for kubernetes
  def validate_kube_api_responding(env_master_ip, expected_node_count)
    puts "Validate Kubernetes api is responding"
    kube_end_point = "http://" + env_master_ip + ":8080"
    http_helper=EsxCloud::HttpClient.new(kube_end_point)
    response = http_helper.get("/api/v1/nodes")
    expect(response.code).to be 200
    total_node_count = JSON.parse(response.body)['items'].size
    # kubenetes api keeps an entry for any nodes it ever created. If one of the
    # creation failed, the entry would still be in items.
    # From observation, our resize_cluster code actually wipe out all existing worker
    # and create new ones. Thus, the entry for node_info would be at least at big
    # for the expected_node_count.
    expect(total_node_count.to_i).to be > expected_node_count - 1
  end

  # Removes a worker VM and check that trigger maintenance can recreate deleted VMs
  def validate_trigger_maintenance(master_ip, total_worker_count, cluster)
    vm = get_random_worker_vm(cluster)
    puts "Stopping random Kubernetes worker node: #{vm.name}"
    vm.stop!
    vm.delete
    wait_for_kube_worker_count(master_ip, total_worker_count - 1, 5, 120)
    client.trigger_maintenance(cluster.id)
    wait_for_kube_worker_count(master_ip, total_worker_count, 5, 120)
  end

  # This function validate that our cluster manager api is working properly
  # It also checks that the extended properties added later were put it properly
  def validate_kube_cluster_info(cluster_current, expected_worker_count, flavor)
    cid_current = cluster_current.id
    cluster_current = client.find_cluster_by_id(cid_current)
    expect(cluster_current.name).to start_with("kubernetes-")
    expect(cluster_current.type).to eq("KUBERNETES")
    expect(cluster_current.worker_count).to eq expected_worker_count
    expect(cluster_current.state).to eq "READY"
    expect(cluster_current.master_vm_flavor).to eq flavor
    expect(cluster_current.other_vm_flavor).to eq flavor
    expect(cluster_current.image_id).to eq @kubernetes_image.id
    expect(cluster_current.extended_properties["cluster_version"]).not_to be_empty
    expect(cluster_current.extended_properties.length).to be > 12
  end

  # Waits for the Kubernetes API to report the number of active worker nodes to be target_worker_count
  def wait_for_kube_worker_count(master_ip, target_worker_count, retry_interval, retry_count)
    puts "Waiting for Kubernetes client to report available worker count #{target_worker_count}"
    worker_count = get_active_worker_node_count(master_ip)

    until worker_count == target_worker_count || retry_count == 0 do
      sleep retry_interval
      worker_count = get_active_worker_node_count(master_ip)
      retry_count -= 1
    end

    fail("Kubernetes at #{master_ip} failed to report #{target_worker_count} workers in time") if retry_count == 0
  end

  # Returns the number of active worker nodes reported by the Kubernetes API
  def get_active_worker_node_count(master_ip)
    kube_end_point = "http://" + master_ip + ":8080"
    http_helper=EsxCloud::HttpClient.new(kube_end_point)
    response = http_helper.get("/api/v1/nodes")
    expect(response.code).to be 200
    worker_node_count = 0

    JSON.parse(response.body)['items'].each do |item|
      # Check that the node is a worker
      if item['metadata']['labels']['vm-hostname'].start_with?("worker")
        conditions = item['status']['conditions']
        conditions.each do |condition|
          # Check that the node is ready
          if condition['type'] == "Ready" && condition['status'] == "True"
            worker_node_count += 1
          end
        end
      end
    end
    return worker_node_count
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

  # Gets a worker vm from the specified cluster
  # Returns a VM
  def get_random_worker_vm(cluster)
    client.get_cluster_vms(cluster.id).items.each do |vm|
      if vm.name.start_with?("worker")
         return vm
      end
    end

    fail("No worker vms found for cluster #{cluster.id}")
  end

end
