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
    class System < Base

      usage "system status"
      desc "Show system status"
      def status(_args = [])
        initialize_client
        system_status = EsxCloud::Status.get
        display_system_status_in_table(system_status)
      end


      usage "system deploy <path>"
      desc "Create a system deployment by parsing deployment config file"
      def deploy(args = [])
        path = shift_keyword_arg(args)

        path ||= ask("Deployment config file path: ") if interactive?

        usage_error("Please provide deployment config file path") if path.blank?

        path = File.expand_path(path, Dir.pwd)
        puts "Using deployment config file #{path}"
        initialize_client

        config = YAML.load_file(path)

        deployment_create_spec = EsxCloud::DeploymentImporter.import_config(config)
        deployment = EsxCloud::Deployment.create(deployment_create_spec)
        puts green("deployment '#{deployment.id}' created")

        host_create_specs = EsxCloud::HostsImporter.import_config(config)
        host_create_specs.each do |spec|
          begin
            host = EsxCloud::Host.create(deployment.id, spec)
            puts green("Host '#{host.address}' '#{host.id}' created")
          rescue EsxCloud::ApiError, EsxCloud::CliError => e
            puts red("Error creating host '#{spec.address}': #{e.message}")
            abort
          end
        end

        deployment = client.deploy_deployment(deployment.id)
        puts green("deployment '#{deployment.id}' deployed")

      end

      usage "system destroy"
      desc "Un-deploys a previously deployed system"
      def destroy(_args = [])
        initialize_client

        # destroy the deployment
        deployments = EsxCloud::Deployment.find_all.items
        puts "Cleaning Deployment(s):" unless deployments.size == 0
        deployments.each do |d|
          puts "  destroying deployment #{d.id}"
          deployment = client.destroy_deployment(d.id)
          puts green("deployment '#{d.id}' deployed")
        end

        # delete all the hosts
        deployments.each do |d|
          hosts = client.get_deployment_hosts(d.id).items
          puts "Cleaning Host(s):" unless hosts.size == 0
          hosts.each do |h|
            puts "  removing host #{h.id}"
            EsxCloud::Host.delete h.id
          end
        end

        # delete the deployment
        deployments = EsxCloud::Deployment.find_all.items
        puts "Cleaning Deployment(s):" unless deployments.size == 0
        deployments.each do |d|
          puts "  removing deployment #{d.id}"
          EsxCloud::Deployment.delete d.id
        end

        if deployments.size == 0
          puts "No previous installation found"
        else
          puts
          puts green "Done."
        end
      end

      usage "system migration prepare"
      desc "Starts the recurring copy state of old system into new"
      def deployment_migration_prepare(args = [])
        sourceDeploymentAddress = shift_keyword_arg(args)

        sourceDeploymentAddress ||= ask("Address of the source system (eg. http://sourceIp:9000): ") if interactive?

        usage_error("Please provide address of the source system") if sourceDeploymentAddress.blank?
        puts "Using deployment confile file #{sourceDeploymentAddress}"
        initialize_client

        deployments = EsxCloud::Deployment.find_all.items
        puts "Preparing migration" unless deployments.size == 0

        deployments.each do |d|
          puts "  destination deployment #{d.id}"
          EsxCloud::Deployment.initialize_deployment_migration(sourceDeploymentAddress, d.id)
        end

        if deployments.size == 0
          puts "No installation found on destination"
        else
          puts
          puts green "Preperation for migration started."
        end
      end

      usage "system migration finalize"
      desc "Finishes the copy state of old system into new and makes new system the active one"
      def deployment_migration_finalize(args = [])
        sourceDeploymentAddress = shift_keyword_arg(args)

        sourceDeploymentAddress ||= ask("Address of the source system (eg. http://sourceIp:9000): ") if interactive?

        usage_error("Please provide address of the source system") if sourceDeploymentAddress.blank?
        puts "Using deployment confile file #{sourceDeploymentAddress}"
        initialize_client

        deployments = EsxCloud::Deployment.find_all.items
        puts "Finalizing migration" unless deployments.size == 0

        deployments.each do |d|
          puts "  destination deployment #{d.id}"
          EsxCloud::Deployment.finalize_deployment_migration(sourceDeploymentAddress, d.id)
        end

        if deployments.size == 0
          puts "No installation found on destination"
        else
          puts
          puts green "Finalize migration started."
        end
      end

      private

      def display_system_status_in_table(system_status)
        table = Terminal::Table.new
        table.headings = %w(Component Status)
        table.add_row(["overall", system_status.status])

        system_status.components.each do |component|
          table.add_row([component.name, component.status])
        end

        puts
        puts table
        puts
      end
    end
  end
end
