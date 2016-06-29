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
    class Deployments < Base

      usage "deployment create [<options>]"
      desc "Create a new deployment"

      def create(args = [])
        options = parse_deployment_creation_arguments(args)

        if interactive?
          options = read_deployment_arguments_inactively(options)
        end

        validate_deployment_arguments(options)

        if confirmed?
          initialize_client
          oauth_security_groups =
              options[:oauth_security_groups].split(/\s*,\s*/) unless options[:oauth_security_groups].nil?
          image_datastores = options[:image_datastores].split(/\s*,\s*/) unless  options[:image_datastores].nil?
          spec = EsxCloud::DeploymentCreateSpec.new(
              image_datastores,
              EsxCloud::AuthConfigurationSpec.new(
                  options[:auth_enabled],
                  options[:oauth_tenant],
                  options[:oauth_password],
                  oauth_security_groups
              ),
              EsxCloud::NetworkConfigurationSpec.new(false),
              EsxCloud::StatsInfo.new(
                  options[:stats_enabled],
                  options[:stats_store_endpoint],
                  options[:stats_store_port]
              ),
              options[:syslog_endpoint],
              options[:ntp_endpoint],
              options[:use_image_datastore_for_vms],
              options[:loadbalancer_enabled])
          deployment = EsxCloud::Deployment.create(spec)
          puts green("deployment '#{deployment.id}' created")
        else
          puts yellow("OK, canceled")
        end

      end

      usage "deployment deploy <id>"
      desc "Perform a deployment"
      def deploy(args = [])
        id = shift_keyword_arg(args)
        if id.blank?
          usage_error("Please provide Deployment id")
        end
        deployment = client.deploy_deployment(id)
        puts green("deployment '#{deployment.id}' deployed")
      end

      usage "deployment update <id> [options]"
      desc "Update a deployment"
      def update(args = [])
        id = shift_keyword_arg(args)
        if id.blank?
          usage_error("Please provide Deployment id")
        end

        security_groups = nil
        opts_parser = OptionParser.new do |opts|
          opts.on('-g', '--security_groups Security Groups', 'List of security groups separated by comma (e.g, g1,
g2)') do |g|
            security_groups = g
          end
        end

        parse_options(args, opts_parser)

        if security_groups.nil?
          puts red("No security groups were provided. Halted!")
          exit
        end

        security_groups_in_hash = {items: security_groups.split(/\s*,\s*/)}

        initialize_client
        client.update_security_groups(id, security_groups_in_hash)

        puts green("Security groups of deployment '#{id}' has been changed to #{security_groups}")
      end

      usage "deployment delete <id>"
      desc "Delete Deployment by id"

      def delete(args = [])
        id = shift_keyword_arg(args)
        if id.blank?
          usage_error("Please provide Deployment id")
        end

        client.delete_api_deployment(id)
        puts green("Deleted Deployment '#{id}'")
      end

      usage "deployment destroy <id>"
      desc "Destroy Deployment by id"

      def destroy(args = [])
        id = shift_keyword_arg(args)
        if id.blank?
          usage_error("Please provide Deployment id")
        end

        client.destroy_deployment(id)
        puts green("Destroy Deployment '#{id}'")
      end

      usage "deployment list"
      desc "Lists all the deployments"

      def list(args = [])
        initialize_client

        deployments = EsxCloud::Deployment.find_all.items
        render_deployments(deployments)
      end

      usage "deployment show <id>"
      desc "Show deployment info"

      def show(args=[])
        id = shift_keyword_arg(args)
        usage_error("Please provide deployment id") if id.blank?

        deployment = client.find_deployment_by_id(id)
        vms = client.get_deployment_vms(id).items
        data = vms.map do |vm|
          connections = client.get_vm_networks(vm.id).network_connections.select { |n| !n.network.blank? }
          [vm, connections.first.ip_address]
        end

        puts
        puts "Deployment #{id}:"
        puts
        puts "  State:                        #{deployment.state}"
        puts
        puts "  Image Datastores:             #{deployment.image_datastores}"
        puts "  Use image datastore for vms:  #{deployment.use_image_datastore_for_vms}"
        puts
        puts "  Auth Enabled:                 #{deployment.auth.enabled}"
        puts "  Auth Endpoint:                #{deployment.auth.endpoint || "-"}"
        puts "  Auth Port:                    #{deployment.auth.port || "-"}"
        puts "  Auth Tenant:                  #{deployment.auth.tenant || "-"}"
        puts "  Auth Security Groups:         #{deployment.auth.securityGroups || "-"}"
        puts
        puts "  Syslog Endpoint:              #{deployment.syslog_endpoint || "-"}"
        puts "  Ntp Endpoint:                 #{deployment.ntp_endpoint || "-"}"
        puts
        puts "  Stats Enabled:                #{deployment.stats.enabled}"
        puts "  Stats Store Endpoint:         #{deployment.stats.storeEndpoint || "-"}"
        puts "  Stats Store Port:             #{deployment.stats.storePort || "-"}"
        puts
        puts "  Loadbalancer Enabled:         #{deployment.loadbalancer_enabled}"
        puts
        render_deployment_summary(data)
        if deployment.migration.data_migration_cycle_size != 0
          puts
          puts "  MigrationState:"
          puts "    Completed data migration cycles:         #{deployment.migration.completed_cycles}"
          puts "    Current data migration cycles progress:  #{deployment.migration.data_migration_cycle_progress} / #{deployment.migration.data_migration_cycle_size}"
          puts "    Vib upload progress:                     #{deployment.migration.vibs_uploaded} / #{deployment.migration.vibs_uploaded + deployment.migration.vibs_uploading}"
        end
        if deployment.cluster_configurations == nil || deployment.cluster_configurations.length == 0
          puts
          puts "  No cluster is supported"
        else
          puts
          puts "  Supported Cluster Types:"
          deployment.cluster_configurations.map { |cc| puts "    #{cc.type}" }
        end
      end

      usage "deployment list_vms <id>"
      desc "Lists all the vms associated with the deployment"

      def list_vms(args = [])
        id = shift_keyword_arg(args)
        usage_error("Please provide deployment id") if id.blank?
        initialize_client

        render_vms(client.get_deployment_vms(id).items)
      end

      usage "deployment list_hosts <id>"
      desc "Lists all the hosts associated with the deployment"

      def list_hosts(args = [])
        id = shift_keyword_arg(args)
        usage_error("Please provide deployment id") if id.blank?
        initialize_client

        render_hosts(client.get_deployment_hosts(id).items)
      end

      usage "deployment pause_system <id>"
      desc "Pause system under the deployment"
      def pause_system(args = [])
        id = shift_keyword_arg(args)
        usage_error("Please provide deployment id") if id.blank?

        client.pause_system(id)
        puts green("System is paused")
      end

      usage "deployment enable_cluster_type <id> [<options>]"
      desc "Enables a cluster of certain type associated with the deployment"
      def enable_cluster_type(args = [])
        type, image_id = nil, nil

        opts_parser = OptionParser.new do |opts|
          opts.on("-k", "--type TYPE",
                  "Cluster type. Accepted values are KUBERNETES, MESOS, or SWARM") { |v| type = v }
          opts.on("-i", "--image-id IMAGE_ID", "ID of the cluster image.") { |v| image_id = v }
        end
        id = shift_keyword_arg(args)
        parse_options(args, opts_parser)

        usage_error("Please provide deployment id") if id.blank?
        usage_error("Please provide cluster type using --type flag") if type.blank?
        usage_error("Please provide image ID using --image-id flag") if image_id.blank?
        usage_error("Unsupported cluster type #{type}") unless %w(KUBERNETES MESOS SWARM).include? type

        if confirmed?
          initialize_client
          spec = EsxCloud::ClusterConfigurationSpec.new(
              type,
              image_id)

          config = EsxCloud::Deployment.enable_cluster_type(id, spec)

          puts green("'#{type}' cluster is configured for deployment '#{id}'")
        else
          puts yellow("OK, canceled")
        end
      end

      usage "deployment disable_cluster_type <id> [<options>]"
      desc "Disables a certain cluster type associated with the deployment"
      def disable_cluster_type(args = [])
        type = nil

        opts_parser = OptionParser.new do |opts|
          opts.on("-k", "--type TYPE",
                  "Cluster type. Accepted values are KUBERNETES, MESOS, or SWARM") { |v| type = v }
        end
        id = shift_keyword_arg(args)
        parse_options(args, opts_parser)

        usage_error("Please provide deployment id") if id.blank?
        usage_error("Please provide cluster type using --type flag") if type.blank?
        usage_error("Unsupported cluster type #{type}") unless %w(KUBERNETES MESOS SWARM).include? type

        if confirmed?
          initialize_client
          spec = EsxCloud::ClusterConfigurationSpec.new(
              type,
              nil)

          EsxCloud::Deployment.disable_cluster_type(id, spec)

          puts green("'#{type}' cluster configuration is deleted for deployment '#{id}'")
        else
          puts yellow("OK, canceled")
        end

      end

      private

      def parse_deployment_creation_arguments(args)
        options = {
            :image_datastores => nil,
            :auth_enabled => false,
            :oauth_tenant => nil,
            :oauth_password => nil,
            :oauth_security_groups => nil,
            :syslog_endpoint => nil,
            :ntp_endpoint => nil,
            :stats_enabled => false,
            :stats_store_endpoint => nil,
            :stats_store_port => nil,
            :use_image_datastore_for_vms => false,
            :loadbalancer_enabled => true,
        }

        opts_parser = OptionParser.new do |opts|
          opts.banner = "Usage: deployment create [options]"
          opts.on('-i', '--image_datastores DATASTORE_NAMES', 'Comma-separated list of Image Datastores') do |i|
            options[:image_datastores] = i
          end
          opts.on('-v', '--use_image_datastore_for_vms', 'Use Image Datastore For VMs') do |_|
            options[:use_image_datastore_for_vms] = true
          end
          opts.on('-a', '--enable_auth', 'Enable authentication/authorization for deployment') do |_|
            options[:auth_enabled] = true
          end
          opts.on('-t', '--oauth_tenant TENANT_NAME', 'OAuth Tenant/Domain') do |t|
            options[:oauth_tenant]= t
          end
          opts.on('-p', '--oauth_password PASSWORD', 'OAuth Tenant/Domain Admin Password') do |p|
            options[:oauth_password] = p
          end
          opts.on('-g', '--oauth_security_groups SECURITY_GROUPS', 'Comma-separated list of security groups') do |g|
            options[:oauth_security_groups] = g
          end
          opts.on('-s', '--syslog_endpoint ENDPOINT', 'Syslog Endpoint/IP') do |s|
            options[:syslog_endpoint] = s
          end
          opts.on('-n', '--ntp_endpoint ENDPOINT', 'Ntp Endpoint/IP') do |n|
            options[:ntp_endpoint] = n
          end
          opts.on('-d', '--enable_stats', 'Enable Stats for deployment') do |_|
            options[:stats_enabled] = true
          end
          opts.on('-e', '--stats_store_endpoint ENDPOINT', 'Stats Store Endpoint') do |e|
            options[:stats_store_endpoint] = e
          end
          opts.on('-f', '--stats_store_port PORT', 'Stats Store Port') do |f|
            options[:stats_store_port] = f
          end
          opts.on('-l', '--disable_loadbalancer', 'Disable loadbalancer for deployment') do |o|
            options[:loadbalancer_enabled] = false
          end
        end

        parse_options(args, opts_parser)

        options
      end

      def read_deployment_arguments_inactively(options)
        options[:image_datastores] ||= ask("Image Datastore Names: ")

        if options[:auth_enabled]
          options[:oauth_tenant] ||= ask("OAuth Tenant: ")
          options[:oauth_password] ||= ask("OAuth Password: ")
          options[:oauth_security_groups] ||= ask("OAuth Security Groups: ")
        end

        if options[:syslog_endpoint].nil?
          syslog_input = ask("Syslog Endpoint: ")
          options[:syslog_endpoint] = syslog_input.blank? ? nil : syslog_input
        end

        if options[:ntp_endpoint].nil?
          ntp_input = ask("Ntp Endpoint: ")
          options[:ntp_endpoint] = ntp_input.blank? ? nil : ntp_input
        end

        if options[:stats_enabled]
          options[:stats_store_endpoint] ||= ask("Stats Store Endpoint: ")
          options[:stats_store_port] ||= ask("Stats Store Port: ")
        end

        options
      end

      def validate_deployment_arguments(options)
        if options[:image_datastores].blank?
          usage_error("Image datastore names cannot be nil.")
        end

        if options[:auth_enabled]
          if options[:oauth_tenant].blank?
            usage_error("OAuth tenant cannot be nil when auth is enabled.")
          end
          if options[:oauth_password].blank?
            usage_error("OAuth password cannot be nil when auth is enabled.")
          end
          if options[:oauth_security_groups].blank?
            usage_error("OAuth security groups cannot be nil when auth is enabled.")
          end
        end

        if options[:stats_enabled]
          if options[:stats_store_endpoint].blank?
            usage_error("Stats store endpoint cannot be nil when stats is enabled.")
          end
        end

      end
    end
  end
end
