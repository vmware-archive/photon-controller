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

package com.vmware.photon.controller.apife.entities;

import com.vmware.photon.controller.api.common.entities.base.BaseEntity;

import org.testng.annotations.Test;

import javax.validation.ConstraintViolationException;
import javax.validation.constraints.NotNull;


/**
 * Test {@link com.vmware.photon.controller.apife.entities.EntityStateValidator}.
 */
public class EntityStateValidatorTest {

  /**
   * Test class extending BaseEntity. Only used for testing.
   */
  public class TestEntity extends BaseEntity {
    @NotNull
    private Long notNullField;

    @Override
    public String getKind() {
      return null;
    }
  }

  @Test (expectedExceptions = ConstraintViolationException.class)
  public void testValidateState() {
    TestEntity testEntity = new TestEntity();
    EntityStateValidator.validateState(testEntity);
  }
}
