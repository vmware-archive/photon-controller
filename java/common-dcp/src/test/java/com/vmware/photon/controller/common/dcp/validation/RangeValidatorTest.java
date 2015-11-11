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
 * This class implements tests for {@link RangeValidator}.
 */
public class RangeValidatorTest {
  RangeValidator validator = RangeValidator.INSTANCE;

  @Test(expectedExceptions = IllegalStateException.class)
  public void failsOnInvalidDocumentNull() {
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

  @Test
  public void passesOnValidNullAnnotatedDocument() {
    validator.validate(new AnnotatedAcceptNullDocument(1));
  }

  @Test
  public void passesOnNullNullAnnotatedDocument() {
    validator.validate(new AnnotatedAcceptNullDocument(null));
  }

  @Test(expectedExceptions = IllegalStateException.class)
  public void failsOnInvalidNullAnnotatedDocument() {
    validator.validate(new AnnotatedAcceptNullDocument(0));
  }

  @Test
  public void passesOnValidNullFalseAnnotatedDocument() {
    validator.validate(new AnnotatedAcceptNullFalseDocument(1));
  }

  @Test(expectedExceptions = IllegalStateException.class)
  public void failsOnNullFalseNullAnnotatedDocument() {
    validator.validate(new AnnotatedAcceptNullFalseDocument(null));
  }

  @Test(expectedExceptions = IllegalStateException.class)
  public void failsOnInvalidNullFalseAnnotatedDocument() {
    validator.validate(new AnnotatedAcceptNullFalseDocument(24));
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
  public static class AnnotatedAcceptNullDocument extends ServiceDocument {
    @Range(min = 1, max = 10, acceptNull = true)
    public Integer value;

    AnnotatedAcceptNullDocument(Integer value) {
      this.value = value;
    }
  }

  /**
   * Test ServiceDocument.
   */
  public static class AnnotatedAcceptNullFalseDocument extends ServiceDocument {
    @Range(min = 1, max = 10, acceptNull = false)
    public Integer value;

    AnnotatedAcceptNullFalseDocument(Integer value) {
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
