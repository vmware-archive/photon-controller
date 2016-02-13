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

import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.TaskState;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.QueryTask;

import com.google.common.collect.ImmutableMap;
import org.testng.annotations.Test;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.startsWith;
import static org.testng.Assert.fail;

import java.util.UUID;

/**
 * Tests {@link QueryTaskUtils}.
 */
public class QueryTaskUtilsTest {
  /**
   * Dummy test case to make Intellij recognize this as a test class.
   */
  @Test
  private void dummy() {
  }

  /**
   * Tests the buildChildServiceQuerySpec method.
   */
  public class BuildChildServiceQuerySpecTest {
    /**
     * Tests that spec is created correctly when additional clauses are NOT provided.
     */
    @Test
    public void testNoAdditionalClauses() {
      QueryTask.QuerySpecification spec = QueryTaskUtils.buildChildServiceQuerySpec("parentLink", Class.class);
      assertThat(spec.query, notNullValue());
      assertThat(spec.query.term, nullValue());
      assertThat(spec.query.booleanClauses.size(), is(2));
      assertThat(spec.query.booleanClauses.get(0).term.matchValue, is(Utils.buildKind(Class.class)));
      assertThat(spec.query.booleanClauses.get(0).term.matchType, nullValue());
      assertThat(spec.query.booleanClauses.get(0).occurance, is(QueryTask.Query.Occurance.MUST_OCCUR));
      assertThat(spec.query.booleanClauses.get(0).term.propertyName, is(ServiceDocument.FIELD_NAME_KIND));
      assertThat(spec.query.booleanClauses.get(1).term.propertyName, is(QueryTaskUtils.PARENT_LINK_FIELD_NAME));
      assertThat(spec.query.booleanClauses.get(1).term.matchValue, is("parentLink"));
      assertThat(spec.query.booleanClauses.get(1).term.matchType, nullValue());
      assertThat(spec.query.booleanClauses.get(1).occurance, is(QueryTask.Query.Occurance.MUST_OCCUR));
    }

    /**
     * Tests that spec is created correctly when additional clauses are provided.
     */
    @Test
    public void testWithAdditionalClauses() {
      QueryTask.Query extra = new QueryTask.Query();
      extra.setTermPropertyName("extraTerm");
      extra.setTermMatchValue("extraTermValue");

      QueryTask.QuerySpecification spec = QueryTaskUtils.buildChildServiceQuerySpec("parentLink", Class.class, extra);
      assertThat(spec.query, notNullValue());
      assertThat(spec.query.term, nullValue());
      assertThat(spec.query.booleanClauses.size(), is(3));
      assertThat(spec.query.booleanClauses.get(1).term.propertyName, is("extraTerm"));
      assertThat(spec.query.booleanClauses.get(1).term.matchValue, is("extraTermValue"));
      assertThat(spec.query.booleanClauses.get(1).term.matchType, nullValue());
      assertThat(spec.query.booleanClauses.get(1).occurance, is(QueryTask.Query.Occurance.MUST_OCCUR));
    }

    /**
     * Test that spec is built successfully with a null parent link.
     */
    @Test
    public void testNullParentLink() {
      QueryTask.QuerySpecification spec = QueryTaskUtils.buildChildServiceQuerySpec(null, Class.class);
      assertThat(spec.query, notNullValue());
      assertThat(spec.query.term, nullValue());
      assertThat(spec.query.booleanClauses.size(), is(2));
      assertThat(spec.query.booleanClauses.get(1).term.propertyName, is(QueryTaskUtils.PARENT_LINK_FIELD_NAME));
      assertThat(spec.query.booleanClauses.get(1).term.matchValue, nullValue());
    }

    /**
     * Test that exception is raised if childClass param is passed in as null.
     */
    @Test
    public void testNullChildClass() {
      try {
        QueryTaskUtils.buildChildServiceQuerySpec("parentLink", null);
        fail("did not throw exception when 'childClass' was 'null'");
      } catch (IllegalArgumentException ex) {
        assertThat(ex.getMessage(), is("childClass cannot be null"));
      }
    }

    /**
     * Tests that exception is raised if additionalClauses param is passed in as null.
     */
    @Test
    public void testNullAdditionalClauses() {
      try {
        QueryTaskUtils.buildChildServiceQuerySpec("parentLink", Class.class, (QueryTask.Query[]) null);
        fail("did not throw exception when 'additionalClauses' was 'null'");
      } catch (IllegalArgumentException ex) {
        assertThat(ex.getMessage(), is("additionalClauses cannot be null"));
      }
    }
  }

