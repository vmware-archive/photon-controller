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

package com.vmware.photon.controller.common.dcp.validation;

import com.vmware.dcp.common.ServiceDocument;

import org.testng.annotations.Test;

/**
 * This class implements tests for {@link PositiveValidator}.
 */
public class PositiveValidatorTest {
  PositiveValidator validator = PositiveValidator.INSTANCE;

  @Test
  public void passesOnValidDocument() {
    validator.validate(new AnnotatedDocument(1, 1L));
  }

  @Test
  public void passesOnValidDocumentNullIsValid() {
    validator.validate(new AnnotatedDocument(1, null));
  }

  @Test(expectedExceptions = IllegalStateException.class)
  public void failsOnInvalidDocumentNonNullableHasError() {
    validator.validate(new AnnotatedDocument(-1, 1L));
  }

  @Test(expectedExceptions = IllegalStateException.class)
  public void failsOnInvalidDocumentNullableHasError() {
    validator.validate(new AnnotatedDocument(1, -1L));
  }

  @Test
  public void passesOnValidUnannotatedDocument() {
    validator.validate(new NotAnnotatedDocument(1));
  }

  @Test
  public void passesOnInvalidUnannotatedDocument() {
    validator.validate(new NotAnnotatedDocument(-1));
  }

  /**
   * Test ServiceDocument.
   */
  public static class AnnotatedDocument extends ServiceDocument {
    @Positive
    public Integer value;

    @Positive(acceptNull = true)
    public Long value2;

    AnnotatedDocument(Integer value, Long value2) {
      this.value = value;
      this.value2 = value2;
    }
  }

  /**
   * Test ServiceDocument.
   */
  public static class NotAnnotatedDocument extends ServiceDocument {
    public Integer value;

    NotAnnotatedDocument(int value) {
      this.value = value;
    }
  }
}
