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
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.core.Is.is;

/**
 * This class implements tests for {@link DefaultStringInitializer}.
 */
public class DefaultStringInitializerTest {

  DefaultStringInitializer initializer = DefaultStringInitializer.INSTANCE;

  @Test
  public void successfullySettingString() {
    AnnotatedDocument doc = new AnnotatedDocument();

    initializer.initialize(doc);

    assertThat(doc.value, is("foo"));
  }

  @Test
  public void successNotSetString() {
    NotAnnotatedDocument doc = new NotAnnotatedDocument();

    initializer.initialize(doc);

    assertThat(doc.value, is(nullValue()));
  }

  @Test(expectedExceptions = RuntimeException.class)
  public void failsOnInitializeingFieldOfWrongType() {
    WronglyAnnotatedDocument doc = new WronglyAnnotatedDocument();

    initializer.initialize(doc);
  }

  /**
   * Test ServiceDocument.
   */
  public static class AnnotatedDocument extends ServiceDocument {
    @DefaultString(value = "foo")
    public String value;
  }

  /**
   * Test ServiceDocument.
   */
  public static class NotAnnotatedDocument extends ServiceDocument {
    public String value;
  }

  /**
   * Test ServiceDocument.
   */
  public static class WronglyAnnotatedDocument extends ServiceDocument {
    @DefaultString(value = "bar")
    public Boolean value;
  }
}
