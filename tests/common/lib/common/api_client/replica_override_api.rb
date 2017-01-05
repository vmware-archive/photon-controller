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
    module ReplicaOverrideApi

      ALLOWED_OVERRIDES = %w(Zookeeper RootScheduler Chairman ManagementApi Housekeeper)
      REPLICA_OVERRIDE_URL = "/deployment/overrides"

      def set_replica_override(job, count)
        check_job(job)

        post_url = "#{REPLICA_OVERRIDE_URL}/#{job}/replicas/#{count}"
        response = @http_client.post(post_url, nil)
        check_response("Set Replica Override", response, 200)
      end

      def get_replica_overrides
        response = @http_client.get("#{REPLICA_OVERRIDE_URL}")
        check_response("Get all deployment flavors", response, 200)
        JSON.parse(response.body)
      end

      def delete_replica_override(job)
        check_job(job)
        response = @http_client.delete("#{REPLICA_OVERRIDE_URL}/#{job}/replicas")
        check_response("Get deployment flavor by id", response, 204)
      end


      private

      def check_job(job)
        found = false
        ALLOWED_OVERRIDES.each { |j| found = true if j == job }

        unless found
          puts "Unknown job: #{job}. Allowed jobs: #{ALLOWED_OVERRIDES}"
          raise "Unknown job: #{job}"
        end
      end

    end
  end
end
