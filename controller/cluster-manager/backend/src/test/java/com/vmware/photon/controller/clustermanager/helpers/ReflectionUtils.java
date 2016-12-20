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

package com.vmware.photon.controller.clustermanager.helpers;

import com.vmware.photon.controller.common.xenon.InitializationUtils;
import com.vmware.photon.controller.common.xenon.validation.DefaultBoolean;
import com.vmware.photon.controller.common.xenon.validation.DefaultInteger;
import com.vmware.photon.controller.common.xenon.validation.DefaultTaskState;
import com.vmware.photon.controller.common.xenon.validation.DefaultUuid;
import com.vmware.photon.controller.common.xenon.validation.NotBlank;
import com.vmware.photon.controller.common.xenon.validation.NotNull;
import com.vmware.xenon.common.ServiceDocument;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * This class provides utilities to handle reflections.
 */
public class ReflectionUtils {
  public static List<String> getAttributeNamesWithAnnotation(Class<?> annotatedClassType, Class<?> annotationType) {
    List<String> attributes = new ArrayList<>();
    for (Field field : annotatedClassType.getDeclaredFields()) {
      for (Annotation annotation : field.getDeclaredAnnotations()) {
        if (annotation.annotationType() == annotationType) {
          attributes.add(field.getName());
        }
      }
    }
    return attributes;
  }

  public static Object getDefaultAttributeValue(Field declaredField) throws Throwable {
    if (declaredField.getType() == Boolean.class) {
      return Boolean.FALSE;
    } else if (declaredField.getType() == Integer.class) {
      return new Integer(0);
    } else if (declaredField.getType() == List.class) {
      return new ArrayList<>();
    } else if (declaredField.getType() == Map.class) {
      return new HashMap<>();
    } else if (declaredField.getType() == Set.class) {
      return new HashSet<>();
    } else if (declaredField.getType() == String.class) {
      return declaredField.getName();
    } else if (declaredField.getType().isEnum()) {
      return declaredField.getType().getEnumConstants()[0];
    } else {
      return declaredField.getType().newInstance();
    }
  }

  public static <T extends ServiceDocument> T buildValidStartState(Class<T> documentClass) throws Throwable {
    T serviceDocument = documentClass.newInstance();
    InitializationUtils.initialize(serviceDocument);

    for (Field field : documentClass.getDeclaredFields()) {
      Set<Class<? extends Annotation>> annotationTypes = new HashSet<>();
      for (Annotation annotation : field.getDeclaredAnnotations()) {
        annotationTypes.add(annotation.annotationType());
      }

      if ((annotationTypes.contains(NotNull.class) || annotationTypes.contains(NotBlank.class)) &&
          !annotationTypes.contains(DefaultBoolean.class) &&
          !annotationTypes.contains(DefaultInteger.class) &&
          !annotationTypes.contains(DefaultTaskState.class) &&
          !annotationTypes.contains(DefaultUuid.class)) {
        field.set(serviceDocument, getDefaultAttributeValue(field));
      }
    }

    return serviceDocument;
  }
}
