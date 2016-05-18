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
package com.vmware.photon.controller.common.xenon.migration;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * This annotation indicates that a given subclass of ServiceDocument should be migrated and upgraded
 * when Photon Controller is upgraded. See also {@link NoMigrationDuringUpgrade}, which indicates that
 * a document should not be migrated or upgraded.
 *
 * This annotation should be put on all ServiceDocuments that represent data entities that should be migrated
 * between old and new control plane. Since we currently do not support running task services this annotation
 * should not be put on tasks.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface MigrateDuringUpgrade {
  /**
   * Should point to service that performs transformation, such as renames, on the entity being migrated.
   *
   * Usually should point to the ReflectionTransformationService or a custom transformation service if
   * ServiceDocument type specific transformations are required.
   */
  String transformationServicePath();

  /**
   * The factory path used by the source system.
   */
  String sourceFactoryServicePath();

  /**
   * The factory path used by the destination system.
   */
  String destinationFactoryServicePath();

  /**
   * The name of the Xenon service that contains the ServiceDocuments being migrated.
   */
  String serviceName();
}
