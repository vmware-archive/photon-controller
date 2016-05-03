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

package com.vmware.photon.controller.apibackend.utils;

import com.vmware.photon.controller.apibackend.annotations.ControlFlagsField;
import com.vmware.photon.controller.apibackend.annotations.TaskServiceEntityField;
import com.vmware.photon.controller.apibackend.annotations.TaskServiceStateField;
import com.vmware.photon.controller.apibackend.annotations.TaskStateField;
import com.vmware.photon.controller.apibackend.annotations.TaskStateSubStageField;
import com.vmware.photon.controller.cloudstore.dcp.entity.TaskService;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.TaskState;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;

/**
 * This class implements utility functions for service documents.
 */
public class ServiceDocumentUtils {

  /**
   * Returns the field that is annotated with ControlFlagsField annotation in given document.
   */
  public static <S extends ServiceDocument> Integer getControlFlags(S document) throws Throwable {
    return (Integer) getAnnotatedField(document, ControlFlagsField.class).get(document);
  }

  /**
   * Returns the field that is annotated with TaskStateField annotation in the given document.
   */
  public static <S extends ServiceDocument, T extends TaskState> T getTaskState(S document) throws Throwable {
    return (T) getAnnotatedField(document, TaskStateField.class).get(document);
  }

  /**
   * Sets the field that is annotated with TaskStateField annotation in the given document.
   */
  public static <S extends ServiceDocument, T extends TaskState> void setTaskState(S document, T taskState)
      throws Throwable {
    getAnnotatedField(document, TaskStateField.class).set(document, taskState);
  }

  /**
   * Gets the field that is annotated with SubStageField annotation in the given document.
   */
  public static <S extends ServiceDocument, T extends TaskState, E extends Enum> E getTaskStateSubStage(S document)
      throws Throwable {
    T taskState = getTaskState(document);
    return (E) getAnnotatedField(taskState, TaskStateSubStageField.class).get(taskState);
  }

  /**
   * Gets the field that is annotated with SubStageField annotation in the given task state.
   */
  public static <T extends TaskState, E extends Enum> E getTaskStateSubStage(T taskState) throws Throwable {
    return (E) getAnnotatedField(taskState, TaskStateSubStageField.class).get(taskState);
  }

  /**
   * Sets the field that is annotated with SubStageField annotation in the given task state.
   */
  public static <S extends ServiceDocument, T extends TaskState, E extends Enum> void setTaskStateSubStage(
      S document, E subStage) throws Throwable {
    T taskState = getTaskState(document);
    getAnnotatedField(taskState, TaskStateSubStageField.class).set(taskState, subStage);
  }

  /**
   * Sets the field that is annotated with SubStageField annotation in the given task state.
   */
  public static <T extends TaskState, E extends Enum> void setTaskStateSubStage(T taskState, E subStage)
      throws Throwable {
    getAnnotatedField(taskState, TaskStateSubStageField.class).set(taskState, subStage);
  }

  /**
   * Gets all enum entries of the given sub stage type.
   */
  public static <E extends Enum> E[] getTaskStateSubStageEntries(Class<E> subStageType) throws Throwable {
    return subStageType.getEnumConstants();
  }

  /**
   * Gets the field that is annotated with TaskServiceStateField annotation in the given document.
   */
  public static <S extends ServiceDocument> TaskService.State getTaskServiceState(S document) throws Throwable {
    return (TaskService.State) getAnnotatedField(document, TaskServiceStateField.class).get(document);
  }

  /**
   * Sets the field that is annotated with TaskServiceStateField annotation in the given document.
   */
  public static <S extends ServiceDocument> void setTaskServiceState(S document, TaskService.State taskServiceState)
    throws Throwable {
    getAnnotatedField(document, TaskServiceStateField.class).set(document, taskServiceState);
  }

  /**
   * Gets the field that is annotated with TaskServiceEntityField annotation in the given document.
   */
  public static <S extends ServiceDocument> ServiceDocument getTaskServiceEntity(S document) throws Throwable {
    return (ServiceDocument) getAnnotatedField(document, TaskServiceEntityField.class).get(document);
  }

  private static Field getAnnotatedField(Object target,
                                         Class annotationType) {
    Field[] declaredFields = target.getClass().getDeclaredFields();
    for (Field field : declaredFields) {
      Annotation[] declaredAnnotation = field.getDeclaredAnnotations();
      for (Annotation annotation : declaredAnnotation) {
        if (annotation.annotationType() == annotationType) {
          return field;
        }
      }
    }

    throw new RuntimeException("Cannot find a field annotated with " + annotationType.getName());
  }
}
