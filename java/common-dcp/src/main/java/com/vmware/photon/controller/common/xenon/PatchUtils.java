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

package com.vmware.photon.controller.common.xenon;

import com.vmware.photon.controller.common.xenon.validation.DeepPatch;
import com.vmware.photon.controller.common.xenon.validation.Immutable;
import com.vmware.xenon.common.ServiceDocument;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

/**
 * This class implements utilities for DCP patching actions.
 */
public class PatchUtils {

  public static <T extends ServiceDocument> void patchState(T currentState, T patchState) {
    try {
      Field[] patchStateDeclaredFields = patchState.getClass().getDeclaredFields();
      for (Field patchStateField : patchStateDeclaredFields) {
        if (Modifier.isStatic(patchStateField.getModifiers())) {
          continue;
        }
        boolean immutableField = false;
        boolean deepPatch = false;
        Annotation[] declaredAnnotations = patchStateField.getDeclaredAnnotations();
        for (Annotation annotation : declaredAnnotations) {
          if (annotation.annotationType() == Immutable.class) {
            immutableField = true;
          } else if (annotation.annotationType() == DeepPatch.class) {
            deepPatch = true;
          }
        }

        if (immutableField) {
          continue;
        }

        if (null != patchStateField.get(patchState)) {
          Field currentStateField = currentState.getClass().getField(patchStateField.getName());

          if (deepPatch) {
            deepPatch(currentStateField.get(currentState), patchStateField.get(patchState));
          } else {
            currentStateField.set(currentState, patchStateField.get(patchState));
          }
        }
      }
    } catch (IllegalStateException e) {
      throw e;
    } catch (Throwable t) {
      throw new RuntimeException(t);
    }
  }

  /**
   * Deep patch operates on object level - sometimes a set-or-not-set operation does not satisfy our requirement
   * for patching a complex type.
   */
  private static <T> void deepPatch(T currentObject, T patchObject) {
    try {
      Field[] patchObjectDeclaredFields = patchObject.getClass().getDeclaredFields();
      for (Field patchObjectField : patchObjectDeclaredFields) {
        if (Modifier.isStatic(patchObjectField.getModifiers())) {
          continue;
        }

        if (null != patchObjectField.get(patchObject)) {
          Field currentObjeField = currentObject.getClass().getField(patchObjectField.getName());
          currentObjeField.set(currentObject, patchObjectField.get(patchObject));
        }
      }
    } catch (IllegalStateException e) {
      throw e;
    } catch (Throwable t) {
      throw new RuntimeException(t);
    }

  }
}
