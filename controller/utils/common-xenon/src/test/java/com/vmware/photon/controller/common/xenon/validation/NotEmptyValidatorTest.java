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

import com.google.common.collect.ImmutableList;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;

/**
 * This class implements tests for {@link NotEmptyValidator}.
 */
public class NotEmptyValidatorTest {

  private NotEmptyValidator validator = NotEmptyValidator.INSTANCE;

  @Test
  public void passesOnValidDocument() {
    validator.validate(new AnnotatedDocument("test", ImmutableList.of("test")));
  }

  @Test(expectedExceptions = IllegalStateException.class)
  public void failsOnNullField() {
    validator.validate(new AnnotatedDocument(null, ImmutableList.of("test")));
  }

  @Test(expectedExceptions = IllegalStateException.class)
  public void failsOnEmtpyStringField() {
    validator.validate(new AnnotatedDocument("", ImmutableList.of("test")));
  }

  @Test(expectedExceptions = IllegalStateException.class)
  public void failsOnEmtpyListField() {
    validator.validate(new AnnotatedDocument("test", new ArrayList<String>()));
  }

  @Test(expectedExceptions = IllegalStateException.class)
  public void failsOnEmtpySetField() {
    validator.validate(new AnnotatedDocument("test", new HashSet<String>()));
  }

  @Test
  public void passesOnValidUnannotatedDocument() {
    validator.validate(new NotAnnotatedDocument("test", ImmutableList.of("test")));
  }

  @Test
  public void passesOnInvalidUnannotatedDocument() {
    validator.validate(new NotAnnotatedDocument(null, new ArrayList<String>()));
  }

  /**
   * Test ServiceDocument.
   */
  public static class AnnotatedDocument extends ServiceDocument {
    @NotEmpty
    public String value;

    @NotEmpty
    public Collection<String> collections;

    AnnotatedDocument(String value, Collection<String> collections) {
      this.value = value;
      this.collections = collections;
    }
  }

  /**
   * Test ServiceDocument.
   */
  public static class NotAnnotatedDocument extends ServiceDocument {
    public String value;
    public Collection<String> collections;

    NotAnnotatedDocument(String value, Collection<String> collections) {
      this.value = value;
      this.collections = collections;
    }
  }
}
