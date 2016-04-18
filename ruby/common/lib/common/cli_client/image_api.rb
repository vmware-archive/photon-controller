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
    module ImageApi
      # @param [String] path
      # @param [String] name
      # @return [Image]
      def create_image(path, name = nil, image_replication = nil)
        if name.nil?
          name = random_name("image-")
        end

        cmd = "image upload '#{path}' -n '#{name}'"
        if image_replication
          cmd += " -i '#{image_replication}'"
        end
        run_cli(cmd)

        images = find_all_images.items.find_all { |image| image.name == name }
        if images.size > 1
          fail EsxCloud::CliError, "There are more than one image having the same name '#{name}'."
        end
        images[0]
      end

      # @param [String] id
      # @param [Hash] payload
      # @return [Image]
      def create_image_from_vm(id, payload)
        image_name = payload[:name]
        replication_type = payload[:replicationType]
        cmd = "vm create_image #{id} -n '#{image_name}' -r '#{replication_type}'"

        run_cli(cmd)

        images = find_all_images.items.find_all { |image| image.name == image_name }
        fail EsxCloud::CliError, "There are more than one image having the same name '#{image_name}'." if images.size > 1
        images.first
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

      # @return [ImageList]
      def find_images_by_name(name)
        @api_client.find_images_by_name(name)
      end

      # @param [String] id
      # @return [Boolean]
      def delete_image(id)
        run_cli("image delete '#{id}'")
        true
      end

      # @param [String] id
      # @param [String] state
      # @return [TaskList]
      def get_image_tasks(id, state = nil)
        @api_client.get_image_tasks(id, state)
      end

      private

      def random_name(prefix = "rn")
        prefix + SecureRandom.base64(12).tr("+/", "ab")
      end

    end
  end
end
