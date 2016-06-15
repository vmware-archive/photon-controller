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

package com.vmware.photon.controller.common.xenon;

import com.vmware.photon.controller.common.xenon.exceptions.XenonRuntimeException;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.TaskState;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.NodeGroupBroadcastResponse;
import com.vmware.xenon.services.common.QueryTask;

import com.google.common.collect.ImmutableMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import java.lang.reflect.Field;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Class implements utility methods for QueryTask objects.
 */
public class QueryTaskUtils {

  /**
   * The name of the field in the service state that stores the parent link.
   */
  protected static final String PARENT_LINK_FIELD_NAME = "parentLink";
  /**
   * The format string for the name of the field in the service state that
   * stores the execution stage.
   */
  protected static final String STAGE_FIELD_NAME_FORMAT = "%s.stage";
  private static final Logger logger = LoggerFactory.getLogger(QueryTaskUtils.class);

  /**
   * This method builds a query specification which will return service instances
   * of type childClass which have the parentLink field set to the passed in value.
   *
   * @param selfLink
   * @param childClass
   * @return
   */
  public static QueryTask.QuerySpecification buildChildServiceQuerySpec(
      final String selfLink,
      final Class childClass) {
    return buildChildServiceQuerySpec(selfLink, childClass, new QueryTask.Query[0]);
  }

  /**
   * This method builds a query specification which will return service instances
   * of type childClass which have the parentLink field set to the passed in value.
   *
   * @param selfLink
   * @param childClass
   * @param additionalClauses
   * @return
   */
  public static QueryTask.QuerySpecification buildChildServiceQuerySpec(
      final String selfLink,
      final Class childClass,
      final QueryTask.Query... additionalClauses) {
    checkArgument(childClass != null, "childClass cannot be null");
    checkArgument(additionalClauses != null, "additionalClauses cannot be null");

    QueryTask.Query parentLinkClause = new QueryTask.Query()
        .setTermPropertyName(PARENT_LINK_FIELD_NAME)
        .setTermMatchValue(selfLink);

    QueryTask.QuerySpecification spec = buildQuerySpec(childClass, additionalClauses);
    spec.query.addBooleanClause(parentLinkClause);

    return spec;
  }

  /**
   * Builds a query specification which will query for service instances of type childClass
   * which have the parentLink field set to the param value and the stage execution field
   * one of the values passed using stages param.
   *
   * @param selfLink
   * @param childClass
   * @return
   */
  public static QueryTask.QuerySpecification buildChildServiceTaskStatusQuerySpec(
      final String selfLink,
      final Class childClass,
      final TaskState.TaskStage... stages) {
    checkArgument(stages != null && stages.length >= 1, "stages.length must be >= 1");

    QueryTask.Query parentLinkClause = new QueryTask.Query()
        .setTermPropertyName(PARENT_LINK_FIELD_NAME)
        .setTermMatchValue(selfLink);

    QueryTask.QuerySpecification spec = buildTaskStatusQuerySpec(childClass, stages);
    spec.query.addBooleanClause(parentLinkClause);

    return spec;
  }

  /**
   * Builds a query specification which will query for service instances of type taskClass
   * with the stage field one of the values passed in as a parameter.
   *
   * @return
   */
  public static QueryTask.QuerySpecification buildTaskStatusQuerySpec(
      final Class taskClass,
      final TaskState.TaskStage... stages) {
    checkArgument(stages != null && stages.length >= 1, "stages.length must be >= 1");

    String fieldName = findStageFieldName(taskClass);
    if (null == fieldName) {
      throw new IllegalArgumentException(
          String.format("%s does not have a member of type %s", taskClass, TaskState.class));
    }

    QueryTask.Query stageQuery =
        buildTaskStateQuery(String.format(STAGE_FIELD_NAME_FORMAT, fieldName), stages);
    return buildQuerySpec(taskClass, stageQuery);
  }

