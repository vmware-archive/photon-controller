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
  class Flavor

    attr_accessor :id, :name, :kind, :cost, :state

    # @param [String] id
    # @param [String] name
    # @param [String] kind
    # @param [Array<QuotaLineItem>] cost
    def initialize(id, name, kind, cost, state)
      @id = id
      @name = name
      @kind = kind
      @cost = cost
      @state = state
    end

    def ==(other)
      @id == other.id && @name == other.name &&
          @kind == other.kind && @cost == other.cost && @state == other.state
    end

    # @param[FlavorCreateSpec] spec
    # @return [Flavor]
    def self.create(spec)
      Config.client.create_flavor(spec.to_hash)
    end

    # @param[String] file
    # @return[Array]
    def self.upload_flavor(file, force)
      create_flavors_from_file(file, force)
    end

    # @param [String] json
    # @return [Flavor]
    def self.create_from_json(json)
      create_from_hash(JSON.parse(json))
    end

    # @param [Hash] hash
    # @return [Flavor]
    def self.create_from_hash(hash)
      unless hash.is_a?(Hash) && hash.keys.to_set.superset?(%w(id name kind cost).to_set)
        raise UnexpectedFormat, "Invalid flavor hash: #{hash}"
      end

      cost = hash["cost"].map { |qli| QuotaLineItem.new(qli["key"], qli["value"], qli["unit"]) }
      new(hash["id"], hash["name"], hash["kind"], cost, hash["state"])
    end

    # @param [String] id
    # @return [Flavor]
    def self.find_by_id(id)
      Config.client.find_flavor_by_id(id)
    end

    # @param [String] name
    # @return [FlavorList]
    def self.find_by_name_kind(name, kind)
      Config.client.find_flavors_by_name_kind(name, kind)
    end

    # @param [String] name
    # @return [FlavorList]
    def self.find_all
      Config.client.find_all_flavors
    end

    # @param [String] id
    # @return [Task]
    def self.delete(id)
      Config.client.delete_flavor(id)
    end

    # @return [Task]
    def delete
      self.class.delete(@id)
    end

    private
    # @return [Array]
    def self.create_flavors_from_file(file, force)
      if !File.exists?(file)
        fail EsxCloud::NotFound, "Flavor file #{file} does not exist"
      end

      hash = YAML.load_file(file)
      flavor_not_created = []
      hash["flavors"].each do |flavor|
        costs = flavor["cost"].map do |cost|
          QuotaLineItem.new(cost["key"], cost["value"], cost["unit"])
        end

        flavors = Config.client.find_flavors_by_name_kind(flavor["name"], flavor["kind"]).items

        if flavors.empty?
          create(FlavorCreateSpec.new(flavor["name"], flavor["kind"], costs))
          next
        end

        unless flavors.size == 1
          fail UnexpectedFormat, "flavor name and kind shouldn't be duplicated."
        end

        unless force
          flavor_not_created << flavor
          next
        end

        begin
          Config.client.delete_flavor(flavors.first.id)
          create(FlavorCreateSpec.new(flavor["name"], flavor["kind"], costs))
        rescue ApiError => e
          flavor_not_created << flavor
        end

      end
      flavor_not_created
    end

  end
end
