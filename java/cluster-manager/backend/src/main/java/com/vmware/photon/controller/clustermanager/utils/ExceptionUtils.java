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

package com.vmware.photon.controller.clustermanager.utils;

import org.eclipse.jetty.util.MultiException;

import java.util.Collection;

/**
 * This class implements helper routines for exceptions.
 */
public class ExceptionUtils {

  /**
   * This method returns a multi-exception which contains the errors in the supplied list of
   * exceptions.
   *
   * @param throwables Supplies a list of exceptions.
   * @return Returns a multi-exception which contains the errors in the supplied list of
   *         exceptions.
   */
  public static MultiException createMultiException(final Collection<Throwable> throwables) {
    MultiException multiException = new MultiException();
    for (Throwable t : throwables) {
      multiException.add(t);
    }

    return multiException;
  }
}
