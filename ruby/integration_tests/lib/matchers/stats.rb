# Copyright 2016 VMware, Inc. All Rights Reserved.
#
# Licensed under the Apache License, Version 2.0 (the "License"); you may not
# use this file except in compliance with the License. You may obtain a copy of
# the License at http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software distributed
# under the License is distributed on an "AS IS" BASIS, without warranties or
# conditions of any kind, EITHER EXPRESS OR IMPLIED. See the License for the
# specific language governing permissions and limitations under the License.

require 'rspec/expectations'

RSpec::Matchers.define :have_graphite_data do
  match do |stats|
    hasValue = 
      stats != nil &&
      stats.first != nil &&
      stats.first["datapoints"] != nil
    # Filter non-null data
    false if !hasValue
    data = stats.first["datapoints"].select { |x| x.first != nil }
    expect(data).to have_at_least(1).things
    data.length > 0
  end
end