  /**
   * This method gets the document links from an operation whose body is a {@link NodeGroupBroadcastResponse} generated
   * by a broadcast query operation.
   *
   * @param queryResult Supplies a completed query operation.
   * @return A set of document links in unsorted order.
   */
  public static Set<String> getBroadcastQueryDocumentLinks(Operation queryResult) {
    NodeGroupBroadcastResponse queryResponse = queryResult.getBody(NodeGroupBroadcastResponse.class);
    return getBroadcastQueryDocumentLinks(queryResponse);
  }

  /**
   * This method gets the document links from a {@link NodeGroupBroadcastResponse} generated by a broadcast query
   * operation by merging the document links in the various responses into a single result set.
   *
   * @param response Supplies a {@link NodeGroupBroadcastResponse}.
   * @return A set of document links in unsorted order.
   */
  public static Set<String> getBroadcastQueryDocumentLinks(NodeGroupBroadcastResponse response) {

    if (!response.failures.isEmpty()) {
      throw new XenonRuntimeException("Failures detected in query task response: " +
          Utils.toJson(false, true, response));
    }

    Set<String> documentLinks = new HashSet<>();
    for (Map.Entry<URI, String> entry : response.jsonResponses.entrySet()) {
      QueryTask queryTask = Utils.fromJson(entry.getValue(), QueryTask.class);
      if (null != queryTask.results) {
        for (String documentLink : queryTask.results.documentLinks) {
          documentLinks.add(documentLink);
        }
      }
    }

    return documentLinks;
  }

  /**
   * This method gets the documents from an operation whose body is a {@link NodeGroupBroadcastResponse} generated by a
   * broadcast query operation. It will choose a document if and only if it was sent by the host that owns it.
   *
   * @param queryResult
   * @return
   */
  public static <T extends ServiceDocument> List<T> getBroadcastQueryDocuments(
      Class<T> documentType, Operation queryResult) {
    NodeGroupBroadcastResponse queryResponse = queryResult.getBody(NodeGroupBroadcastResponse.class);
    return getBroadcastQueryDocuments(documentType, queryResponse);
  }

  /**
   * This method gets the documents from a {@link NodeGroupBroadcastResponse} generated by a broadcast query operation
   * by merging the documents in the various responses into a single result set. It will choose a document if and only
   * if it was sent by the host that owns it.
   *
   * @param response
   * @return
   */
  public static <T extends ServiceDocument> List<T> getBroadcastQueryDocuments(
      Class<T> documentType, NodeGroupBroadcastResponse response) {

    if (!response.failures.isEmpty()) {
      throw new XenonRuntimeException("Failures detected in query task response: " +
          Utils.toJson(false, true, response));
    }

    List<T> documents = new ArrayList<>();
    for (Map.Entry<URI, String> entry : response.jsonResponses.entrySet()) {
      QueryTask queryTask = Utils.fromJson(entry.getValue(), QueryTask.class);
      if (null != queryTask.results && queryTask.results.documents != null) {
        for (Object value : queryTask.results.documents.values()) {
          T document = Utils.fromJson(value, documentType);
          if (queryTask.documentOwner.equals(document.documentOwner)) {
            documents.add(document);
          }
        }
      }
    }

    return documents;
  }

  /**
   * Get the URI of the service document from the query response.
   * <p>
   * This is to be used for "indirect" Xenon calls. A call is returned as
   * being accepted, and subsequent calls need to be issued to check
   * if the service is finished.
   *
   * @param queryResult
   * @return
   */
  public static <T extends ServiceDocument> URI getServiceDocumentUri(Operation queryResult) {
    URI uri = queryResult.getUri();
    QueryTask task = queryResult.getBody(QueryTask.class);

    return UriUtils.buildUri(uri.getScheme(), uri.getHost(), uri.getPort(), task.documentSelfLink, null);
  }

  /**
   * Extract the status of the task from the query result.
   *
   * @param queryResult
   * @return
   */
  public static TaskState.TaskStage getServiceState(Operation queryResult) {
    QueryTask task = queryResult.getBody(QueryTask.class);
    return task.taskInfo.stage;
  }

