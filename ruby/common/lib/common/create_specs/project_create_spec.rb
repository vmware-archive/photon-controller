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
  class ProjectCreateSpec

    attr_accessor :name, :resource_ticket_name, :limits, :security_groups

    # @param [String] name
    # @param [String] resource_ticket_name
    # @param [Array<QuotaLineItem>] limits
    # @param [Array<SecurityGroup>]
    def initialize(name, resource_ticket_name, limits, security_groups = nil)
      # TODO(olegs): support 'subdivide' shortcut
      @name = name
      @resource_ticket_name = resource_ticket_name
      @limits = limits
      @security_groups = security_groups
    end

    def to_hash
      {
        :name => name,
        :resourceTicket => {
            :name => @resource_ticket_name,
            :limits => limits.map { |qli| qli.to_hash }
        },
        :securityGroups => @security_groups
      }
    end

  end
end
