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

package com.vmware.photon.controller.apife.auth;

import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.isEmptyOrNullString;

/**
 * Tests for {@link TransactionAuthorizationObject}.
 */
public class TransactionAuthorizationObjectTest {

  private static final TransactionAuthorizationObject.Kind DEFAULT_KIND =
      TransactionAuthorizationObject.Kind.DEPLOYMENT;
  private static final TransactionAuthorizationObject.Strategy DEFAULT_STRATEGY =
      TransactionAuthorizationObject.Strategy.SELF;
  private static final String DEFAULT_ID = "id";

  private TransactionAuthorizationObject authObject;

  @BeforeMethod
  private void setUp() {
    authObject = new TransactionAuthorizationObject(DEFAULT_KIND, DEFAULT_STRATEGY, DEFAULT_ID);
  }

  @Test
  public void testInitialization() {
    assertThat(authObject.getKind(), is(DEFAULT_KIND));
    assertThat(authObject.getStrategy(), is(DEFAULT_STRATEGY));
    assertThat(authObject.getId(), is(DEFAULT_ID));
  }

  @Test
  public void testConstructorOverload() {
    authObject = new TransactionAuthorizationObject(DEFAULT_KIND);
    assertThat(authObject.getKind(), is(DEFAULT_KIND));
    assertThat(authObject.getStrategy(), is(TransactionAuthorizationObject.Strategy.SELF));
    assertThat(authObject.getId(), isEmptyOrNullString());
  }

  @Test
  public void testKindProperty() {
    assertThat(authObject.getKind(), is(DEFAULT_KIND));

    authObject.setKind(TransactionAuthorizationObject.Kind.PROJECT);
    assertThat(authObject.getKind(), is(TransactionAuthorizationObject.Kind.PROJECT));
  }

  @Test
  public void testStrategyProperty() {
    assertThat(authObject.getStrategy(), is(DEFAULT_STRATEGY));

    authObject.setStrategy(TransactionAuthorizationObject.Strategy.PARENT);
    assertThat(authObject.getStrategy(), is(TransactionAuthorizationObject.Strategy.PARENT));
  }

  @Test
  public void testIdProperty() {
    assertThat(authObject.getId(), is(DEFAULT_ID));

    authObject.setId("new-id");
    assertThat(authObject.getId(), is("new-id"));
  }
}
