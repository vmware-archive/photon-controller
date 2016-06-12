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

package com.vmware.photon.controller.deployer.migration;

import com.vmware.photon.controller.cloudstore.xenon.CloudStoreServiceGroup;
import com.vmware.photon.controller.common.xenon.deployment.MigrateDuringDeployment;
import com.vmware.photon.controller.common.xenon.deployment.NoMigrationDuringDeployment;
import com.vmware.photon.controller.common.xenon.migration.MigrateDuringUpgrade;
import com.vmware.photon.controller.common.xenon.migration.NoMigrationDuringUpgrade;
import com.vmware.photon.controller.deployer.dcp.DeployerXenonServiceHost;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.UriUtils;

import com.google.common.collect.ImmutableSet;
import com.google.common.reflect.ClassPath;
import com.google.common.reflect.ClassPath.ClassInfo;
import org.testng.annotations.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/**
 *
 * This calls implements tests related to upgrade.
 *
 */
public class AnnotationUsageTest {

  private static final String PHOTON_CONTROLLER_PACKAGE = "com.vmware.photon.controller";
  private static Set<Class<?>> upgradeAnnotations = ImmutableSet.<Class<?>>builder()
      .add(MigrateDuringUpgrade.class)
      .add(NoMigrationDuringUpgrade.class)
      .build();

  private static Set<Class<?>> deploymentAnnotations = ImmutableSet.<Class<?>>builder()
      .add(NoMigrationDuringDeployment.class)
      .add(MigrateDuringDeployment.class)
      .build();

  @Test
  public void checkServiceDocumentsAreAnnotatedWithUpgradeAnnotation() throws Throwable {
    Collection<String> missingUpgradeAnnotation = new HashSet<>();
    ClassLoader cl = ClassLoader.getSystemClassLoader();
    ClassPath classPath = ClassPath.from(cl);

    for (ClassInfo classFile : classPath.getAllClasses()) {
      if (classFile.getName().contains(PHOTON_CONTROLLER_PACKAGE)
          && !classFile.getName().endsWith("Test")
          && !classFile.getName().contains(".Test")
          && !classFile.getName().contains("Test$")) {
        Class<?> type = classFile.load();
          if (type.getSuperclass() != null && type.getSuperclass() == ServiceDocument.class) {
            boolean hasUpgradeAnnotation = false;
            for (Annotation a : type.getAnnotations()) {
              hasUpgradeAnnotation = hasUpgradeAnnotation || upgradeAnnotations.contains(a.annotationType());
            }
            if (!hasUpgradeAnnotation) {
              missingUpgradeAnnotation.add(type.getName());
            }
          }
      }
    }
    String errorMessage = "The following classes are missing upgrade annotations:\n";
    if (!missingUpgradeAnnotation.isEmpty()) {
      errorMessage += String.join("\n", missingUpgradeAnnotation);
    }
    assertThat(errorMessage, missingUpgradeAnnotation.size(), is(0));
  }

  @Test
  public void checkServiceDocumentsAreAnnotatedWithDeploymentAnnotation() throws Throwable {
    Collection<String> missingUpgradeAnnotation = new HashSet<>();
    ClassLoader cl = ClassLoader.getSystemClassLoader();
    ClassPath classPath = ClassPath.from(cl);

    for (ClassInfo classFile : classPath.getAllClasses()) {
      if (classFile.getName().contains(PHOTON_CONTROLLER_PACKAGE)
          && !classFile.getName().endsWith("Test")
          && !classFile.getName().contains(".Test")
          && !classFile.getName().contains("Test$")) {
        Class<?> type = classFile.load();
          if (type.getSuperclass() != null && type.getSuperclass() == ServiceDocument.class) {
            boolean hasUpgradeAnnotation = false;
            for (Annotation a : type.getAnnotations()) {
              hasUpgradeAnnotation = hasUpgradeAnnotation || deploymentAnnotations.contains(a.annotationType());
            }
            if (!hasUpgradeAnnotation) {
              missingUpgradeAnnotation.add(type.getName());
            }
          }
      }
    }
    String errorMessage = "The following classes are missing upgrade annotations:\n";
    if (!missingUpgradeAnnotation.isEmpty()) {
      errorMessage += String.join("\n", missingUpgradeAnnotation);
    }
    assertThat(errorMessage, missingUpgradeAnnotation.size(), is(0));
  }

