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

package com.vmware.transfer.nfc;

import java.io.IOException;

/**
 * Base class for NFC exceptions.
 */
public class NfcException extends IOException {

  private static final long serialVersionUID = 1L;

  public NfcException(final String message) {
    super(message);
  }

  public NfcException(Throwable cause) {
    super(cause);
  }

  public NfcException(String message, Throwable cause) {
    super(message, cause);
  }

}
