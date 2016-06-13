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
  module Dcp
    class Client
      LOCAL_QUERY_TASK_LINK = "/core/local-query-tasks"

      def initialize(endpoint)
        @endpoint = endpoint.chomp("/")
        @http_client = EsxCloud::HttpClient.new(endpoint)
      end

      def get(service_link)
        response = @http_client.get service_link
        check_response "get", response, 200

        JSON.parse response.body
      end

      def post(service_link, payload)
        response = @http_client.post_json service_link, payload
        check_response "post", response, [200, 201]

        JSON.parse response.body
      end

      def delete(service_link)
        response = @http_client.delete(service_link, nil, {}) # send a {} in body to actually delete the object
        check_response "delete", response, 200

        JSON.parse response.body
      end

      def patch(service_link, payload)
        response = @http_client.patch_json(service_link, payload)
        check_response "patch", response, 200

        JSON.parse response.body
      end

      def query(terms, broadcast = false)
        optionsList = []
        optionsList << "BROADCAST" if broadcast

        payload = {
            taskInfo: {
              isDirect: true,
            },
            querySpec: {
              options: optionsList,
              query: {
                booleanClauses: terms,
              },
            },
        }

        response = @http_client.post_json(LOCAL_QUERY_TASK_LINK, payload)
        check_response "post", response, 200

        JSON.parse response.body
      end

      def poll_task(service_link, sleep = 1, retries = 5, backoff = 1, target_state = ["FINISHED", "FAILED", "CANCELED"], &blk)
        retries.times do
          response = @http_client.get service_link
          if response.code == 200
            body = JSON.parse(response.body)

            if block_given?
              done = blk.call(body)
            else
              done = target_state.include? body["taskState"]["stage"]
            end

            if done
              return body
            end
          end

          sleep sleep
          sleep *= backoff
        end

        raise TimeoutError.new
      end

      def check_response(operation, response, good_codes = [])
        return if Array(good_codes).include?(response.code)

        begin
          errors = JSON.parse(response.body)
        rescue JSON::ParserError
          errors = []
        end
        message = "#{operation}: HTTP #{response.code}: #{errors}"

        raise StandardError.new message
      end
    end
  end
end
