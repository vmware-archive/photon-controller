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
    module ImportApi

      # @param [Hash] options
      # @return [Task] task
      def import_from(options)
        post_url = "/configuration/import/#{options[:from]}"
        if options[:from] == "yaml"
          payload = IO.read(options[:url])
        else
          options[:url].chomp!("/")
          if options[:from] == "vcenter"
            payload = "#{options[:url]},#{options[:username]},#{options[:password]}"
          elsif options[:from] == "openstack"
            payload = "#{options[:url]},#{options[:tenant]},#{options[:username]},#{options[:password]}"
          end
        end
        response = @http_client.post(post_url, payload)

        check_response("Import configuration ", response, 201)

        task = JSON.parse(response.body)
        puts "Import task: #{task["id"]}"

        poll_response(response)
      end

      # @param [String] file
      def export_to(file)
        get_url = "/configuration/export"
        response = @http_client.get(get_url)
        check_response("Export configuration ", response, 200)
        IO.write(file, response.body())
      end
    end
  end
end
