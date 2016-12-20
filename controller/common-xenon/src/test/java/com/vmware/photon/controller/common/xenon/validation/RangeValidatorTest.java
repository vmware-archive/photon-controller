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
package com.vmware.photon.controller.common.xenon.validation;

import com.vmware.xenon.common.ServiceDocument;

import org.testng.annotations.Test;

/**
 * This class implements tests for {@link RangeValidator}.
 */
public class RangeValidatorTest {
  RangeValidator validator = RangeValidator.INSTANCE;

  @Test
  public void passesOnValidDocumentNull() {
    validator.validate(new AnnotatedDocument(null));
  }

  @Test(expectedExceptions = IllegalStateException.class)
  public void failsOnInvalidDocumentMaxRangeExceeded() {
    validator.validate(new AnnotatedDocument(20));
  }

  @Test(expectedExceptions = IllegalStateException.class)
  public void failsOnInvalidDocumentLessThanMinRange() {
    validator.validate(new AnnotatedDocument(-2));
  }

  @Test
  public void passesOnValidDocumentOnMinRange() {
    validator.validate(new AnnotatedDocument(1));
  }

  @Test
  public void passesOnValidDocumentOnMaxRange() {
    validator.validate(new AnnotatedDocument(10));
  }

  @Test
  public void passesOnValidDocumentWithinRange() {
    validator.validate(new AnnotatedDocument(5));
  }

  @Test
  public void passesOnValidUnannotatedDocument() {
    validator.validate(new NotAnnotatedDocument(null));
  }

  @Test
  public void passesOnInvalidUnannotatedDocument() {
    validator.validate(new NotAnnotatedDocument(1));
  }

  /**
   * Test ServiceDocument.
   */
  public static class AnnotatedDocument extends ServiceDocument {
    @Range(min = 1, max = 10)
    public Integer value;

    AnnotatedDocument(Integer value) {
      this.value = value;
    }
  }

  /**
   * Test ServiceDocument.
   */
  public static class NotAnnotatedDocument extends ServiceDocument {
    public Integer value;

    NotAnnotatedDocument(Integer value) {
      this.value = value;
    }
  }
}
