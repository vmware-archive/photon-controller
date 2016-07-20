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
 * Test {@link IPv4Validator}.
 */
public class IPv4ValidatorTest {
  @Mock
  private ConstraintValidatorContext context;
  @Mock
  private ConstraintValidatorContext.ConstraintViolationBuilder builder;

  private IPv4Validator validator;

  @BeforeMethod
  public void setup() {
    MockitoAnnotations.initMocks(this);
    Mockito.doReturn(builder).when(context).buildConstraintViolationWithTemplate(Mockito.anyString());

    validator = new IPv4Validator();
  }

  @Test
  public void testNull() {
    Assert.assertFalse(validator.isValid(null, context));
  }

  @Test
  public void testValidIpAddress() {
    Assert.assertTrue(validator.isValid("192.168.1.1", context));
  }

  @Test
  public void testInvalidIpAddress() {
    Assert.assertFalse(validator.isValid("192.168.1", context));
    Assert.assertFalse(validator.isValid("192.168.1.300", context));
  }
}
