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
  class ApiClient
    module ImageApi

      IMAGES_ROOT = "/images"

      # @param [String] image_path
      # @param [String] image_name
      # @return [Image]
      def create_image(image_path, image_name = nil, image_replication = nil)
        payload = {}
        if image_replication
          payload = {imageReplication: image_replication}
        end
        response = @http_client.upload(IMAGES_ROOT, image_path, image_name, payload)
        check_response("Upload image '#{image_path}'", response, 201)

        task = poll_response(response)
        find_image_by_id(task.entity_id)
      end

      # @param [String] id
      # @param [Hash] payload
      # @return [Image]
      def create_image_from_vm(id, payload)
        response = @http_client.post_json("/vms/#{id}/create_image", payload)
        check_response("Create image from VM #{id}", response, 201)

        task = poll_response(response)
        find_image_by_id(task.entity_id)
      end

      # @param [String] id
      # @return [Image]
      def find_image_by_id(id)
        response = @http_client.get("#{IMAGES_ROOT}/#{id}")
        check_response("Get image by ID '#{id}'", response, 200)

        Image.create_from_json(response.body)
      end

      # @return [ImageList]
      def find_all_images
        response = @http_client.get(IMAGES_ROOT)
        check_response("Find all images", response, 200)

        ImageList.create_from_json(response.body)
      end

      # @return [ImageList]
      def find_images_by_name(name)
        response = @http_client.get("#{IMAGES_ROOT}?name=#{name}")
        check_response("Get iamges by name '#{name}'", response, 200)

        ImageList.create_from_json(response.body)
      end

      # @param [String] id
      # @return [Boolean]
      def delete_image(id)
        response = @http_client.delete("#{IMAGES_ROOT}/#{id}")
        check_response("Delete image '#{id}'", response, 201)

        poll_response(response)
        true
      end

      # @param [String] id
      # @param [String] state
      # @return [TaskList]
      def get_image_tasks(id, state = nil)
        url = "#{IMAGES_ROOT}/#{id}/tasks"
        url += "?state=#{state}" if state
        response = @http_client.get(url)
        check_response("Get tasks for Image '#{id}'", response, 200)

        TaskList.create_from_json(response.body)
      end
    end
  end
end
