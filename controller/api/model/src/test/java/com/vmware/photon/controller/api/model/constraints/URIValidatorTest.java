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
import org.mockito.MockitoAnnotations;
import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;

import javax.validation.ConstraintValidatorContext;

/**
 * Tests {@link URIValidator}.
 */
public class URIValidatorTest {
  @Mock
  private ConstraintValidatorContext context;
  @Mock
  private ConstraintValidatorContext.ConstraintViolationBuilder builder;

  @BeforeMethod
  public void setUp() {
    MockitoAnnotations.initMocks(this);

    doNothing().when(context).disableDefaultConstraintViolation();
    doReturn(builder).when(context).buildConstraintViolationWithTemplate(anyString());
  }

  @Test
  public void testValidURI() {
    URIValidator validator = new URIValidator();
    Assert.assertTrue(validator.isValid("/vmfs/1234", context));
  }

  @Test
  public void testInvalidURI() {
    URIValidator validator = new URIValidator();
    Assert.assertFalse(validator.isValid("// unknown//1234", context));
  }
}
