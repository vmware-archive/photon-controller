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

package com.vmware.photon.controller.common.config;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.google.common.base.Joiner;

import javax.validation.ConstraintViolation;
import javax.validation.Validation;
import javax.validation.Validator;
import javax.validation.ValidatorFactory;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * ConfigBuilder parses and validates a YAML config file into a config bean.
 *
 * @param <T> the config bean type.
 */
public class ConfigBuilder<T> {
  private final Class<T> configClass;
  private final String configFile;
  private T config = null;

  private ConfigBuilder(Class<T> configClass, String configFile) {
    this.configClass = configClass;
    this.configFile = configFile;
  }

  /**
   * Builds the config bean by parsing the config file and then running any validations.
   *
   * @param configClass config bean class for initialization.
   * @param configFile  path to the YAML encoded config file.
   * @param <T>         the config bean type.
   * @return validated config.
   * @throws BadConfigException if the config was invalid.
   */
  public static <T> T build(Class<T> configClass, String configFile) throws BadConfigException {
    ConfigBuilder<T> builder = new ConfigBuilder<>(configClass, configFile);
    builder.parse();
    builder.validate();
    return builder.config;
  }

  private void parse() throws BadConfigException {
    ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
    try {
      config = mapper.readValue(new File(configFile), configClass);
    } catch (FileNotFoundException e) {
      throw new BadConfigException("Could not find configuration file: " + configFile);
    } catch (JsonProcessingException e) {
      e.printStackTrace();
      throw new BadConfigException("Could not parse configuration: " + e.getMessage(), e);
    } catch (IOException e) {
      throw new BadConfigException("Could not read configuration file" + e.getMessage(), e);
    }
  }

  private void validate() throws BadConfigException {
    ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
    Validator validator = factory.getValidator();
    Set<ConstraintViolation<T>> violations = validator.validate(config);

    if (!violations.isEmpty()) {
      List<String> errors = new ArrayList<>();
      errors.add("Configuration is not valid:");
      for (ConstraintViolation<T> violation : violations) {
        String error = String.format("\t%s %s (was %s)", violation.getPropertyPath(), violation.getMessage(),
            violation.getInvalidValue());
        errors.add(error);
      }
      throw new BadConfigException(Joiner.on("\n").join(errors));
    }
  }
}
