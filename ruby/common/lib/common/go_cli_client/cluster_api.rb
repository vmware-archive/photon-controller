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
        @api_client.create_cluster(id)
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
