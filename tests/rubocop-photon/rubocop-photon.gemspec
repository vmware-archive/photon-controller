# coding: utf-8

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

lib = File.expand_path("../lib", __FILE__)
puts lib
$LOAD_PATH.unshift(lib) unless $LOAD_PATH.include?(lib)

require "rubocop/photon/version"

Gem::Specification.new do |spec|
  spec.name          = "rubocop-photon"
  spec.version       = RuboCop::Photon::VERSION
  spec.authors       = ["VMWare Inc"]
  spec.summary       = "Rubocop Photon"
  spec.description   = "Rubocop Photon"

  spec.files         += `git ls-files lib`.split($/)

  spec.require_paths = ["lib"]

  spec.add_development_dependency "bundler"
  spec.add_development_dependency "rspec"

  spec.add_runtime_dependency "rubocop"
end
