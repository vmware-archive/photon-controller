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
  class ApiRoute

    attr_accessor :action, :uri, :rc_admin, :rc_admin2, :rc_tenant_admin, :rc_project_user, :rc_guest_user

    # @param [Symbol] action
    # @param [String] uri
    # @param [Integer] rc_admin
    # @param [Integer] rc_admin2
    # @param [Integer] rc_tenant_admin
    # @param [Integer] rc_project_user
    # @param [Integer] rc_guest_user
    def initialize(action, uri, rc_admin, rc_admin2, rc_tenant_admin, rc_project_user, rc_guest_user)
      @action = action
      @uri = uri
      @rc_admin = rc_admin
      @rc_admin2 = rc_admin2
      @rc_tenant_admin = rc_tenant_admin
      @rc_project_user = rc_project_user
      @rc_guest_user = rc_guest_user
    end
  end
end
