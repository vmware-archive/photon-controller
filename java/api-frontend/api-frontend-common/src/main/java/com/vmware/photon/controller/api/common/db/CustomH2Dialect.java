/*
 * Copyright 2015 VMware, Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License.  You may obtain a copy of
 * the License at http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed
 * under the License is distributed on an "AS IS" BASIS, without warranties or
 * conditions of any kind, EITHER EXPRESS OR IMPLIED.  See the License for the
 * specific language governing permissions and limitations under the License.
 */

package com.vmware.photon.controller.api.common.db;


import org.hibernate.dialect.H2Dialect;

/**
 * Workaround for the issue that foreign references are being dropped even for an empty database.
 *
 * @see https://hibernate.atlassian.net/browse/HHH-7002
 */
public class CustomH2Dialect extends H2Dialect {
  @Override
  public String getDropSequenceString(String sequenceName) {
    // Only drops a sequence if it exists.
    return "drop sequence if exists " + sequenceName;
  }

  @Override
  public boolean dropConstraints() {
    // Do not need to drop constraints before dropping tables.
    return false;
  }
}
