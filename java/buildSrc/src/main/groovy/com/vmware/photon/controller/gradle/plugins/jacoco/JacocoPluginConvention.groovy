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

import org.gradle.api.Project

class JacocoPluginConvention {
  def reportPath
  def coverageFileName
  def tmpDir
  def includes
  def excludes
  def exclclassloader
  def append
  def sessionid
  def dumponexit
  def output
  def address
  def port
  def version

  def jacoco(Closure close) {
    close.delegate = this
    close.run()
  }

  JacocoPluginConvention(Project project) {
    reportPath = "${project.reporting.baseDir.absolutePath}/jacoco"
    tmpDir = "${project.buildDir}/tmp/jacoco"
    coverageFileName = "${tmpDir}/jacoco.exec"
    includes = []
    excludes = []
    exclclassloader = []
    sessionid = null
    append = false
    dumponexit = true
    output = 'file'
    address = null
    port = null
    version = '0.6.2.201302030002'
  }

  def getParams() {
    def params = [:]
    params['property'] = 'agentvmparam'
    params['destfile'] = coverageFileName
    if (includes != null && includes.size() > 0) params['includes'] = includes.join(':')
    if (excludes != null && excludes.size() > 0) params['excludes'] = excludes.join(':')
    if (exclclassloader != null && exclclassloader.size > 0) params['exclclassloader'] = exclclassloader
    if (sessionid != null) params['sessionid'] = sessionid
    params['append'] = append
    params['dumponexit'] = dumponexit
    params['output'] = output
    if (address != null) params['address'] = address
    if (port != null) params['port'] = port
    return params
  }
}
