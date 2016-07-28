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

package com.vmware.photon.controller.api.frontend.jackson;

import com.fasterxml.jackson.databind.BeanDescription;
import com.fasterxml.jackson.databind.DeserializationConfig;
import com.fasterxml.jackson.databind.deser.ValueInstantiator;
import com.fasterxml.jackson.databind.introspect.AnnotatedConstructor;
import com.fasterxml.jackson.databind.module.SimpleValueInstantiators;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Singleton;

import java.util.List;

/**
 * Value Instantiators that uses {@link GuiceValueInstantiator} for any bean that
 * has @Inject annotation.
 */
@Singleton
public class GuiceValueInstantiators extends SimpleValueInstantiators {
  private final Injector injector;

  @Inject
  public GuiceValueInstantiators(Injector injector) {
    this.injector = injector;
  }

  @Override
  public ValueInstantiator findValueInstantiator(DeserializationConfig config, BeanDescription beanDesc,
                                                 ValueInstantiator defaultInstantiator) {
    AnnotatedConstructor defaultConstructor = beanDesc.findDefaultConstructor();
    if (defaultConstructor != null) {
      return defaultInstantiator;
    }

    List<AnnotatedConstructor> constructors = beanDesc.getConstructors();
    if (!constructors.isEmpty()) {
      for (AnnotatedConstructor constructor : constructors) {
        if (constructor.hasAnnotation(javax.inject.Inject.class) ||
            constructor.hasAnnotation(com.google.inject.Inject.class)) {
          return new GuiceValueInstantiator(beanDesc.getBeanClass(), injector.getProvider(beanDesc.getBeanClass()));
        }
      }
    }

    return defaultInstantiator;
  }
}
