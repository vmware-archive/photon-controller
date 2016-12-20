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

/**
 * This class implements simple control flags for Xenon task services.
 */
public class ControlFlags {

  public static final int CONTROL_FLAG_OPERATION_PROCESSING_DISABLED = 1;
  public static final int CONTROL_FLAG_DISABLE_OPERATION_PROCESSING_ON_STAGE_TRANSITION = 1 << 1;

  public static final int CONTROL_FLAG_DISABLE_OPERATION_PROCESSING_ON_HANDLE_CREATE = 1 << 2;
  public static final int CONTROL_FLAG_DISABLE_OPERATION_PROCESSING_ON_HANDLE_START = 1 << 3;
  public static final int CONTROL_FLAG_DISABLE_OPERATION_PROCESSING_ON_HANDLE_PATCH = 1 << 4;


  public static boolean isOperationProcessingDisabled(int value) {
    return check(value, CONTROL_FLAG_OPERATION_PROCESSING_DISABLED);
  }

  public static boolean disableOperationProcessingOnStageTransition(int value) {
    return check(value, CONTROL_FLAG_DISABLE_OPERATION_PROCESSING_ON_STAGE_TRANSITION);
  }

  public static boolean isHandleCreateDisabled(int value) {
    return check(value, CONTROL_FLAG_DISABLE_OPERATION_PROCESSING_ON_HANDLE_CREATE);
  }

  public static boolean isHandleStartDisabled(int value) {
    return check(value, CONTROL_FLAG_DISABLE_OPERATION_PROCESSING_ON_HANDLE_START);
  }

  public static boolean isHandlePatchDisabled(int value) {
    return check(value, CONTROL_FLAG_DISABLE_OPERATION_PROCESSING_ON_HANDLE_PATCH);
  }

  private static boolean check(int value, int flag) {
    return (0 != (value & flag));
  }

  /**
   * Builder class to build control flags.
   */
  public static class Builder {
    private int value;

    public Builder() {
      value = 0;
    }

    public Builder disableOperationProcessing() {
      return set(CONTROL_FLAG_OPERATION_PROCESSING_DISABLED);
    }

    public Builder disableOperationProcessingOnStageTransition() {
      return set(CONTROL_FLAG_DISABLE_OPERATION_PROCESSING_ON_STAGE_TRANSITION);
    }

    public Builder disableOperationProcessingOnHandleCreate() {
      return set(CONTROL_FLAG_DISABLE_OPERATION_PROCESSING_ON_HANDLE_CREATE);
    }

    public Builder disableOperationProcessingOnHandleStart() {
      return set(CONTROL_FLAG_DISABLE_OPERATION_PROCESSING_ON_HANDLE_START);
    }

    public Builder disableOperationProcessingOnHandlePatch() {
      return set(CONTROL_FLAG_DISABLE_OPERATION_PROCESSING_ON_HANDLE_PATCH);
    }

    public int build() {
      return this.value;
    }

    private Builder set(int value) {
      this.value |= value;
      return this;
    }
  }
}
