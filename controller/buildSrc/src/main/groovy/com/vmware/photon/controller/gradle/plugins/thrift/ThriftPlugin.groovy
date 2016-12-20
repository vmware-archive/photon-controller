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

import org.gradle.api.Action
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.tasks.SourceSet

class ThriftPlugin implements Plugin<Project> {

  @Override
  void apply(Project project) {
    project.apply plugin: 'java'

    project.sourceSets.all { SourceSet sourceSet ->
      def generateThriftTask = project.tasks.create(
          sourceSet.getTaskName('generate', 'thrift'), ThriftCompile)
      generateThriftTask.setClasspath(sourceSet.getCompileClasspath())
      generateThriftTask.setDestinationDir(
          project.file("$project.buildDir/generated-sources/$sourceSet.name"))

      def thriftSrc = []
      def configName = getConfigName(sourceSet)
      project.configurations.create(configName) {
        visible = false
        transitive = false
        extendsFrom = []
      }

      def resolveThriftTask = project.tasks.create(sourceSet.getTaskName('resolve', 'thrift')) {
        actions = [
            {
              project.configurations[configName].files.each { file ->
                if (file.path.endsWith('.thrift')) {
                  thriftSrc.add(file)
                }
              }
              generateThriftTask.setSource(thriftSrc)
            } as Action
        ]
      }

      generateThriftTask.dependsOn(resolveThriftTask)

      sourceSet.java.srcDir "${project.buildDir}/generated-sources/${sourceSet.name}"
      Task compileJavaTask = project.tasks.getByName(sourceSet.getCompileTaskName('java'))
      compileJavaTask.dependsOn(generateThriftTask)
    }
  }

  private static getConfigName(SourceSet sourceSet) {
    if (sourceSet.name.equals(SourceSet.MAIN_SOURCE_SET_NAME)) {
      return 'thrift'
    }
    return "${sourceSet.name}Thrift"
  }
}
