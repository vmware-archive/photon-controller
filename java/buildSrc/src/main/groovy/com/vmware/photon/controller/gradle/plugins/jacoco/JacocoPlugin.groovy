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
package com.vmware.photon.controller.gradle.plugins.jacoco

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.testing.Test

/**
 * Jacoco Plugin, ported from https://github.com/gschmidl/jacoco-gradle.
 */
class JacocoPlugin implements Plugin<Project> {

  @Override
  void apply(Project project) {
    project.apply plugin: 'java'

    def convention = new JacocoPluginConvention(project)
    project.convention.plugins.jacoco = convention;

    project.configurations.create('jacoco')

    project.dependencies.add('jacoco', "org.jacoco:org.jacoco.agent:${convention.version}")
    project.dependencies.add('jacoco', "org.jacoco:org.jacoco.ant:${convention.version}")

    Test testTask = (Test) project.tasks.getByName('test')

    testTask.doFirst {
      ant.taskdef(name: 'jacocoagent',
          classname: 'org.jacoco.ant.AgentTask',
          classpath: project.configurations.jacoco.asPath)
      ant.jacocoagent(convention.getParams())
      jvmArgs "${ant.properties.agentvmparam}"
    }

    testTask.doLast {
      if (!new File(convention.coverageFileName).exists()) {
        logger.info("Skipping Jacoco report for ${project.name}. " +
            "The data file is missing. (Maybe no tests ran in this module?)")
        logger.info("The data file was expected at ${convention.coverageFileName}")
        return
      }
      ant.taskdef(name: 'jacocoreport',
          classname: 'org.jacoco.ant.ReportTask',
          classpath: project.configurations.jacoco.asPath)
      ant.mkdir dir: "${convention.reportPath}"

      ant.jacocoreport {
        executiondata {
          ant.file file: "${convention.coverageFileName}"
        }
        structure(name: project.name) {
          classfiles {
            fileset dir: "${project.sourceSets.main.output.classesDir}"
          }
          sourcefiles {
            project.sourceSets.java.srcDirs.each {
              fileset(dir: it.absolutePath)
            }
          }
        }
        xml destfile: "${convention.reportPath}/jacoco.xml"
        html destdir: "${convention.reportPath}"
      }
    }
  }
}