  /**
   * Tests the buildChildServiceTaskStatusQuerySpec method.
   */
  public class BuildChildServiceTaskStatusQuerySpecTest {
    /**
     * Tests that spec is built successfully when only one stage value is provided.
     */
    @Test
    public void testSingleStageValue() {
      QueryTask.QuerySpecification spec =
          QueryTaskUtils.buildChildServiceTaskStatusQuerySpec(
              "parentLink", TestDerivedStateClass.class, TaskState.TaskStage.FINISHED);

      assertThat(spec.query, notNullValue());
      assertThat(spec.query.term, nullValue());
      assertThat(spec.query.booleanClauses.size(), is(3));
      assertThat(
          spec.query.booleanClauses.get(1).term.propertyName,
          is(String.format(QueryTaskUtils.STAGE_FIELD_NAME_FORMAT, "state")));
      assertThat(
          spec.query.booleanClauses.get(1).term.matchValue,
          is(TaskState.TaskStage.FINISHED.toString()));

      // validate that we have a parent link
      assertThat(spec.query.booleanClauses.get(2).term.propertyName, is(QueryTaskUtils.PARENT_LINK_FIELD_NAME));
      assertThat(spec.query.booleanClauses.get(2).term.matchValue, is("parentLink"));
      assertThat(spec.query.booleanClauses.get(2).term.matchType, nullValue());
      assertThat(spec.query.booleanClauses.get(2).occurance, is(QueryTask.Query.Occurance.MUST_OCCUR));
    }

    /**
     * Tests that spec is build successfully when multiple stage values are provided.
     */
    @Test
    public void testMultipleStageValues() {
      QueryTask.QuerySpecification spec =
          QueryTaskUtils.buildChildServiceTaskStatusQuerySpec(
              "parentLink", TestStateClass.class, TaskState.TaskStage.FAILED, TaskState.TaskStage.CANCELLED);

      assertThat(spec.query, notNullValue());
      assertThat(spec.query.term, nullValue());
      assertThat(spec.query.booleanClauses.size(), is(3));

      QueryTask.Query stageQuery = spec.query.booleanClauses.get(1);
      assertThat(stageQuery, notNullValue());
      assertThat(stageQuery.term, nullValue());
      assertThat(stageQuery.booleanClauses.size(), is(2));

      assertThat(
          stageQuery.booleanClauses.get(0).term.propertyName,
          is(String.format(QueryTaskUtils.STAGE_FIELD_NAME_FORMAT, "state")));
      assertThat(stageQuery.booleanClauses.get(0).term.matchValue, is(TaskState.TaskStage.FAILED.toString()));
      assertThat(stageQuery.booleanClauses.get(0).occurance, is(QueryTask.Query.Occurance.SHOULD_OCCUR));

      assertThat(
          stageQuery.booleanClauses.get(1).term.propertyName,
          is(String.format(QueryTaskUtils.STAGE_FIELD_NAME_FORMAT, "state")));
      assertThat(stageQuery.booleanClauses.get(1).term.matchValue, is(TaskState.TaskStage.CANCELLED.toString()));
      assertThat(stageQuery.booleanClauses.get(1).occurance, is(QueryTask.Query.Occurance.SHOULD_OCCUR));

      // validate that we have a parent link
      assertThat(spec.query.booleanClauses.get(2).term.propertyName, is(QueryTaskUtils.PARENT_LINK_FIELD_NAME));
      assertThat(spec.query.booleanClauses.get(2).term.matchValue, is("parentLink"));
      assertThat(spec.query.booleanClauses.get(2).term.matchType, nullValue());
      assertThat(spec.query.booleanClauses.get(2).occurance, is(QueryTask.Query.Occurance.MUST_OCCUR));
    }

    /**
     * Tests that spec is built successfully when only one stage value is provided.
     */
    @Test
    public void testSingleStageValueWithDerivedTaskStateClass() {
      QueryTask.QuerySpecification spec =
          QueryTaskUtils.buildChildServiceTaskStatusQuerySpec(
              "parentLink", TestStateClass.class, TaskState.TaskStage.FINISHED);

      assertThat(spec.query, notNullValue());
      assertThat(spec.query.term, nullValue());
      assertThat(spec.query.booleanClauses.size(), is(3));
      assertThat(
          spec.query.booleanClauses.get(1).term.propertyName,
          is(String.format(QueryTaskUtils.STAGE_FIELD_NAME_FORMAT, "state")));
      assertThat(
          spec.query.booleanClauses.get(1).term.matchValue,
          is(TaskState.TaskStage.FINISHED.toString()));
    }

