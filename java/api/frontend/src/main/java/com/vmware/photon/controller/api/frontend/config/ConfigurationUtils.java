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

package com.vmware.photon.controller.api.frontend.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import io.dropwizard.configuration.ConfigurationException;
import io.dropwizard.configuration.ConfigurationFactory;
import io.dropwizard.configuration.DefaultConfigurationFactoryFactory;
import io.dropwizard.jackson.Jackson;
import io.dropwizard.validation.valuehandling.OptionalValidatedValueUnwrapper;
import org.apache.commons.lang3.StringUtils;
import org.hibernate.validator.HibernateValidator;
import static com.google.common.base.Preconditions.checkArgument;

import javax.validation.Validation;
import javax.validation.ValidatorFactory;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;

/**
 * Class providing helper functions for configuration parsing.
 */
public class ConfigurationUtils {

  // copied from io.dropwizard.cli.ConfiguredCommand
  public static ApiFeConfiguration parseConfiguration(String filename)
      throws IOException, ConfigurationException {
    ObjectMapper objectMapper = Jackson.newObjectMapper(new YAMLFactory());
    ValidatorFactory validatorFactory = Validation
        .byProvider(HibernateValidator.class)
        .configure()
        .addValidatedValueHandler(new OptionalValidatedValueUnwrapper())
        .buildValidatorFactory();
    final ConfigurationFactory<ApiFeStaticConfiguration> configurationFactory =
        new DefaultConfigurationFactoryFactory<ApiFeStaticConfiguration>().create(ApiFeStaticConfiguration.class,
            validatorFactory.getValidator(),
            objectMapper, "dw");
    checkArgument(StringUtils.isNotBlank(filename), "filename cannot be blank");
    final File file = new File(filename);
    if (!file.exists()) {
      throw new FileNotFoundException("File " + file + " not found");
    }
    return configurationFactory.build(file);
  }
}
