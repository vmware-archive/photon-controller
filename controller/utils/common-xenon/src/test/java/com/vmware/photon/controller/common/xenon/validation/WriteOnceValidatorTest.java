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
 * Implements tests for {@link WriteOnceValidator}.
 */
public class WriteOnceValidatorTest {
  WriteOnceValidator validator = WriteOnceValidator.INSTANCE;

  @Test
  public void valueNotSetInPatch() {
    AnnotatedDocument initialState = new AnnotatedDocument(null);
    AnnotatedDocument patchState = new AnnotatedDocument(null);
    validator.validate(initialState, patchState);
  }

  @Test
  public void valueSetInPatch() {
    AnnotatedDocument initialState = new AnnotatedDocument(null);
    AnnotatedDocument patchState = new AnnotatedDocument(1);
    validator.validate(initialState, patchState);
  }

  @Test(expectedExceptions = IllegalStateException.class)
  public void valueUnchangedInPatch() {
    AnnotatedDocument initialState = new AnnotatedDocument(1);
    AnnotatedDocument patchState = new AnnotatedDocument(1);
    validator.validate(initialState, patchState);
  }

  @Test(expectedExceptions = IllegalStateException.class)
  public void valueChangedInPatch() {
    AnnotatedDocument initialState = new AnnotatedDocument(1);
    AnnotatedDocument patchState = new AnnotatedDocument(2);
    validator.validate(initialState, patchState);
  }

  @Test
  public void passesOnValidUnannotatedDocument() {
    NotAnnotatedDocument initialState = new NotAnnotatedDocument(1);
    NotAnnotatedDocument patchState = new NotAnnotatedDocument(2);
    validator.validate(initialState, patchState);
  }

  /**
   * Test ServiceDocument with annotation.
   */
  public static class AnnotatedDocument extends ServiceDocument {
    @WriteOnce
    public Integer value;

    AnnotatedDocument(Integer value) {
      this.value = value;
    }
  }

  /**
   * Test ServiceDocument without annotation.
   */
  public static class NotAnnotatedDocument extends ServiceDocument {
    public Integer value;

    NotAnnotatedDocument(int value) {
      this.value = value;
    }
  }
}
