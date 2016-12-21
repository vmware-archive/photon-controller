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
package com.vmware.photon.controller.deployer.xenon.entity;

import com.vmware.photon.controller.common.xenon.deployment.NoMigrationDuringDeployment;
import com.vmware.photon.controller.common.xenon.migration.NoMigrationDuringUpgrade;
import com.vmware.photon.controller.common.xenon.validation.RenamedField;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.StatefulService;

import java.util.Set;

/**
 * Test service.
 */
public class SampleService extends StatefulService {

  public SampleService() {
    super(State.class);
  }

  @Override
  public void handleStart(Operation startOperation) {
    startOperation.complete();
  }

  /**
   * This class defines the document state associated with a single {@link SampleService} instance.
   *
   * We do not need to migrate this class since it is solely used in tests. However the unit test
   * checking that all ServiceDocuments are annotated is picking up this class as well, thus to
   * avoid test errors we need to annotate this class.
   */
  @NoMigrationDuringUpgrade
  @NoMigrationDuringDeployment
  public static class State extends ServiceDocument {

    @RenamedField(originalName = "hostAddress")
    public String field1;

    @RenamedField(originalName = "usageTags")
    public Set<String> fieldTags;
  }
}
