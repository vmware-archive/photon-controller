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
 * This class implements tests for {@link NotBlankValidator}.
 */
public class NotBlankValidatorTest {

  private NotBlankValidator validator = NotBlankValidator.INSTANCE;

  @Test
  public void passesOnValidDocument() {
    validator.validate(new AnnotatedDocument("test"));
  }

  @Test(expectedExceptions = IllegalStateException.class)
  public void failsOnNullField() {
    validator.validate(new AnnotatedDocument(null));
  }

  @Test(expectedExceptions = IllegalStateException.class)
  public void failsOnBlankField() {
    validator.validate(new AnnotatedDocument("  "));
  }

  @Test
  public void passesOnValidUnannotatedDocument() {
    validator.validate(new NotAnnotatedDocument("test"));
  }

  @Test
  public void passesOnInvalidUnannotatedDocument() {
    validator.validate(new NotAnnotatedDocument(null));
  }

  /**
   * Test ServiceDocument.
   */
  public static class AnnotatedDocument extends ServiceDocument {
    @NotBlank
    public String value;

    AnnotatedDocument(String value) {
      this.value = value;
    }
  }

  /**
   * Test ServiceDocument.
   */
  public static class NotAnnotatedDocument extends ServiceDocument {
    public String value;

    NotAnnotatedDocument(String value) {
      this.value = value;
    }
  }
}
