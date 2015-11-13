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

package com.vmware.photon.controller.common.extensions;

import com.vmware.photon.controller.common.extensions.exceptions.AmbiguousExtensionException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URL;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Class Loader, used to dynamically load
 * extensions implementation classes. These
 * classes are then bounded to interfaces.
 */
public class ExtensionLoader {
  private static final Logger logger = LoggerFactory.getLogger(ExtensionLoader.class);

  public static Class<?> load(String classPath)
      throws ClassNotFoundException, AmbiguousExtensionException {
    ClassLoader classLoader = ExtensionLoader.class.getClassLoader();
    if (classLoader == null) {
      classLoader = ClassLoader.getSystemClassLoader();
    }
    checkBinding(classLoader, classPath);
    return loadImpl(classPath);
  }

  private static void checkBinding(ClassLoader classLoader, String classPath)
      throws AmbiguousExtensionException, ClassNotFoundException {

    Enumeration<URL> paths;
    Set<URL> classPathSet = new LinkedHashSet<>();
    String inputPath = classPath.replace('.', '/') + ".class";

    try {
      paths = classLoader.getResources(inputPath);
    } catch (IOException ioe) {
      logger.error("IOException caught during extensions loading");
      throw new ClassNotFoundException("No implementation found for extensions: " + classPath);
    }

    while (paths.hasMoreElements()) {
      URL path = paths.nextElement();
      classPathSet.add(path);
    }

    if (classPathSet.size() == 0) {
      throw new ClassNotFoundException("No implementation found for extensions: " + classPath);
    }

    if (classPathSet.size() > 1) {
      logger.error("Class path contains multiple extensions bindings");
      for (URL classPathUrl : classPathSet) {
        logger.error("Found binding in [" + classPathUrl + "]");
      }
      throw new AmbiguousExtensionException("Found multiple implementation for extensions: " + classPath);
    }
  }

  private static Class<?> loadImpl(String classPath)
      throws AmbiguousExtensionException, ClassNotFoundException {

    Class<?> clazz;
    try {
      clazz = Class.forName(classPath);
    } catch (ClassNotFoundException ex) {
      throw ex;
    }
    return clazz;
  }
}
