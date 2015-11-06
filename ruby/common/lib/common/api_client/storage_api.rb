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
    module StorageApi

      STORAGE_URL = "/resources/storage"

      # @param [Hash] payload
      # @return [Storage]
      def create_storage(payload)
        response = @http_client.post_json(STORAGE_URL, payload)
        check_response("Register storage  #{payload}", response, 201)
        JSON.parse(response.body)
      end

      # @return [StorageList]
      def find_all_storages
        response = @http_client.get(STORAGE_URL)
        check_response("Find all storages", response, 200)
        JSON.parse(response.body)
      end

      # @param [String] id
      # @return [Storage]
      def find_storage_by_id(id)
        response = @http_client.get("#{STORAGE_URL}/#{id}")
        check_response("Find storage by ID '#{id}'", response, 200)
        JSON.parse(response.body)
      end

      # @param [String] id
      # @param [String] property
      # @param [String] value
      # @return [Metadata]
      def update_storage_property(id, property, value)
        response = @http_client.put_json("#{STORAGE_URL}/#{id}/#{property}/#{value}", {})
        check_response("Update  property for storage '#{id}' with  '#{property}' = '#{value}'", response, 200)
        JSON.parse(response.body)
      end

      # @param [String] id
      # @return [Metadata]
      def find_storage_metadata_by_id(id)
        response = @http_client.get("#{STORAGE_URL}/#{id}/metadata")
        check_response("Find storage metadata by ID '#{id}'", response, 200)
        JSON.parse(response.body)
      end

      # @param [String] id
      # @param [String] property
      # @param [String] value
      # @return [Metadata]
      def update_storage_metadata_property(id, property, value)
        response = @http_client.put_json("#{STORAGE_URL}/#{id}/metadata/#{property}/#{value}", {})
        check_response("Update  metadata property for storage id '#{id}' with  '#{property}' = '#{value}'", response, 200)
        JSON.parse(response.body)
      end

      # @param [String] id
      def delete_storage(id)
        response = @http_client.delete("#{STORAGE_URL}/#{id}")
        check_response("Delete storage with id '#{id}'", response, 204)
      end
    end
  end
end
