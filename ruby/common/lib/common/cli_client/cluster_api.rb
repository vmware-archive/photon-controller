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
  class CliClient
    module ClusterApi

      # @param [String] project_id
      # @param [Hash] payload
      # @return [Cluster]
      def create_cluster(project_id, payload)
        project = find_project_by_id(project_id)
        tenant = @project_to_tenant[project.id]

        cmd = "cluster create -t '#{tenant.name}' -p '#{project.name}' " +
          "-n '#{payload[:name]}' -k '#{payload[:type]}' -v '#{payload[:vm_flavor]}' " +
          "-d '#{payload[:disk_flavor]}' -w '#{payload[:network_id]}' -s #{payload[:slave_count]} " +
          "--dns '#{payload[:extended_properties]["dns"]}' " +
          "--gateway '#{payload[:extended_properties]["gateway"]}' " +
          "--netmask '#{payload[:extended_properties]["netmask"]}' "
        if payload[:type] === "KUBERNETES"
          cmd += "--etcd1 '#{payload[:extended_properties]["etcd_ip1"]}' "
          cmd += "--etcd2 '#{payload[:extended_properties]["etcd_ip2"]}' "
          cmd += "--etcd3 '#{payload[:extended_properties]["etcd_ip3"]}' "
          cmd += "--master-ip '#{payload[:extended_properties]["master_ip"]}' "
          cmd += "--container-network '#{payload[:extended_properties]["container_network"]}'"
        elsif payload[:type] === "MESOS"
          cmd += "--zookeeper1 '#{payload[:extended_properties]["zookeeper_ip1"]}' "
          cmd += "--zookeeper2 '#{payload[:extended_properties]["zookeeper_ip2"]}' "
          cmd += "--zookeeper3 '#{payload[:extended_properties]["zookeeper_ip3"]}'"
        elsif payload[:type] === "SWARM"
          cmd += "--etcd1 '#{payload[:extended_properties]["etcd_ip1"]}' "
          cmd += "--etcd2 '#{payload[:extended_properties]["etcd_ip2"]}' "
          cmd += "--etcd3 '#{payload[:extended_properties]["etcd_ip3"]}'"
        end

        cluster_id = (run_cli(cmd)).split("'")[1]
        find_cluster_by_id(cluster_id)
      end

      # @param [String] id
      # @return [Cluster]
      def find_cluster_by_id(id)
        @api_client.find_cluster_by_id(id)
      end

      # @param [String] id
      # @return [VmList]
      def get_cluster_vms(id)
        @api_client.get_cluster_vms(id)
      end

      # @param [String] id
      # @param [int] new_slave_count
      # @return [Boolean]
      def resize_cluster(id, new_slave_count)
        @api_client.resize_cluster(id, new_slave_count)
      end

      # @param [String] id
      # @return [Boolean]
      def delete_cluster(id)
        @api_client.delete_cluster(id)
      end
    end
  end
end
