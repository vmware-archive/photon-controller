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

package com.vmware.photon.controller.apife.jackson;

import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.deser.ValueInstantiator;

import javax.inject.Provider;

/**
 * Jackson Value Instantiator that uses Guice providers for instantiation
 * instead of default constructors.
 */
public class GuiceValueInstantiator extends ValueInstantiator {
  private final Provider provider;
  private final String valueTypeDesc;

  public GuiceValueInstantiator(Class clazz, Provider provider) {
    this.provider = provider;
    this.valueTypeDesc = clazz.getName();
  }

  @Override
  public String getValueTypeDesc() {
    return valueTypeDesc;
  }

  @Override
  public boolean canCreateUsingDefault() {
    return true;
  }

  @Override
  public Object createUsingDefault(DeserializationContext ctxt) {
    return provider.get();
  }
}
