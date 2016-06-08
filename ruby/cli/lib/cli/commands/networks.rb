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
    class Networks < Base

      usage "network create [<options>]"
      desc "Create a new network"
      def create(args = [])
        name, description, portgroups = nil, nil, nil

        opts_parser = OptionParser.new do |opts|
          opts.on("-n", "--name Name", "Name") { |v| name = v }
          opts.on("-d", "--description Description", "Description") { |v| description = v }
          opts.on("-p", "--portgroups PortGroups", "PortGroups") { |v| portgroups = v }
        end

        parse_options(args, opts_parser)

        if interactive?
          name ||= ask("Network Name: ")
          description ||= ask("Description: ")
          portgroups ||= ask("PortGroups: ")
        end

        usage_error("Please provide --name flag", opts_parser) unless name
        usage_error("Please provide --portgroups flag", opts_parser) unless portgroups

        portgroups = portgroups.split /\s*,\s*/
        initialize_client
        spec = EsxCloud::NetworkCreateSpec.new(name, description, portgroups)
        network = EsxCloud::Network.create(spec)
        puts green("Network '#{network.id}' created")
      end

      usage "network show <id>"
      desc "Show network info"
      def show(args = [])
        id = shift_keyword_arg(args)
        usage_error("Please provide network id") if id.blank?

        network = client.find_network_by_id(id)

        puts "Network '#{id}':"
        puts "  Name:        #{network.name}"
        puts "  State:       #{network.state}"
        puts "  Description: #{network.description}" if network.description
        puts "  Port Groups: #{network.portgroups.join(", ")}"
        puts "  Default: #{network.is_default}"
      end

      usage "network list [<options>]"
      desc "List networks"
      def list(args = [])
        name = nil

        opts_parser = OptionParser.new do |opts|
          opts.on("-n", "--name Name", "Network name") { |n| name = n }
        end

        parse_options(args, opts_parser)

        if name
          networks = client.find_networks_by_name(name)
        else
          networks = client.find_all_networks
        end

        render_networks(networks.items)
      end

      usage "network delete [<id>]"
      desc "Delete Network"
      def delete_network(args = [])
        id = shift_keyword_arg(args)
        usage_error("Please provide network id") if id.blank?

        if confirmed?
          client.delete_network(id)
          puts green("Deleted Network '#{id}'")
        else
          puts yellow("OK, canceled")
        end
      end

      usage "network set_portgroups <id> [<options>]"
      desc "Set Network portgroups"
      def set_portgroups(args = [])
        portgroups = nil

        id = shift_keyword_arg(args)
        usage_error("Please provide Network id") if id.blank?

        opts_parser = OptionParser.new do |opts|
          opts.on("-p", "--portgroups PORTGROUPS", "portgroups") { |p| portgroups = p }
        end

        parse_options(args, opts_parser)

        usage_error("Please provide --portgroups flag", opts_parser) unless portgroups

        portgroups = portgroups.split /\s*,\s*/

        client.set_portgroups(id, portgroups)
        puts green("Updated Network '#{id}' port groups")
      end

      usage "network set_default <id>"
      desc "Set Default Network"
      def set_default(args = [])
        id = shift_keyword_arg(args)
        usage_error("Please provide Network id") if id.blank?

        client.set_default(id)
        puts green ("Set Default Network '#{id}'")
      end
    end
  end
end
