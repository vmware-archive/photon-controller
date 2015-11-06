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
package com.vmware.photon.controller.gradle.plugins.thrift

import org.gradle.api.InvalidUserDataException
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.compile.AbstractCompile

public class ThriftCompile extends AbstractCompile {
  @TaskAction
  protected void compile() {
    getDestinationDir().mkdir()
    getSource().each { File file ->
      def output = new StringBuffer()
      def result = "thrift --gen java:hashcode,beans --out ${getDestinationDir()} ${file}".execute()
      result.consumeProcessOutput(output, output)
      result.waitFor()
      if (result.exitValue() != 0) {
        throw new InvalidUserDataException(output.toString())
      }
    }
  }
}