    /**
     * Tests that exception is raised when no stages param is provided.
     */
    @Test
    public void testNoTaskStageList() {
      try {
        QueryTaskUtils.buildChildServiceTaskStatusQuerySpec("parentLink", Class.class);
        fail("did not throw exception when stages param was not provided");
      } catch (IllegalArgumentException ex) {
        assertThat(ex.getMessage(), startsWith("stages.length must be >= 1"));
      }
    }
  }

  /**
   * Tests the buildTaskStatusQuerySpec method.
   */
  public class BuildTaskStatusQuerySpecTest {
    /**
     * Tests that spec is built successfully when only one stage value is provided.
     */
    @Test
    public void testSingleStageValue() {
      QueryTask.QuerySpecification spec =
          QueryTaskUtils.buildTaskStatusQuerySpec(
              TestDerivedStateClass.class, TaskState.TaskStage.FINISHED);

      assertThat(spec.query, notNullValue());
      assertThat(spec.query.term, nullValue());
      assertThat(spec.query.booleanClauses.size(), is(2));
      assertThat(
          spec.query.booleanClauses.get(1).term.propertyName,
          is(String.format(QueryTaskUtils.STAGE_FIELD_NAME_FORMAT, "state")));
      assertThat(
          spec.query.booleanClauses.get(1).term.matchValue,
          is(TaskState.TaskStage.FINISHED.toString()));
    }

    /**
     * Tests that spec is build successfully when multiple stage values are provided.
     */
    @Test
    public void testMultipleStageValues() {
      QueryTask.QuerySpecification spec =
          QueryTaskUtils.buildTaskStatusQuerySpec(
              TestStateClass.class, TaskState.TaskStage.FAILED, TaskState.TaskStage.CANCELLED);

      assertThat(spec.query, notNullValue());
      assertThat(spec.query.term, nullValue());
      assertThat(spec.query.booleanClauses.size(), is(2));

      QueryTask.Query stageQuery = spec.query.booleanClauses.get(1);
      assertThat(stageQuery, notNullValue());
      assertThat(stageQuery.term, nullValue());
      assertThat(stageQuery.booleanClauses.size(), is(2));

      assertThat(
          stageQuery.booleanClauses.get(0).term.propertyName,
          is(String.format(QueryTaskUtils.STAGE_FIELD_NAME_FORMAT, "state")));
      assertThat(stageQuery.booleanClauses.get(0).term.matchValue, is(TaskState.TaskStage.FAILED.toString()));
      assertThat(stageQuery.booleanClauses.get(0).occurance, is(QueryTask.Query.Occurance.SHOULD_OCCUR));

      assertThat(
          stageQuery.booleanClauses.get(1).term.propertyName,
          is(String.format(QueryTaskUtils.STAGE_FIELD_NAME_FORMAT, "state")));
      assertThat(stageQuery.booleanClauses.get(1).term.matchValue, is(TaskState.TaskStage.CANCELLED.toString()));
      assertThat(stageQuery.booleanClauses.get(1).occurance, is(QueryTask.Query.Occurance.SHOULD_OCCUR));
    }

    /**
     * Tests that spec is built successfully when only one stage value is provided.
     */
    @Test
    public void testSingleStageValueWithDerivedTaskStateClass() {
      QueryTask.QuerySpecification spec =
          QueryTaskUtils.buildTaskStatusQuerySpec(
              TestStateClass.class, TaskState.TaskStage.FINISHED);

      assertThat(spec.query, notNullValue());
      assertThat(spec.query.term, nullValue());
      assertThat(spec.query.booleanClauses.size(), is(2));
      assertThat(
          spec.query.booleanClauses.get(1).term.propertyName,
          is(String.format(QueryTaskUtils.STAGE_FIELD_NAME_FORMAT, "state")));
      assertThat(
          spec.query.booleanClauses.get(1).term.matchValue,
          is(TaskState.TaskStage.FINISHED.toString()));
    }

    /**
     * Tests that exception is raised when no stages param is provided.
     */
    @Test
    public void testNoTaskStageList() {
      try {
        QueryTaskUtils.buildTaskStatusQuerySpec(Class.class);
        fail("did not throw exception when stages param was not provided");
      } catch (IllegalArgumentException ex) {
        assertThat(ex.getMessage(), startsWith("stages.length must be >= 1"));
      }
    }

