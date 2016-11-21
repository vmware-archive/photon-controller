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

package com.vmware.photon.controller.common.manifest;

import com.google.common.base.Joiner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.JarURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.List;
import java.util.jar.Attributes;
import java.util.jar.Manifest;
import static java.util.jar.Attributes.Name.SPECIFICATION_VERSION;

/**
 * Build Information.
 */
public class BuildInfo {

  private static final Logger logger = LoggerFactory.getLogger(BuildInfo.class);
  private static final Attributes.Name GIT_COMMIT = new Attributes.Name("Git-Commit");
  private static final Attributes.Name BUILT_DATE = new Attributes.Name("Built-Date");

  private final String version;
  private final String date;
  private final String commit;

  private BuildInfo(String version, String date, String commit) {
    this.version = version;
    this.date = date;
    this.commit = commit;
  }

  /**
   * Parses the Jar (if available) for the build info attributes in the manifest file.
   *
   * @param clazz class that resides in the Jar containing the build info manifest.
   * @return parsed build info attributes.
   */
  public static BuildInfo get(Class<?> clazz) {
    try {
      URL resource = clazz.getResource(clazz.getSimpleName() + ".class");
      if (resource != null) {
        URLConnection urlConn = resource.openConnection();
        if (urlConn instanceof JarURLConnection) {
          JarURLConnection jarUrlConn = (JarURLConnection) urlConn;
          Manifest manifest = jarUrlConn.getManifest();
          Attributes attributes = null;
          if (manifest != null) {
            attributes = manifest.getMainAttributes();
          }
          String version = "N/A";
          String date = "N/A";
          String commit = "N/A";
          if (attributes != null) {
            version = attributes.getValue(SPECIFICATION_VERSION);
            date = attributes.getValue(BUILT_DATE);
            commit = attributes.getValue(GIT_COMMIT);
          }
          return new BuildInfo(version, date, commit);
        }
      }
    } catch (IOException e) {
      logger.warn("Could not bind manifest", e);
    }
    return new BuildInfo(null, null, null);
  }

  public String getVersion() {
    return version;
  }

  public String getDate() {
    return date;
  }

  public String getCommit() {
    return commit;
  }

  @Override
  public String toString() {
    List<String> parts = new ArrayList<>();
    if (version != null) {
      parts.add("version: " + version);
    }

    if (commit != null) {
      parts.add("git-commit: " + commit);
    }

    if (date != null) {
      parts.add("built-date: " + date);
    }

    if (parts.isEmpty()) {
      return "build-info: N/A";
    } else {
      return Joiner.on(", ").join(parts);
    }
  }
}
