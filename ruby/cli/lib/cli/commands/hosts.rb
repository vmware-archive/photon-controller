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
    class APIHosts < Base

      usage "host create [<options>]"
      desc "Create a new host"

      def create(args = [])
        username, password, availability_zone, usage_tags, address, metadata = nil, nil, nil, nil, nil, nil

        opts_parser = OptionParser.new do |opts|
          opts.on("-u", "--username Username", "User name") { |value| username = value }
          opts.on("-t", "--usage_tags Tags", "Usage Tag [MGMT|CLOUD]") { |value| usage_tags = value.split /\s*,\s*/ }
          opts.on("-p", "--password Password", "Password") { |value| password = value }
          opts.on("-z", "--availability_zone Zone", "Availability Zone") { |value| availability_zone = value }
          opts.on("-m", "--metadata Metadata", "Meta data") { |value| metadata = JSON.parse(value) }
          opts.on("-i", "--address Address", "Address") { |value| address = value }
        end

        parse_options(args, opts_parser)

        if interactive?
          username ||= ask("Username: ")
          password ||= ask("Password: ")
          usage_tags ||= ask_for_usage_tags
          address ||= ask("Host Ip: ")
          metadata ||= ask_for_metadata
        end

        initialize_client

        # find the deployment
        deployments = client.find_all_api_deployments()
        usage_error "Unexpected error retrieving deployment resource" if deployments.items.size != 1

        spec = EsxCloud::HostCreateSpec.new(username, password, usage_tags, address, metadata, availability_zone)
        host = EsxCloud::Host.create(deployments.items.first.id, spec)
        puts green("Host '#{host.id}' created")
      end

      usage "host show <id> [options]"
      desc "Show host info"
      def select(args = [])
        id = shift_keyword_arg(args)
        if id.nil?
          usage_error("Please provide host id")
        end

        host = client.mgmt_find_host_by_id(id)
        puts ("HOST #{id}:")
        puts ("  IP:       #{host.address}")
        puts ("  Tags:     #{host.usage_tags.join(", ")}")
        puts ("  State:    #{host.state}")
        puts ("  Metadata: #{host.metadata}")
      end

      usage "host delete <id>"
      desc "Delete a host"
      def delete_host(args = [])
        id = shift_keyword_arg(args)
        usage_error("Please provide host id") if id.nil?

        client.mgmt_delete_host(id)
        puts green("host (ID=#{id}) is deleted")
      end

      usage "host list_vms <id>"
      desc "List all the vms on host"
      def list_vms(args = [])
        id = shift_keyword_arg(args)
        usage_error("Please provide host id") if id.nil?

        render_vms(client.mgmt_get_host_vms(id).items)
      end

      usage "host list_tasks <id>"
      desc "List all tasks for host"
      def list_tasks(args = [])
        id = shift_keyword_arg(args)
        usage_error("Please provide host id") if id.nil?

        render_task_list(client.find_tasks_by_host_id(id).items)
      end

      # mode related coomands

      usage "host enter_maintenance <id>"
      desc "Enter maintenance mode"
      def enter_maintenance(args = [])
        id = shift_keyword_arg(args)
        usage_error("Please provide host id") if id.nil?

        client.host_enter_maintenance_mode(id)
        puts green("Host (ID=#{id}) is in maintenance mode.")
      end

      usage "host exit_maintenance <id>"
      desc "Exit maintenance mode"
      def exit_maintenance(args = [])
        id = shift_keyword_arg(args)
        usage_error("Please provide host id") if id.nil?

        client.host_exit_maintenance_mode(id)
        puts green("Host (ID=#{id}) is in normal mode.")
      end

      usage "host enter_suspended <id>"
      desc "Enter suspended mode"
      def enter_suspended(args = [])
        id = shift_keyword_arg(args)
        usage_error("Please provide host id") if id.nil?

        client.host_enter_suspended_mode(id)
        puts green("Host (ID=#{id}) is in suspended mode.")
      end

      usage "host resume <id>"
      desc "Resume host"
      def resume(args = [])
        id = shift_keyword_arg(args)
        usage_error("Please provide host id") if id.nil?

        client.host_resume(id)
        puts green("Host (ID=#{id}) is in normal mode.")
      end

      private

      def ask_for_usage_tags
        tags = ask("Usage Tags (',' separated string. Options [CLOUD,MGMT]):")
        tags.split /\s*,\s*/
      end

      def ask_for_metadata
        metadata = ask("Metadata (optional {key:value} dict): ")
        metadata.blank? ? {} : JSON.parse(metadata)
      end
    end
  end
end
