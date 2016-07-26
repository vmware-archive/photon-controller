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

package com.vmware.photon.controller.apife.exceptions.external;

import com.vmware.photon.controller.apife.exceptions.ApiFeException;
import com.vmware.photon.controller.common.logging.LoggingUtils;

import java.util.HashMap;
import java.util.Map;

/**
 * Base ESXCloud exception.
 */
public class ExternalException extends ApiFeException {

  // todo(markl): discuss capturing current operation and current request id to aid with debug
  // todo(markl): https://www.pivotaltracker.com/story/show/52317629
  private ErrorCode errorCode;
  private Map<String, String> data;

  public ExternalException(ErrorCode errorCode, String message, Map<String, String> data) {
    this(errorCode, message, data, null);
  }

  public ExternalException(ErrorCode errorCode, String message, Map<String, String> data, Throwable e) {
    super(message, e);
    this.errorCode = errorCode;
    if (data == null) {
      this.data = new HashMap<>();
    } else {
      this.data = data;
    }
  }

  public ExternalException(ErrorCode errorCode) {
    this.errorCode = errorCode;
    this.data = new HashMap<>();
  }

  public ExternalException() {
    this(ErrorCode.INTERNAL_ERROR);
  }

  public ExternalException(String message) {
    this(ErrorCode.INTERNAL_ERROR, message, null);
  }

  public ExternalException(Throwable e) {
    this(ErrorCode.INTERNAL_ERROR, null, null, e);
  }

  /**
   * Turns any exception into an external exception that is safe to show to the client.
   */
  public static ExternalException launder(Throwable t) {
    if (t instanceof ExternalException) {
      return (ExternalException) t;
    }

    String errorMessage = "Please contact the system administrator about request #" + LoggingUtils.getRequestId();
    return new ExternalException(ErrorCode.INTERNAL_ERROR, errorMessage, new HashMap<String, String>());
  }

  public String getErrorCode() {
    return errorCode.getCode();
  }

  public int getHttpStatus() {
    return errorCode.getHttpStatus();
  }

  public synchronized void addData(String key, String value) {
    data.put(key, value);
  }

  public Map<String, String> getData() {
    return data;
  }
}
