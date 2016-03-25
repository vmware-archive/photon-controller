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
package com.vmware.photon.controller.common.xenon.upgrade;

import com.vmware.photon.controller.common.xenon.ServiceUriPaths;
import com.vmware.photon.controller.common.xenon.validation.RenamedFieldHandler;
import com.vmware.xenon.common.ServiceDocument;

import com.google.common.reflect.ClassPath;
import com.google.common.reflect.ClassPath.ClassInfo;

import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

/**
 * This class implements common upgrade utils and map.
 */
public class UpgradeUtils {
  public static final String REFLECTION_TRANSFORMATION_SERVICE_LINK = ServiceUriPaths.UPGRADE_ROOT + "/reflection";

  public static final String PHOTON_CONTROLLER_PACKAGE = "com.vmware.photon.controller";

  public static List<Field> handleRenamedField(Object source, ServiceDocument destination) {
      return RenamedFieldHandler.initialize(source, destination);
  }

  private static List<UpgradeInformation> cachedList = null;

  /**
   * This method searches the class path to identify each class definition that extends {@link ServiceDocument}
   * that is part of the Photon Controller code base.
   * It selects all {@link ServiceDocument} with the {@link MigrateDuringUpgrade} annotation and record the necessesary
   * upgrade information.
   *
   * @return list of {@link UpgradeInfromation} objects describing each service document that needs to be migrated
   * during upgrade.
   */
  @SuppressWarnings("unchecked")
  public static List<UpgradeInformation> findAllUpgradeServices() {
    if (cachedList != null) {
      return cachedList;
    }
    List<UpgradeInformation> infoEntries = new ArrayList<>();
    ClassLoader cl = ClassLoader.getSystemClassLoader();
    ClassPath classPath;
    try {
      classPath = ClassPath.from(cl);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }

    for (ClassInfo classFile : classPath.getAllClasses()) {
      if (classFile.getName().contains(PHOTON_CONTROLLER_PACKAGE)) {
        Class<?> type = classFile.load();
        if (type.getSuperclass() != null && type.getSuperclass() == ServiceDocument.class) {
          for (Annotation a : type.getAnnotations()) {
            if (a.annotationType() == MigrateDuringUpgrade.class) {
              MigrateDuringUpgrade u = (MigrateDuringUpgrade) a;

              UpgradeInformation info = new UpgradeInformation(
                  u.sourceFactoryServicePath(),
                  u.destinationFactoryServicePath(),
                  u.serviceName(),
                  u.transformationServicePath(),
                  (Class<? extends ServiceDocument>) type);

              infoEntries.add(info);
            }
          }
        }
      }
    }
    cachedList = infoEntries;
    return infoEntries;
  }
}
