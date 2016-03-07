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
    module AuthApi

      # @return [AuthInfo]
      def get_auth_info
        result = run_cli("auth show")
        get_auth_info_from_response(result)
      end

      private

      def get_auth_info_from_response(result)
        result.slice! "\n"
        values = result.split("\t", -1)
        auth_hash = Hash.new
        auth_hash["enabled"]  = to_boolean(values[0])
        auth_hash["endpoint"] = values[1] unless values[1] == ""
        auth_hash["port"]     = values[2].to_i unless values[2] == ""

        AuthInfo.create_from_hash(auth_hash)
      end
    end
  end
end
