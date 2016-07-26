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

package com.vmware.photon.controller.api.model.constraints;

import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import javax.validation.ConstraintValidatorContext;

/**
 * Test {@link com.vmware.photon.controller.api.model.constraints.CidrValidator}.
 */
public class CidrValidatorTest {
  @Mock
  private ConstraintValidatorContext context;
  @Mock
  private ConstraintValidatorContext.ConstraintViolationBuilder builder;

  private CidrValidator validator;

  @BeforeMethod
  public void setup() {
    MockitoAnnotations.initMocks(this);
    Mockito.doReturn(builder).when(context).buildConstraintViolationWithTemplate(Mockito.anyString());

    validator = new CidrValidator();
  }

  @Test
  public void testNull() {
    Assert.assertFalse(validator.isValid(null, context));
  }

  @Test
  public void testValidCidr() {
    Assert.assertTrue(validator.isValid("192.168.0.1/1", context));
    Assert.assertTrue(validator.isValid("192.168.0.1/24", context));
    Assert.assertTrue(validator.isValid("192.168.0.1/31", context));
  }

  @Test
  public void testInvalidCidr() {
    Assert.assertFalse(validator.isValid("192.168.0.1", context));
    Assert.assertFalse(validator.isValid("192.168.0.1/", context));
    Assert.assertFalse(validator.isValid("/24", context));
    Assert.assertFalse(validator.isValid("192.168.0.500/24", context));
    Assert.assertFalse(validator.isValid("192.168.0.1/0", context));
    Assert.assertFalse(validator.isValid("192.168.0.1/a", context));
    Assert.assertFalse(validator.isValid("192.168.0.a/24", context));
    Assert.assertFalse(validator.isValid("192.168.0.1/32", context));
  }
}