  /**
   * Builds a QueryTask.QuerySpecification which will query for documents of type T.
   * Any other filter clauses are optional.
   * This allows for a query that returns all documents of type T.
   * This also expands the content of the resulting documents.
   *
   * @param documentType
   * @param terms
   * @return
   */
  public static QueryTask.QuerySpecification buildQuerySpec(Class documentType, ImmutableMap<String, String> terms) {
    checkNotNull(documentType, "Cannot build query spec for unspecified documentType");
    QueryTask.QuerySpecification spec = new QueryTask.QuerySpecification();
    QueryTask.Query documentKindClause = new QueryTask.Query()
        .setTermPropertyName(ServiceDocument.FIELD_NAME_KIND)
        .setTermMatchValue(Utils.buildKind(documentType));

    if (terms == null || terms.isEmpty()) {
      // since there are no other clauses
      // skip adding boolean clauses
      // to workaround the Xenon requirement to have at least 2
      // boolean clauses for a valid query
      spec.query = documentKindClause;
    } else {
      spec.query.addBooleanClause(documentKindClause);
      for (String key : terms.keySet()) {
        QueryTask.Query clause = new QueryTask.Query()
            .setTermPropertyName(key)
            .setTermMatchValue(terms.get(key));
        spec.query.addBooleanClause(clause);
      }
    }

    return spec;
  }

  /**
   * Builds a QueryTask.QuerySpecification instance using the passed in arguments.
   *
   * @param childClass
   * @param additionalClauses
   * @return
   */
  private static QueryTask.QuerySpecification buildQuerySpec(
      final Class childClass,
      final QueryTask.Query... additionalClauses) {
    QueryTask.Query kindClause = new QueryTask.Query()
        .setTermPropertyName(ServiceDocument.FIELD_NAME_KIND)
        .setTermMatchValue(Utils.buildKind(childClass));

    QueryTask.QuerySpecification spec = new QueryTask.QuerySpecification();
    spec.query.addBooleanClause(kindClause);

    for (QueryTask.Query clause : additionalClauses) {
      spec.query.addBooleanClause(clause);
    }

    return spec;
  }

  /**
   * Builds a QueryTask.Query instance using the passed in parameters.
   *
   * @param propertyName
   * @param stages
   * @return
   */
  private static QueryTask.Query buildTaskStateQuery(String propertyName, TaskState.TaskStage... stages) {
    if (stages.length == 1) {
      // we want to match only one TaskStage value
      return new QueryTask.Query()
          .setTermPropertyName(propertyName)
          .setTermMatchValue(stages[0].toString());
    }

    // we want to match one of multiple TaskStage values
    QueryTask.Query query = new QueryTask.Query();
    for (TaskState.TaskStage stage : stages) {
      QueryTask.Query stageQuery = new QueryTask.Query()
          .setTermPropertyName(propertyName)
          .setTermMatchValue(stage.toString());
      stageQuery.occurance = QueryTask.Query.Occurance.SHOULD_OCCUR;

      query.addBooleanClause(stageQuery);
    }

    return query;
  }

  /**
   * Finds the name of the field of type {@link com.vmware.xenon.common.TaskState} in
   * the class passed as a parameter.
   *
   * @param childClass
   * @return
   */
  private static String findStageFieldName(final Class childClass) {
    for (Field field : childClass.getFields()) {
      if (TaskState.class.isAssignableFrom(field.getType())) {
        return field.getName();
      }
    }

    return null;
  }

  /**
   * Dumps the results of a query for logging purposes.
   *
   * @param service
   * @param documentLinks
   */
  public static void logQueryResults(Service service, Collection<String> documentLinks) {
    String summary = "Query from service " + service.getSelfLink() + " returned " +
        Integer.toString(documentLinks.size()) + " results";
    for (String documentLink : documentLinks) {
      summary += System.lineSeparator() + "  - " + documentLink;
    }
    ServiceUtils.logInfo(service, summary);
  }
}
