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
  class GoCliClient
    module ClusterApi

      # @param [String] project_id
      # @param [Hash] payload
      # @return [Cluster]
      def create_cluster(project_id, payload)
        project = find_project_by_id(project_id)
        tenant = @project_to_tenant[project.id]

        cmd = "cluster create -t '#{tenant.name}' -p '#{project.name}' " +
            "-n '#{payload[:name]}' -k '#{payload[:type]}' -v '#{payload[:vmFlavor]}' " +
            "-d '#{payload[:diskFlavor]}' -w '#{payload[:vmNetworkId]}' -c #{payload[:workerCount]} " +
            "--dns '#{payload[:extendedProperties]["dns"]}' " +
            "--gateway '#{payload[:extendedProperties]["gateway"]}' " +
            "--netmask '#{payload[:extendedProperties]["netmask"]}' "
        if payload[:type] === "KUBERNETES"
          cmd += "--etcd1 '#{payload[:extendedProperties]["etcd_ip1"]}' "
          cmd += "--etcd2 '#{payload[:extendedProperties]["etcd_ip2"]}' "
          cmd += "--etcd3 '#{payload[:extendedProperties]["etcd_ip3"]}' "
          cmd += "--master-ip '#{payload[:extendedProperties]["master_ip"]}' "
          cmd += "--container-network '#{payload[:extendedProperties]["container_network"]}'"
        elsif payload[:type] === "MESOS"
          cmd += "--zookeeper1 '#{payload[:extendedProperties]["zookeeper_ip1"]}' "
          cmd += "--zookeeper2 '#{payload[:extendedProperties]["zookeeper_ip2"]}' "
          cmd += "--zookeeper3 '#{payload[:extendedProperties]["zookeeper_ip3"]}'"
        elsif payload[:type] === "SWARM"
          cmd += "--etcd1 '#{payload[:extendedProperties]["etcd_ip1"]}' "
          cmd += "--etcd2 '#{payload[:extendedProperties]["etcd_ip2"]}' "
          cmd += "--etcd3 '#{payload[:extendedProperties]["etcd_ip3"]}'"
        end

        cluster_id = run_cli(cmd).split("\n")[0]
        find_cluster_by_id(cluster_id)
      end

      # @param [String] id
      # @return [Cluster]
      def find_cluster_by_id(id)
        result = run_cli("cluster show #{id}")

        get_cluster_from_response(result)
      end

      # @param [String] id
      # @return [VmList]
      def get_cluster_vms(id)
        result = run_cli ("cluster list_vms '#{id}'")

        get_vm_list_from_response(result)
      end

      # @param [String] id
      # @param [int] new_worker_count
      # @return [Boolean]
      def resize_cluster(id, new_worker_count)
        run_cli("cluster resize '#{id}' '#{new_worker_count}'")
        true
      end

      # @param [String] id
      # @return [Boolean]
      def delete_cluster(id)
        run_cli("cluster delete '#{id}'")
        true
      end

      private

      def get_cluster_from_response(result)
        result.slice! "\n"
        cluster_attributes = result.split("\t", -1)
        cluster_hash = Hash.new
        cluster_hash["id"]                 = cluster_attributes[0] unless cluster_attributes[0] == ""
        cluster_hash["name"]               = cluster_attributes[1] unless cluster_attributes[1] == ""
        cluster_hash["state"]              = cluster_attributes[2] unless cluster_attributes[2] == ""
        cluster_hash["type"]               = cluster_attributes[3] unless cluster_attributes[3] == ""
        cluster_hash["workerCount"]        = cluster_attributes[4].to_i unless cluster_attributes[4] == ""
        cluster_hash["extendedProperties"] = extendedProperties_to_hash(cluster_attributes[5])

        Cluster.create_from_hash(cluster_hash)
      end

      # @param [String] properties
      # @return hash
      def extendedProperties_to_hash(properties)
        hash_new = Hash.new
        if properties.to_s != ''
          properties.split(' ').each { |property|
            values = property.split(':')
            hash_new.merge!({ values[0] => values[1]})}
        end
        hash_new
      end
    end
  end
end
