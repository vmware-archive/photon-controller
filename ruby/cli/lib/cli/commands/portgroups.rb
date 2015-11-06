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

module EsxCloud::Cli
  module Commands
    class PortGroups < Base

      usage "portgroup show <id>"
      desc "Show portgroup info"
      def show(args = [])
        id = shift_keyword_arg(args)
        usage_error("Please provide portgroup id") if id.blank?

        portgroup = client.find_portgroup_by_id(id)

        puts "PortGroup '#{id}':"
        puts "  Name:        #{portgroup.name}"
        puts "  Tags:        #{portgroup.usage_tags.join(", ")}"
      end

      usage "portgroup list [<options>]"
      desc "List portgroups"
      def list(args = [])
        name, usage_tag = nil, nil

        opts_parser = OptionParser.new do |opts|
          opts.on("-n", "--name Name", "PortGroup name") { |n| name = n }
          opts.on("-t", "--usage_tag Tag", "Usage Tag [MGMT|CLOUD]") { |t| usage_tag = t }
        end

        parse_options(args, opts_parser)

        portgroups = client.find_portgroups(name, usage_tag)
        render_portgroups(portgroups.items)
      end

    end
  end
end
