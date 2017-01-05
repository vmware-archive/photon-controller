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
require "net/http"
require "spec_helper"
require "test_helpers"

# End to End integration tests of Harbor cluster working with Kubernetes cluster.
# Deploys harbor cluster and then deploys Kubernetes cluster with harbor registry ca_cert.
# Logins to harbor registry from devbox and uploads the busy box image to harbor.
# Creates busy box application pod on kubernetes cluster and runs nslookup command
# remotely to confirm the application is up and running.
describe "Harbor-Kube cluster-service lifecycle", cluster: true, devbox: true do

  before(:all) do
    @seeder = EsxCloud::SystemSeeder.instance
    @cleaner = EsxCloud::SystemCleaner.new(client)
    @deployment = @seeder.deployment!
    @harbor_image = EsxCloud::ClusterHelper.upload_harbor_image(client)
    @kubernetes_image = EsxCloud::ClusterHelper.upload_kubernetes_image(client)
    EsxCloud::ClusterHelper.enable_cluster_type(client, @deployment, @harbor_image, "HARBOR")
    EsxCloud::ClusterHelper.enable_cluster_type(client, @deployment, @kubernetes_image, "KUBERNETES")
    EsxCloud::ClusterHelper.generate_temporary_ssh_key
  end

  after(:all) do
    puts "Starting to clean up Harbor-Kube Cluster lifecycle tests Env"
    system_cleaner = EsxCloud::SystemCleaner.new(ApiClientHelper.management)
    EsxCloud::ClusterHelper.disable_cluster_type(client, @deployment, "KUBERNETES")
    @cleaner.delete_image(@kubernetes_image)
    EsxCloud::ClusterHelper.disable_cluster_type(client, @deployment, "HARBOR")
    @cleaner.delete_image(@harbor_image)
    EsxCloud::ClusterHelper.remove_temporary_ssh_key
  end

  it 'should create/delete Harbor cluster successfully' do
    fail("MESOS_ZK_DNS is not defined") unless ENV["MESOS_ZK_DNS"]
    fail("MESOS_ZK_GATEWAY is not defined") unless ENV["MESOS_ZK_GATEWAY"]
    fail("MESOS_ZK_NETMASK is not defined") unless ENV["MESOS_ZK_NETMASK"]
    fail("SWARM_ETCD_1_IP is not defined") unless ENV["SWARM_ETCD_1_IP"]
    fail("KUBERNETES_MASTER_IP is not defined") unless ENV["KUBERNETES_MASTER_IP"]
    fail("KUBERNETES_ETCD_1_IP is not defined") unless ENV["KUBERNETES_ETCD_1_IP"]

    begin
      # We don't have IP addresses set aside for Harbor, so we re-use the Kubernetes IP addresses
      harbor_master_ip = ENV["SWARM_ETCD_1_IP"]
      kubernetes_master_ip = ENV["KUBERNETES_MASTER_IP"]
      kubernetes_etcd_ip = ENV["KUBERNETES_ETCD_1_IP"]

      puts "Create a Harbor cluster"
      harbor_props = construct_harbor_properties(harbor_master_ip)
      project = @seeder.project!
      harbor_cluster = project.create_cluster(
          name: random_name("harbor-"),
          type: "HARBOR",
          vm_flavor: @seeder.vm_flavor!.name,
          disk_flavor: @seeder.ephemeral_disk_flavor!.name,
          network_id: @seeder.network!.id,
          batch_size: nil,
          extended_properties: harbor_props
      )
      validate_harbor_cluster_info(harbor_cluster.id)
      export_ca_cert(harbor_cluster.id)
      EsxCloud::ClusterHelper.validate_ssh(harbor_master_ip)

      puts "Create a kubernetes cluster"
      registry_ca_cert = File.read("/tmp/harbor_ca_cert.crt")
      kube_props = EsxCloud::ClusterHelper.construct_kube_properties(kubernetes_master_ip, kubernetes_etcd_ip, registry_ca_cert)
      kubernetes_cluster = project.create_cluster(
          name: random_name("kubernetes-"),
          type: "KUBERNETES",
          vm_flavor: @seeder.vm_flavor!.name,
          disk_flavor: @seeder.ephemeral_disk_flavor!.name,
          network_id: @seeder.network!.id,
          worker_count: 1,
          batch_size: nil,
          extended_properties: kube_props
      )
      validate_kube_cluster_info(kubernetes_cluster, 1, @seeder.vm_flavor!.name)
      EsxCloud::ClusterHelper.validate_ssh(kubernetes_master_ip)

      puts "Copying harbor_ca_cert from local machine to management vm"
      copy_harbor_ca_cert_cmd = "sshpass -p \"vmware\" scp -o StrictHostKeyChecking=no -o UserKnownHostsFile=/dev/null /tmp/harbor_ca_cert.crt esxcloud@#{ENV['API_ADDRESS']}:/tmp/"
      puts copy_harbor_ca_cert_cmd
      `#{copy_harbor_ca_cert_cmd}`

      puts "Copying priv key (for kube nodes) test_rsa from local machine to management vm"
      copy_test_rsa_cmd = "sshpass -p \"vmware\" scp -o StrictHostKeyChecking=no -o UserKnownHostsFile=/dev/null /tmp/test_rsa esxcloud@#{ENV['API_ADDRESS']}:/tmp/"
      puts copy_test_rsa_cmd
      `#{copy_test_rsa_cmd}`

      create_kubernetes_application_workflow()

      EsxCloud::ClusterHelper.remove_temporary_file("/tmp/harbor_ca_cert.crt")
      EsxCloud::ClusterHelper.remove_temporary_file("/tmp/busybox_updated.yaml")
    rescue EsxCloud::Error => e
      EsxCloud::ClusterHelper.show_logs(@seeder.project, client)
      fail "Harbor-Kube cluster integration Test failed. Error: #{e.message}"
    ensure
      # Since we are sharing the ips KUBERNETES_MASTER_IP, KUBERNETES_ETCD_1_IP and SWARM_ETCD_1_IP between different cluster
      # lifecycle tests, we need to ensure that clusters created in this test get deleted so that those test which may run
      # after this test does not fail even if this test fails.
      if harbor_cluster.id != nil
        EsxCloud::ClusterHelper.delete_cluster(client, harbor_cluster.id, "HARBOR")
      end
      if kubernetes_cluster.id != nil
        EsxCloud::ClusterHelper.delete_cluster(client, kubernetes_cluster.id, "KUBERNETES")
      end
    end
  end

  def export_ca_cert(cluster_id)
    cluster = client.find_cluster_by_id(cluster_id)
    File.write("/tmp/harbor_ca_cert.crt", cluster.extended_properties["ca_cert"])
  end

  # Creating this method separately from harbor_cluster_lifecycle spec
  # because we need admin_pasword hardcoded here, so that we can login
  # TODO: Use environment variable for harbor admin password and merge this method to harbor_cluster_lifecycle_spec
  def construct_harbor_properties(master_ip)
    public_key_contents = File.read("/tmp/test_rsa.pub")
    props = {
        "dns" => ENV["MESOS_ZK_DNS"],
        "gateway" => ENV["MESOS_ZK_GATEWAY"],
        "netmask" => ENV["MESOS_ZK_NETMASK"],
        "master_ip" => master_ip,
        "ssh_key" => public_key_contents,
        "admin_password" => "Harbor12345"
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

  def create_kubernetes_application_workflow()
    server = ENV['API_ADDRESS']
    user_name = "esxcloud"
    password = "vmware"

    # Adding user to docker group in a separate ssh session because it does not takes effect in same ssh session
    Net::SSH.start(server, user_name, {password: password, user_known_hosts_file: "/dev/null"}) do |ssh|
      add_current_user_to_docker_grp(ssh)
    end

    Net::SSH.start(server, user_name, {password: password, user_known_hosts_file: "/dev/null"}) do |ssh|
      copy_ca_cert_to_devbox(ssh)
      pull_and_push_busybox_image(ssh)
      copy_over_the_kubectl_from_master_node_to_devbox(ssh)
      create_kubernetes_application(ssh)
    end
  end

  def add_current_user_to_docker_grp(ssh)
    puts "Add current user to docker group"
    add_user_cmd = "sudo usermod -aG docker $(whoami)"
    ssh.exec!(add_user_cmd)
  end

  def copy_ca_cert_to_devbox(ssh)
    puts "Creating ca_cert forlder in devbox to be used by docker"
    mk_folder_cmd = "sudo mkdir -p /etc/docker/certs.d/#{ENV['SWARM_ETCD_1_IP']}/"
    puts mk_folder_cmd
    output = ssh.exec!(mk_folder_cmd)
    puts output

    puts "Copying harbor_ca_cert to the above created folder"
    copy_cmd = "sudo cp /tmp/harbor_ca_cert.crt /etc/docker/certs.d/#{ENV['SWARM_ETCD_1_IP']}/"
    puts copy_cmd
    output = ssh.exec!(copy_cmd)
    puts output
  end

  def pull_and_push_busybox_image(ssh)

    puts "Login in harbor registry"
    login_cmd = "docker login -u admin -p Harbor12345 https://#{ENV['SWARM_ETCD_1_IP']}"
    puts login_cmd
    output = ssh.exec!(login_cmd)
    puts output
    expect(output).to include("Login Succeeded")

    puts "pulling busybox image from docker hub"
    pull_cmd = "docker pull busybox"
    puts pull_cmd
    output = ssh.exec!(pull_cmd)
    puts output


    puts "tagging image"
    tag_cmd = "docker tag busybox #{ENV['SWARM_ETCD_1_IP']}/library/busybox"
    puts tag_cmd
    output = ssh.exec!(tag_cmd)
    puts output


    puts "pushing busybox image to harbor registry"
    push_cmd = "docker push #{ENV['SWARM_ETCD_1_IP']}/library/busybox"
    puts push_cmd
    output = ssh.exec!(push_cmd)
    puts output
    expect(output).to include("Pushed")
  end

  def copy_over_the_kubectl_from_master_node_to_devbox(ssh)

    puts "copying the kubectl from master node to devbox"
    scp_cmd = "sudo scp -i /tmp/test_rsa -o StrictHostKeyChecking=no -o UserKnownHostsFile=/dev/null root@#{ENV['KUBERNETES_MASTER_IP']}:~/kubectl ~/"
    puts scp_cmd
    output = ssh.exec!(scp_cmd)
    puts output

    puts "changing permission for kubectl"
    chmod_cmd = "sudo chmod +x kubectl"
    puts chmod_cmd
    output = ssh.exec!(chmod_cmd)
    puts output
  end

  def create_kubernetes_application(ssh)

    populate_busybox_config_file()

    puts "creating imagePullSecret"
    secret_cmd = "./kubectl create secret docker-registry myregistrykey --docker-server=#{ENV['SWARM_ETCD_1_IP']} --docker-username=admin --docker-password=Harbor12345 --docker-email=vrai@vmware.com -s http://#{ENV['KUBERNETES_MASTER_IP']}:8080"
    puts secret_cmd
    output = ssh.exec!(secret_cmd)
    puts output

    puts "creating busybox application"
    create_cmd = "./kubectl -s http://#{ENV['KUBERNETES_MASTER_IP']}:8080 create -f /tmp/busybox_updated.yaml"
    puts create_cmd
    output = ssh.exec!(create_cmd)
    puts output
    expect(output).to include("created")

    puts "Checking busybox status"
    if !check_kube_app_busybox_is_ready?(ssh)
      puts "Kubernetes app busybox did not become READY after polling for status 300 iterations."
    end

    puts "list pods running on kubernetes"
    list_cmd = "./kubectl -s http://#{ENV['KUBERNETES_MASTER_IP']}:8080 get pods"
    puts list_cmd
    output = ssh.exec!(list_cmd)
    puts output
    expect(output).to include("Running")

    puts "running nslookup on busybox"
    nslookup_cmd = "./kubectl -s http://#{ENV['KUBERNETES_MASTER_IP']}:8080 exec busybox -- nslookup kubernetes.default"
    puts nslookup_cmd
    output = ssh.exec!(nslookup_cmd)
    puts output
    expect(output).to include("kube-dns.kube-system.svc.cluster.local")
    expect(output).to include("kubernetes.default.svc.cluster.local")
  end

  def populate_busybox_config_file()
    busybox_yaml_path = "#{ENV['WORKSPACE']}/tests/integration_tests/spec/api/cluster/busybox.yaml"
    busybox_yaml_content = File.read(busybox_yaml_path)

    busybox_yaml_content["$HARBOR_MASTER_IP"] = ENV["SWARM_ETCD_1_IP"]

    new_busybox_yaml_path = "/tmp/busybox_updated.yaml"
    File.write(new_busybox_yaml_path, busybox_yaml_content)

    puts "Copying busybox_updated.yaml from local machine to management vm"
    copy_busybox_yaml_cmd = "sshpass -p \"vmware\" scp -o StrictHostKeyChecking=no -o UserKnownHostsFile=/dev/null /tmp/busybox_updated.yaml esxcloud@#{ENV['API_ADDRESS']}:/tmp/"
    puts copy_busybox_yaml_cmd
    `#{copy_busybox_yaml_cmd}`
  end

  def check_kube_app_busybox_is_ready?(ssh)
    retry_max = 300
    retry_cnt = 0
    while retry_cnt < retry_max
      check_status_cmd = "./kubectl -s http://#{ENV['KUBERNETES_MASTER_IP']}:8080 get pods busybox"
      output = ssh.exec!(check_status_cmd)
      puts output
      if output.include? "Running"
        puts "Pod busybox is running"
        return true
      end
    end
    return false
  end
end
