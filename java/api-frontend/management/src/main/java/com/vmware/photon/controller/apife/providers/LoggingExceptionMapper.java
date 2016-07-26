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

package com.vmware.photon.controller.apife.providers;

import com.vmware.photon.controller.apife.exceptions.external.ErrorCode;
import com.vmware.photon.controller.apife.exceptions.external.ExternalException;
import static com.vmware.photon.controller.apife.Responses.externalException;
import static com.vmware.photon.controller.apife.Responses.serverError;

import org.glassfish.jersey.internal.inject.ExtractorException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.core.Response;
import javax.ws.rs.ext.ExceptionMapper;
import javax.ws.rs.ext.Provider;

/**
 * The logging exception exception mapper is designed to catch and process all exceptions that
 * occur during API operations but that are unexpected and therefore not handled by the specific
 * WebApplicationExceptionMapper or ConstraintViolationExceptionMapper.
 * IF exceptions hit here they should be analyzed and a specific mapper should be written to deal with
 * the exception correctly.
 * By they time they get this far, all we return is a generic 500 server error.
 */
@Provider
public class LoggingExceptionMapper implements ExceptionMapper<Throwable> {

  private static final Logger logger = LoggerFactory.getLogger(LoggingExceptionMapper.class);

  @Override
  public Response toResponse(Throwable e) {
    if (e instanceof ExtractorException &&
        e.getMessage().contains("No enum constant ImageReplicationType")) {
      logger.error("IllegalArgumentException", e);
      return externalException(new ExternalException(ErrorCode.INVALID_IMAGE_REPLICATION_TYPE,
          "Image replication type unsupported", null));
    }

    logger.error("Error handling a request", e);
    return serverError();
  }
}
