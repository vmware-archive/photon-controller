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
    module ImageApi
      # @param [String] path
      # @param [String] name
      # @return [Image]
      def create_image(path, name = nil, image_replication = nil)
        @api_client.create_image(path, name, image_replication)
      end

      # @param [String] id
      # @param [Hash] payload
      # @return [Image]
      def create_image_from_vm(id, payload)
        @api_client.create_image_from_vm(id, payload)
      end

      # @param [String] id
      # @return [Image]
      def find_image_by_id(id)
        @api_client.find_image_by_id(id)
      end

      # @return [ImageList]
      def find_all_images
        @api_client.find_all_images
      end

      # @param [String] id
      # @return [Boolean]
      def delete_image(id)
        @api_client.delete_image(id)
      end

      # @param [String] id
      # @param [String] state
      # @return [TaskList]
      def get_image_tasks(id, state = nil)
        @api_client.get_image_tasks(id, state)
      end

    end
  end
end