  @Test
  public void checkAllTransformationServicesAreStartedInDeployer() throws Throwable {
    Collection<String> transformationServicePaths = new HashSet<>();

    Collection<String> factoryPaths = new HashSet<>();
    for (Class<?> type : DeployerXenonServiceHost.FACTORY_SERVICES) {
      Field f = type.getField(UriUtils.FIELD_NAME_SELF_LINK);
      String path = (String) f.get(null);
      factoryPaths.add(path);
    }

    ClassLoader cl = ClassLoader.getSystemClassLoader();
    ClassPath classPath = ClassPath.from(cl);

    for (ClassInfo classFile : classPath.getAllClasses()) {
      if (classFile.getName().contains(PHOTON_CONTROLLER_PACKAGE)
          && !classFile.getName().endsWith("Test")
          && !classFile.getName().contains(".Test")
          && !classFile.getName().contains("Test$")) {
        Collection<Class<?>> allClasses = getNestedClasses(classFile.load());

        for (Class<?> type : allClasses) {
          if (type.getSuperclass() != null && type.getSuperclass() == ServiceDocument.class) {
            for (Annotation a : type.getAnnotations()) {
              if (a.annotationType() == MigrateDuringUpgrade.class) {
                MigrateDuringUpgrade u = (MigrateDuringUpgrade) a;
                transformationServicePaths.add(u.transformationServicePath());
              }
            }
          }
        }
      }
    }

    transformationServicePaths.removeAll(factoryPaths);
    String errorMessage = "The following transformation services are not starte in deployer:\n";
    if (!transformationServicePaths.isEmpty()) {
      errorMessage += String.join("\n", transformationServicePaths);
    }
    assertThat(errorMessage, transformationServicePaths.size(), is(0));
  }

  @Test
  public void checkSourceFactoriesExist() throws Throwable {
    Set<String> factoryPaths = loadFactoryLinks();

    ClassLoader cl = ClassLoader.getSystemClassLoader();
    ClassPath classPath = ClassPath.from(cl);

    Set<String> errors = new HashSet<>();

    for (ClassInfo classFile : classPath.getAllClasses()) {
      if (classFile.getName().contains(PHOTON_CONTROLLER_PACKAGE)
          && !classFile.getName().endsWith("Test")
          && !classFile.getName().contains(".Test")
          && !classFile.getName().contains("Test$")) {
        Collection<Class<?>> allClasses = getNestedClasses(classFile.load());

        for (Class<?> type : allClasses) {
          if (type.getSuperclass() != null && type.getSuperclass() == ServiceDocument.class) {
            for (Annotation a : type.getAnnotations()) {
              if (a.annotationType() == MigrateDuringUpgrade.class) {
                MigrateDuringUpgrade u = (MigrateDuringUpgrade) a;

                if (!factoryPaths.contains(u.sourceFactoryServicePath())) {
                  errors.add(type.getName()
                      + " refers to unknown sourceFactoryPath ["
                      + u.sourceFactoryServicePath() + "]");
                }
              }
            }
          }
        }
      }
    }

    String errorMessage = "";
    if (!errors.isEmpty()) {
      errorMessage = String.join("\n", errors);
    }
    assertThat(errorMessage, errors.size(), is(0));
  }

  @Test
  public void checkDestinationFactoriesExist() throws Throwable {
    Set<String> factoryPaths = loadFactoryLinks();

    ClassLoader cl = ClassLoader.getSystemClassLoader();
    ClassPath classPath = ClassPath.from(cl);

    Set<String> errors = new HashSet<>();

    for (ClassInfo classFile : classPath.getAllClasses()) {
      if (classFile.getName().contains(PHOTON_CONTROLLER_PACKAGE)
          && !classFile.getName().endsWith("Test")
          && !classFile.getName().contains(".Test")
          && !classFile.getName().contains("Test$")) {
        Collection<Class<?>> allClasses = getNestedClasses(classFile.load());

        for (Class<?> type : allClasses) {
          if (type.getSuperclass() != null && type.getSuperclass() == ServiceDocument.class) {
            for (Annotation a : type.getAnnotations()) {
              if (a.annotationType() == MigrateDuringUpgrade.class) {
                MigrateDuringUpgrade u = (MigrateDuringUpgrade) a;

                if (!factoryPaths.contains(u.destinationFactoryServicePath())) {
                  errors.add(type.getName()
                      + " refers to unknown sourceFactoryPath ["
                      + u.sourceFactoryServicePath() + "]");
                }
              }
            }
          }
        }
      }
    }

    String errorMessage = "";
    if (!errors.isEmpty()) {
      errorMessage = String.join("\n", errors);
    }
    assertThat(errorMessage, errors.size(), is(0));
  }

  private Set<String> loadFactoryLinks() throws Throwable {
    Set<String> factoryPaths = new HashSet<>();
    factoryPaths.addAll(loadCloudStoreFactoryLinks());
    return factoryPaths;
  }

  private Set<String> loadCloudStoreFactoryLinks() throws Throwable {
    Set<String> factoryPaths = new HashSet<>();
    for (Class<?> type : CloudStoreServiceGroup.FACTORY_SERVICES) {
      Field f = type.getField(UriUtils.FIELD_NAME_SELF_LINK);
      String path = (String) f.get(null);
      factoryPaths.add(path);
    }
    for (Class<?> type : CloudStoreServiceGroup.FACTORY_SERVICES_MAP.keySet()) {
      Field f = type.getField(UriUtils.FIELD_NAME_FACTORY_LINK);
      String path = (String) f.get(null);
      factoryPaths.add(path);
    }
    return factoryPaths;
  }

  private Collection<Class<?>> getNestedClasses(Class<?> type) {
    Collection<Class<?>> classes = new ArrayList<>();
    classes.add(type);

    for (Class<?> c : type.getDeclaredClasses()) {
      classes.addAll(getNestedClasses(c));
    }

    return classes;
  }
}
