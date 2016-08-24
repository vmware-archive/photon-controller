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

require 'yaml'

module EsxCloud
  class ApiClient
    module ClusterApi

      CLUSTERS_ROOT = "/clusters"

      # @param [String] project_id
      # @param [Hash] payload
      # @return [Cluster]
      def create_cluster(project_id, payload)
        response = @http_client.post_json("/projects/#{project_id}/clusters", payload)
        check_response("Create Cluster (#{project_id}) #{payload}", response, 201)

        task = poll_response(response)
        find_cluster_by_id(task.entity_id)
      end

      # @param [String] id
      # @return [Cluster]
      def find_cluster_by_id(id)
        response = @http_client.get("#{CLUSTERS_ROOT}/#{id}")
        check_response("Get cluster by ID '#{id}'", response, 200)

        Cluster.create_from_json(response.body)
      end

      # @param [String] id
      # @return [VmList]
      def get_cluster_vms(id)
        response = @http_client.get("#{CLUSTERS_ROOT}/#{id}/vms")
        check_response("Find all VMs for cluster '#{id}'", response, 200)

        VmList.create_from_json(response.body)
      end

      # @param [String] id
      # @param [int] new_worker_count
      # @return [Boolean]
      def resize_cluster(id, new_worker_count)
        payload = {newWorkerCount: new_worker_count}
        response = @http_client.post_json("#{CLUSTERS_ROOT}/#{id}/resize", payload)
        check_response("Resize cluster '#{id}'", response, 201)

        poll_response(response)
        true
      end

      # @param [String] id
      # @return [Boolean]
      def delete_cluster(id)
        response = @http_client.delete("#{CLUSTERS_ROOT}/#{id}")
        check_response("Delete cluster '#{id}'", response, 201)

        poll_response(response)
        true
      end
    end
  end
end
