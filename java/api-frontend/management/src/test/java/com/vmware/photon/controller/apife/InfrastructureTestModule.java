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

package com.vmware.photon.controller.apife;

import com.google.inject.AbstractModule;
import com.google.inject.Injector;
import com.google.inject.Provides;
import com.google.inject.Singleton;

import javax.validation.ConstraintValidator;
import javax.validation.ConstraintValidatorFactory;
import javax.validation.Validation;
import javax.validation.Validator;
import javax.validation.ValidatorFactory;

/**
 * Guice module for testing infrastructure objects.
 */
public class InfrastructureTestModule extends AbstractModule {
  @Override
  protected void configure() {
  }

  @Provides
  @Singleton
  public ValidatorFactory getValidatorFactory(final Injector injector) {
    return Validation
        .byDefaultProvider()
        .configure()
        .constraintValidatorFactory(new ConstraintValidatorFactory() {
          @Override
          public <T extends ConstraintValidator<?, ?>> T getInstance(Class<T> key) {
            return injector.getInstance(key);
          }

          @Override
          public void releaseInstance(ConstraintValidator<?, ?> constraintValidator) {
            // do nothing
          }
        }).buildValidatorFactory();
  }

  @Provides
  public Validator getValidator(ValidatorFactory validatorFactory) {
    return validatorFactory.getValidator();
  }
}