    /**
     * Tests that exception is raised when no stages param is empty.
     */
    @Test
    public void testEmptyTaskStageList() {
      try {
        QueryTaskUtils.buildTaskStatusQuerySpec(
            Class.class, (TaskState.TaskStage[]) null);
        fail("did not throw exception when stages param was not provided");
      } catch (IllegalArgumentException ex) {
        assertThat(ex.getMessage(), startsWith("stages.length must be >= 1"));
      }
    }

    /**
     * Tests that exception is raised when child task state does not have a field of type TaskState.
     */
    @Test
    public void testServiceDoesNotTrackProgress() {
      try {
        QueryTaskUtils.buildTaskStatusQuerySpec(
            Class.class, new TaskState.TaskStage[1]);
        fail("did not throw exception when child task state did not have a field of type TaskState");
      } catch (IllegalArgumentException ex) {
        assertThat(ex.getMessage(), startsWith("class java.lang.Class does not have a member of type"));
      }
    }
  }

  /**
   * Class used for query build tests.
   */
  private class TestStateClass {
    public TaskState state;
  }

  /**
   * Class used for query build tests.
   */
  private class TestDerivedStateClass {
    public DerivedTaskState state;

    public class DerivedTaskState extends com.vmware.xenon.common.TaskState {

    }
  }

  /**
   * Tests buildQuerySpec method.
   */
  public class BuildQuerySpecTest {
    @Test
    public void testWith2Clauses() {
      ImmutableMap.Builder<String, String> termsBuilder = new ImmutableMap.Builder<>();
      termsBuilder.put("key1", UUID.randomUUID().toString());
      termsBuilder.put("key2", UUID.randomUUID().toString());

      QueryTask.QuerySpecification spec = QueryTaskUtils.buildQuerySpec(Object.class, termsBuilder.build());

      assertThat(spec.query.booleanClauses.size(), is(3));
      assertThat(spec.query.booleanClauses.get(0).term.propertyName, is("documentKind"));
      assertThat(spec.query.booleanClauses.get(0).term.matchValue, is(equalTo(Utils.buildKind(Object.class))));
      assertThat(spec.query.booleanClauses.get(1).term.propertyName, is("key1"));
      assertThat(spec.query.booleanClauses.get(1).term.matchValue, is(termsBuilder.build().get("key1")));
      assertThat(spec.query.booleanClauses.get(2).term.propertyName, is("key2"));
      assertThat(spec.query.booleanClauses.get(2).term.matchValue, is(termsBuilder.build().get("key2")));
    }

    @Test
    public void testWith1Clause() {
      ImmutableMap.Builder<String, String> termsBuilder = new ImmutableMap.Builder<>();
      termsBuilder.put("key1", UUID.randomUUID().toString());

      QueryTask.QuerySpecification spec = QueryTaskUtils.buildQuerySpec(Object.class, termsBuilder.build());

      assertThat(spec.query.booleanClauses.size(), is(2));
      assertThat(spec.query.booleanClauses.get(0).term.propertyName, is("documentKind"));
      assertThat(spec.query.booleanClauses.get(0).term.matchValue, is(equalTo(Utils.buildKind(Object.class))));
      assertThat(spec.query.booleanClauses.get(1).term.propertyName, is("key1"));
      assertThat(spec.query.booleanClauses.get(1).term.matchValue, is(termsBuilder.build().get("key1")));
    }

    @Test
    public void testWith0Clauses() {
      ImmutableMap.Builder<String, String> termsBuilder = new ImmutableMap.Builder<>();

      QueryTask.QuerySpecification spec = QueryTaskUtils.buildQuerySpec(Object.class, termsBuilder.build());

      assertThat(spec.query.term.propertyName, is("documentKind"));
      assertThat(spec.query.term.matchValue, is(equalTo(Utils.buildKind(Object.class))));
    }

    @Test
    public void testWithNullClauses() {
      QueryTask.QuerySpecification spec = QueryTaskUtils.buildQuerySpec(Object.class, null);

      assertThat(spec.query.term.propertyName, is("documentKind"));
      assertThat(spec.query.term.matchValue, is(equalTo(Utils.buildKind(Object.class))));
    }

    @Test(expectedExceptions = NullPointerException.class)
    public void testWithNullDocumentKind() {
      ImmutableMap.Builder<String, String> termsBuilder = new ImmutableMap.Builder<>();
      termsBuilder.put("key1", UUID.randomUUID().toString());
      QueryTaskUtils.buildQuerySpec(null, termsBuilder.build());
    }
  }
}
