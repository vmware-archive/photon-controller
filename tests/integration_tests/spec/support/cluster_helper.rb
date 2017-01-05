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


module EsxCloud
  class ClusterHelper
    class << self
      KEY_FILE = "/tmp/test_rsa"

      def upload_kubernetes_image(client)
        fail("KUBERNETES_IMAGE is not defined") unless ENV["KUBERNETES_IMAGE"]
        Config.logger.info "Starting to Upload Kubernetes Image"
        client.create_image(ENV["KUBERNETES_IMAGE"], "photon-kubernetes-vm-disk1.vmdk", "EAGER")
      end

      def upload_mesos_image(client)
        fail("MESOS_IMAGE is not defined") unless ENV["MESOS_IMAGE"]
        Config.logger.info "Starting to Upload Mesos Image"
        client.create_image(ENV["MESOS_IMAGE"], "photon-mesos-vm-disk1.vmdk", "EAGER")
      end

      def upload_swarm_image(client)
        fail("SWARM_IMAGE is not defined") unless ENV["SWARM_IMAGE"]
        Config.logger.info "Starting to Upload Swarm Image"
        client.create_image(ENV["SWARM_IMAGE"], "photon-swarm-vm-disk1.vmdk", "EAGER")
      end

      def upload_harbor_image(client)
        fail("HARBOR_IMAGE is not defined") unless ENV["HARBOR_IMAGE"]
        Config.logger.info "Starting to Upload Harbor Image"
        client.create_image(ENV["HARBOR_IMAGE"], "photon-harbor-vm-disk1.vmdk", "EAGER")
      end

      def enable_cluster_type(client, deployment, image, cluster_type)
        spec = EsxCloud::ClusterConfigurationSpec.new(
            cluster_type,
            image.id)

        Config.logger.info "Spec: #{spec.to_hash}"
        Config.logger.info "Deployment: #{deployment.to_hash}"

        client.enable_cluster_type(deployment.id, spec.to_hash)
      end

      def disable_cluster_type(client, deployment, cluster_type)
        spec = EsxCloud::ClusterConfigurationSpec.new(
            cluster_type)

        Config.logger.info "Spec: #{spec.to_hash}"
        Config.logger.info "Deployment: #{deployment.to_hash}"

        client.disable_cluster_type(deployment.id, spec.to_hash)
      end

      def delete_cluster(client, cluster_id, cluster_type)
        puts "Starting to delete a "+ cluster_type +" cluster: " + cluster_id
        client.delete_cluster(cluster_id)
        begin
          client.find_cluster_by_id(cluster_id)
          fail(cluster_type + " Cluster #{cluster_id} should be deleted")
        rescue EsxCloud::ApiError => e
          e.response_code.should == 404
        rescue EsxCloud::CliError => e
          e.output.should match("not found")
        end
      end

      def validate_ssh(master_ip)
        # Disabling strict_host_key_checking (:paranoid => false) and setting user_known_hosts_file to null to not validate
        # the host key as it will change with each lifecycle run.
        Net::SSH.start(master_ip, "root",
                       :keys => ["/tmp/test_rsa"],
                       :paranoid => false,
                       :user_known_hosts_file => ["/dev/null"]) do |ssh|
          # Getting here without an exception means we can connect with ssh successfully. If SSH failed, we would get an
          # exception like Authentication Failed or Connection Timeout and our tests will fail as we are catching the
          # exception and failing the test below.
          puts "SSH successful"
        end
      end

      def construct_kube_properties(master_ip, etcd_ip, ca_cert = nil)
        public_key_contents = File.read("/tmp/test_rsa.pub")
        props = {
            "dns" => ENV["MESOS_ZK_DNS"],
            "gateway" => ENV["MESOS_ZK_GATEWAY"],
            "netmask" => ENV["MESOS_ZK_NETMASK"],
            "master_ip" => master_ip,
            "container_network" => "10.2.0.0/16",
            "etcd_ip1" => etcd_ip,
            "ssh_key" => public_key_contents,
            "registry_ca_cert" => ca_cert
        }
        return props
      end

      def show_logs(project, client)
        clusters = client.get_project_clusters(project.id).items
        clusters.map do |cluster|
          show_cluster_logs(cluster, client)
        end
      end

      def generate_temporary_ssh_key()
        puts "Generating ssh key"
        `ssh-keygen -f #{KEY_FILE} -N ''`
      end

      def remove_temporary_ssh_key()
        puts "Removing ssh key"
        File.delete(KEY_FILE, KEY_FILE + '.pub')
      end

      def remove_temporary_file(file_path)
        File.delete(file_path)
      end

      def copy_file(source_path, dest_path)
        FileUtils.cp(source_path, dest_path)
      end

      def wait_for_cluster_state(cluster_id, target_cluster_state, retry_interval, retry_count, client)
        cluster = client.find_cluster_by_id(cluster_id)

        until cluster.state == target_cluster_state || retry_count == 0 do
          sleep retry_interval
          cluster = client.find_cluster_by_id(cluster_id)
          retry_count -= 1
        end

        fail("Cluster #{cluster_id} did not become #{target_cluster_state} in time") if retry_count == 0
      end

      private

      def show_cluster_logs(cluster, client)
        begin
          vms = client.get_cluster_vms(cluster.id).items
          vms.map do |vm|
            puts "*************** " + vm.name + ", " + vm.id + ", " + vm.state + " ***************"
            connections = client.get_vm_networks(vm.id).network_connections.select { |n| !n.network.nil? }
            puts "ip address is " + connections.first.ip_address
            begin
              if File.exist?(KEY_FILE)
                ssh = Net::SSH.start(connections.first.ip_address, "root", :keys => [KEY_FILE],
                                     :paranoid => false, :user_known_hosts_file => ["/dev/null"])
              else
                ssh = Net::SSH.start(connections.first.ip_address, "root", :password => "vmware")
              end
              puts "=============== ifconfig ==============="
              res = ssh.exec!("ifconfig")
              puts res
              puts "=============== docker ps ==============="
              res = ssh.exec!("docker ps -a")
              puts res
              if cluster.type === "KUBERNETES"
                res = ssh.exec!("docker -H unix:///var/run/docker-bootstrap.sock ps -a")
                puts res
              elsif cluster.type === "SWARM"
                res = ssh.exec!("docker -H tcp://0.0.0.0:2375 ps -a")
                puts res
              end
              puts "=============== docker logs ==============="
              res = ssh.exec!("for container in $(docker ps -q); do docker logs $container; done")
              puts res
              if cluster.type === "KUBERNETES"
                res = ssh.exec!("for container in $(docker -H unix:///var/run/docker-bootstrap.sock ps -q); do docker -H unix:///var/run/docker-bootstrap.sock logs $container; done")
                puts res
              elsif cluster.type === "SWARM"
                res = ssh.exec!("for container in $(docker -H tcp://0.0.0.0:2375 ps -q); do docker -H tcp://0.0.0.0:2375 logs $container; done")
                puts res
              elsif cluster.type === "HARBOR"
                res = ssh.exec!("cat /var/log/harbor/*/*")
                puts res
              end
              puts "=============== start cluster node logs ==============="
              if cluster.type === "KUBERNETES"
                res = ssh.exec!("cat /var/log/start-kubernetes-node.log")
                puts res
              end
              if cluster.type === "HARBOR"
                res = ssh.exec!("cat /var/log/start-harbor.log")
                puts res
              end
              puts "=============== journalctl ==============="
              res = ssh.exec!("journalctl")
              puts res
              ssh.close
            rescue Exception => e
              puts e.message
              puts e.backtrace.inspect
            end
          end
        rescue Exception => e
          puts e.message
          puts e.backtrace.inspect
        end
      end
    end
  end
end
