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

package com.vmware.photon.controller.api.client.resource;

import com.vmware.photon.controller.api.client.RestClient;
import com.vmware.photon.controller.api.model.Task;
import static com.vmware.photon.controller.api.client.RestClient.Method;

import com.fasterxml.jackson.core.type.TypeReference;
import com.google.common.util.concurrent.FutureCallback;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;

import java.io.IOException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

/**
 * Tasks Api.
 */
public class TasksApi extends ApiBase {

  public TasksApi(RestClient restClient) {
    super(restClient);
  }

  @Override
  public String getBasePath() {
    return "/tasks";
  }

  /**
   * Get task details synchronously.
   *
   * @param taskId
   * @return
   * @throws IOException
   */
  public Task getTask(final String taskId) throws IOException {
    String path = getBasePath() + "/" + taskId;

    Future<HttpResponse> response = this.restClient.performAsync(
        Method.GET,
        path,
        null /* payload */,
        null /* callback */);

    try {
      return parseGetTaskHttpResponse(response.get());
    } catch (InterruptedException e) {
      throw new RuntimeException(e);
    } catch (ExecutionException e) {
      throw new RuntimeException(e);
    }
  }

  /**
   * Get task details asynchronously.
   *
   * @param taskId
   * @param responseCallback
   * @throws IOException
   */
  public void getTaskAsync(final String taskId, final FutureCallback<Task> responseCallback) throws IOException {
    String path = getBasePath() + "/" + taskId;

    getObjectByPathAsync(path, responseCallback, new TypeReference<Task>() {
    });
  }

  private Task parseGetTaskHttpResponse(HttpResponse response) throws IOException {
    restClient.checkResponse(response, HttpStatus.SC_OK);
    return parseTaskFromHttpResponse(response);
  }
}
