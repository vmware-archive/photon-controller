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
      def upload_kubernetes_image(client)
        fail("KUBERNETES_IMAGE is not defined") unless ENV["KUBERNETES_IMAGE"]
        Config.logger.info "Starting to Upload Kubernetes Image"
        client.create_image(ENV["KUBERNETES_IMAGE"], "`basename #{KUBERNETES_IMAGE}`", "EAGER")
      end

      def upload_mesos_image(client)
        fail("MESOS_IMAGE is not defined") unless ENV["MESOS_IMAGE"]
        Config.logger.info "Starting to Upload Mesos Image"
        client.create_image(ENV["MESOS_IMAGE"], "`basename #{MESOS_IMAGE}`", "EAGER")
      end

      def upload_swarm_image(client)
        fail("SWARM_IMAGE is not defined") unless ENV["SWARM_IMAGE"]
        Config.logger.info "Starting to Upload Swarm Image"
        client.create_image(ENV["SWARM_IMAGE"], "`basename #{SWARM_IMAGE}`", "EAGER")
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

      def show_logs(project, client)
        clusters = client.get_project_clusters(project.id).items
        clusters.map do |cluster|
          show_cluster_logs(cluster, client)
        end
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
              ssh = Net::SSH.start(connections.first.ip_address, "root", :password => "vmware")
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
