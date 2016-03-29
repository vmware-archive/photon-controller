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

package com.vmware.photon.controller.deployer.deployengine;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.CreateContainerCmd;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.command.ListContainersCmd;
import com.github.dockerjava.api.model.Bind;
import com.github.dockerjava.api.model.Container;
import com.github.dockerjava.api.model.ExposedPort;
import com.github.dockerjava.api.model.Ports;
import com.github.dockerjava.api.model.Volume;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.testng.annotations.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasItemInArray;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Matchers.anyBoolean;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.fail;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * This class implements tests for the {@link DockerProvisioner} class.
 */
public class DockerProvisionerTest {

  @Test
  public void testNullDockerEndpoint() {
    try {
      new DockerProvisioner(null);
      fail("DockerProvisioner should fail with null dockerEndpoint parameter");
    } catch (IllegalArgumentException e) {
      assertThat(e.getMessage(), is("dockerEndpoint field cannot be null or blank"));
    }
  }

  @Test
  public void testBlankDockerEndpoint() {
    try {
      new DockerProvisioner("");
      fail("DockerProvisioner should fail with blank dockerEndpoint parameter");
    } catch (IllegalArgumentException e) {
      assertThat(e.getMessage(), is("dockerEndpoint field cannot be null or blank"));
    }
  }

  @Test
  public void testGetPortBindingsWithNull() {
    DockerProvisioner dockerProvisioner = new DockerProvisioner("0.0.0.0");
    assertThat(dockerProvisioner.getPortBindings(null), is(nullValue()));
  }

  @Test
  public void testGetPortBindingsWithEmptyMap() {
    DockerProvisioner dockerProvisioner = new DockerProvisioner("0.0.0.0");
    assertThat(dockerProvisioner.getPortBindings(new HashMap<Integer, Integer>()), is(nullValue()));
  }

  @Test
  public void testGetPortBindingsWithMap() {
    DockerProvisioner dockerProvisioner = new DockerProvisioner("0.0.0.0");
    HashMap<Integer, Integer> portMap = new HashMap<Integer, Integer>();
    portMap.put(5432, 5432);
    Ports portBindings = dockerProvisioner.getPortBindings(portMap);
    Map<ExposedPort, Ports.Binding[]> bindingMap = portBindings.getBindings();
    assertEquals(bindingMap.keySet().size(), 2);
    assertNotNull(bindingMap.get(ExposedPort.tcp(5432)));
    assertEquals(bindingMap.get(ExposedPort.tcp(5432))[0], Ports.Binding(5432));
    assertNotNull(bindingMap.get(ExposedPort.udp(5432)));
    assertEquals(bindingMap.get(ExposedPort.udp(5432))[0], Ports.Binding(5432));
  }

  @Test
  public void testGetVolumeBindingsWithNull() {
    DockerProvisioner dockerProvisioner = new DockerProvisioner("0.0.0.0");
    assertThat(dockerProvisioner.getVolumeBindings(null), is(nullValue()));
  }

  @Test
  public void testGetVolumeBindingsWithEmptyMap() {
    DockerProvisioner dockerProvisioner = new DockerProvisioner("0.0.0.0");
    assertThat(dockerProvisioner.getVolumeBindings(new HashMap<String, String>()), is(nullValue()));
  }

  @Test
  public void testGetVolumeBindingsWithMap() {
    DockerProvisioner dockerProvisioner = new DockerProvisioner("0.0.0.0");
    HashMap<String, String> volumeMap = new HashMap<String, String>();
    volumeMap.put("/var/esxcloud", "/var/esxcloud,/etc/esxcloud");
    volumeMap.put("/vagrant", "/vagrant");
    Bind[] volumeBindings = dockerProvisioner.getVolumeBindings(volumeMap);
    assertEquals(volumeBindings.length, 3);
    assertEquals(volumeBindings[0].getPath(), "/var/esxcloud");
    assertEquals(volumeBindings[0].getVolume(), new Volume("/var/esxcloud"));
    assertEquals(volumeBindings[1].getPath(), "/var/esxcloud");
    assertEquals(volumeBindings[1].getVolume(), new Volume("/etc/esxcloud"));
    assertEquals(volumeBindings[2].getPath(), "/vagrant");
    assertEquals(volumeBindings[2].getVolume(), new Volume("/vagrant"));
  }

  @Test
  public void testGetEnvironmentVariablesListWithNull() {
    DockerProvisioner dockerProvisioner = new DockerProvisioner("0.0.0.0");
    assertThat(dockerProvisioner.getEnvironmentVariablesList(null), is(nullValue()));
  }

  @Test
  public void testGetEnvironmentVariablesListWithEmptyMap() {
    DockerProvisioner dockerProvisioner = new DockerProvisioner("0.0.0.0");
    assertThat(dockerProvisioner.getEnvironmentVariablesList(new HashMap<String, String>()), is(nullValue()));
  }

  @Test
  public void testGetEnvironmentVariablesListWithMap() {
    DockerProvisioner dockerProvisioner = new DockerProvisioner("0.0.0.0");
    HashMap<String, String> envMap = new HashMap<String, String>();
    envMap.put("VERSION", "1.0");
    envMap.put("PATH", "$PATH:/usr/bin");
    String[] envList = dockerProvisioner.getEnvironmentVariablesList(envMap);
    assertEquals(envList.length, 2);
    assertThat(envList, hasItemInArray("VERSION=1.0"));
    assertThat(envList, hasItemInArray("PATH=$PATH:/usr/bin"));
  }

  @Test
  public void testGetImageLoadEndpoint() {
    DockerProvisioner dockerProvisioner = new DockerProvisioner("0.0.0.0");
    assertEquals(dockerProvisioner.getImageLoadEndpoint(), "http://0.0.0.0:2375/images/load");
  }

  @Test
  public void testStartContainerWithNull() {
    DockerProvisioner dockerProvisioner = new DockerProvisioner("0.0.0.0");
    try {
      dockerProvisioner.startContainer(null);
      fail("startContainer should fail with null container id parameter");
    } catch (IllegalArgumentException e) {
      assertThat(e.getMessage(), is("containerId field cannot be null or blank"));
    }
  }

  @Test
  public void testStartContainerWithBlank() {
    DockerProvisioner dockerProvisioner = new DockerProvisioner("0.0.0.0");
    try {
      dockerProvisioner.startContainer("");
      fail("startContainer should fail with blank container id parameter");
    } catch (IllegalArgumentException e) {
      assertThat(e.getMessage(), is("containerId field cannot be null or blank"));
    }
  }

  @Test
  public void testCreateImageFromTarWithNull() {
    DockerProvisioner dockerProvisioner = new DockerProvisioner("0.0.0.0");
    try {
      dockerProvisioner.createImageFromTar(null, "imageName");
      fail("createImageFromTar should fail with null filePath parameter");
    } catch (IllegalArgumentException e) {
      assertThat(e.getMessage(), is("filePath field cannot be null or blank"));
    }

    try {
      dockerProvisioner.createImageFromTar("filePath", null);
      fail("createImageFromTar should fail with null imageName parameter");
    } catch (IllegalArgumentException e) {
      assertThat(e.getMessage(), is("imageName field cannot be null or blank"));
    }
  }

  @Test
  public void testCreateImageFromTarWithBlank() {
    DockerProvisioner dockerProvisioner = new DockerProvisioner("0.0.0.0");
    try {
      dockerProvisioner.createImageFromTar("", "imageName");
      fail("createImageFromTar should fail with blank filePath parameter");
    } catch (IllegalArgumentException e) {
      assertThat(e.getMessage(), is("filePath field cannot be null or blank"));
    }

    try {
      dockerProvisioner.createImageFromTar("filePath", "");
      fail("createImageFromTar should fail with blank imageName parameter");
    } catch (IllegalArgumentException e) {
      assertThat(e.getMessage(), is("imageName field cannot be null or blank"));
    }
  }

  @Test
  public void testRetagImageWithNull() {
    DockerProvisioner dockerProvisioner = new DockerProvisioner("0.0.0.0");
    try {
      dockerProvisioner.retagImage(null, "newName", "newTag");
      fail("retagImage should fail with null oldName parameter");
    } catch (IllegalArgumentException e) {
      assertThat(e.getMessage(), is("oldName field cannot be null or blank"));
    }

    try {
      dockerProvisioner.retagImage("oldName", null, "newTag");
      fail("retagImage should fail with null newName parameter");
    } catch (IllegalArgumentException e) {
      assertThat(e.getMessage(), is("newName field cannot be null or blank"));
    }
  }

  @Test
  public void testRetagImageWithBlank() {
    DockerProvisioner dockerProvisioner = new DockerProvisioner("0.0.0.0");
    try {
      dockerProvisioner.retagImage("", "newName", "newTag");
      fail("retagImage should fail with blank oldName parameter");
    } catch (IllegalArgumentException e) {
      assertThat(e.getMessage(), is("oldName field cannot be null or blank"));
    }

    try {
      dockerProvisioner.retagImage("oldName", "", "newTag");
      fail("retagImage should fail with blank newName parameter");
    } catch (IllegalArgumentException e) {
      assertThat(e.getMessage(), is("newName field cannot be null or blank"));
    }
  }

  @Test
  public void testRemoveImageWithNull() {
    DockerProvisioner dockerProvisioner = new DockerProvisioner("0.0.0.0");
    try {
      dockerProvisioner.removeImage(null);
      fail("removeImage should fail with null imageName parameter");
    } catch (IllegalArgumentException e) {
      assertThat(e.getMessage(), is("imageName field cannot be null or blank"));
    }
  }

  @Test
  public void testRemoveImageWithBlank() {
    DockerProvisioner dockerProvisioner = new DockerProvisioner("0.0.0.0");
    try {
      dockerProvisioner.removeImage("");
      fail("removeImage should fail with blank oldName parameter");
    } catch (IllegalArgumentException e) {
      assertThat(e.getMessage(), is("imageName field cannot be null or blank"));
    }
  }

  @Test
  public void testLaunchContainerWithNull() {
    DockerProvisioner dockerProvisioner = new DockerProvisioner("0.0.0.0");
    try {
      dockerProvisioner.launchContainer(null, "image", 0, 0L, null, null, null, true, null, true, true);
      fail("launchContainer should fail with null container name parameter");
    } catch (IllegalArgumentException e) {
      assertThat(e.getMessage(), is("containerName field cannot be null or blank"));
    }

    try {
      dockerProvisioner.launchContainer("name", null, 0, 0L, null, null, null, true, null, true, true);
      fail("launchContainer should fail with null container image parameter");
    } catch (IllegalArgumentException e) {
      assertThat(e.getMessage(), is("containerImage field cannot be null or blank"));
    }
  }

  @Test
  public void testLaunchContainerWithBlank() {
    DockerProvisioner dockerProvisioner = new DockerProvisioner("0.0.0.0");
    try {
      dockerProvisioner.launchContainer("", "image", 0, 0L, null, null, null, true, null, true, true);
      fail("launchContainer should fail with blank container name parameter");
    } catch (IllegalArgumentException e) {
      assertThat(e.getMessage(), is("containerName field cannot be null or blank"));
    }

    try {
      dockerProvisioner.launchContainer("name", "", 0, 0L, null, null, null, true, null, true, true);
      fail("launchContainer should fail with blank container image parameter");
    } catch (IllegalArgumentException e) {
      assertThat(e.getMessage(), is("containerImage field cannot be null or blank"));
    }
  }

  @Test
  public void testCreateContainerWithNull() {
    DockerProvisioner dockerProvisioner = new DockerProvisioner("0.0.0.0");
    try {
      dockerProvisioner.createContainer(null, "image", 0, 0L, null, null, null, true, null, true, true);
      fail("createContainer should fail with null container name parameter");
    } catch (IllegalArgumentException e) {
      assertThat(e.getMessage(), is("containerName field cannot be null or blank"));
    }

    try {
      dockerProvisioner.createContainer("name", null, 0, 0L, null, null, null, true, null, true, true);
      fail("createContainer should fail with null container image parameter");
    } catch (IllegalArgumentException e) {
      assertThat(e.getMessage(), is("containerImage field cannot be null or blank"));
    }
  }

  @Test
  public void testCreateContainerWithBlank() {
    DockerProvisioner dockerProvisioner = new DockerProvisioner("0.0.0.0");
    try {
      dockerProvisioner.createContainer("", "image", 0, 0L, null, null, null, true, null, true, true);
      fail("createContainer should fail with blank container name parameter");
    } catch (IllegalArgumentException e) {
      assertThat(e.getMessage(), is("containerName field cannot be null or blank"));
    }

    try {
      dockerProvisioner.createContainer("name", "", 0, 0L, null, null, null, true, null, true, true);
      fail("createContainer should fail with blank container image parameter");
    } catch (IllegalArgumentException e) {
      assertThat(e.getMessage(), is("containerImage field cannot be null or blank"));
    }
  }

  @Test
  public void testDeleteContainerWithNull() {
    DockerProvisioner dockerProvisioner = new DockerProvisioner("0.0.0.0");
    try {
      dockerProvisioner.deleteContainer(null, true, true);
      fail("deleteContainer should fail with null container id parameter");
    } catch (IllegalArgumentException e) {
      assertThat(e.getMessage(), is("containerId field cannot be null or blank"));
    }
  }

  @Test
  public void testDeleteContainerWithBlank() {
    DockerProvisioner dockerProvisioner = new DockerProvisioner("0.0.0.0");
    try {
      dockerProvisioner.deleteContainer("", true, true);
      fail("deleteContainer should fail with blank container id parameter");
    } catch (IllegalArgumentException e) {
      assertThat(e.getMessage(), is("containerId field cannot be null or blank"));
    }
  }

  @Test
  public void testDeleteContainer() {
    DockerProvisioner dockerProvisioner = mock(DockerProvisioner.class);
    when(dockerProvisioner.deleteContainer(anyString(), anyBoolean(), anyBoolean())).
      thenAnswer(new Answer<String>() {
        @Override
        public String answer(InvocationOnMock invocation) throws Throwable {
          Object[] args = invocation.getArguments();
          return (String) args[0];
        }
      });
    assertEquals(dockerProvisioner.deleteContainer("id", true, true), "id");
  }

  @Test
  public void testLaunchContainer() throws IOException {
    DockerProvisioner dockerProvisioner = mock(DockerProvisioner.class);
    DockerClient dockerClient = mock(DockerClient.class);
    when(dockerProvisioner.getDockerClient()).thenReturn(dockerClient);

    String containerImage = "devbox/postgres";

    // Create container
    CreateContainerCmd createContainerCmd = mock(CreateContainerCmd.class);
    when(dockerClient.createContainerCmd(containerImage)).thenReturn(createContainerCmd);
    CreateContainerResponse containerResponse = mock(CreateContainerResponse.class);
    containerResponse.setId("id");
    when(createContainerCmd.exec()).thenReturn(containerResponse);

    // Start container
    doNothing().when(dockerProvisioner).startContainer("id");

    // Test with name, linked container and privileged flag
    when(dockerProvisioner.launchContainer("postgres", "devbox/postgres", 0, 0L, null, null, "data", true, null,
        true, true)).thenCallRealMethod();
    assertEquals(dockerProvisioner.launchContainer("postgres", "devbox/postgres", 0, 0L, null, null, "data", true, null,
        true, true), null);

    // Test with volume and port bindings
    Map<String, String> vb = new HashMap<String, String>();
    vb.put("/vagrant/log/postgresql", "/var/log/postgresql");
    Map<Integer, Integer> pb = new HashMap<Integer, Integer>();
    pb.put(5432, 5432);
    pb.put(2181, 2181);
    when(dockerProvisioner.launchContainer("postgres", "devbox/postgres", 0, 0L, vb, pb, "data", true, null,
        true, true)).thenCallRealMethod();
    assertEquals(dockerProvisioner.launchContainer("postgres", "devbox/postgres", 0, 0L, vb, pb, "data", true, null,
            true, true), null);

    // Test with command
    when(dockerProvisioner.launchContainer("postgres", "devbox/postgres", 0, 0L, vb, pb, "data", true, null, true,
        true, "postgres")).thenCallRealMethod();
    assertEquals(dockerProvisioner.launchContainer("postgres", "devbox/postgres", 0, 0L, vb, pb, "data", true, null,
        true, true, "postgres"), null);
  }

  @Test
  public void testStopContainerMatching() {
    DockerProvisioner dockerProvisioner = spy(new DockerProvisioner("1.1.1.1"));
    DockerClient dockerClient = mock(DockerClient.class);
    when(dockerProvisioner.getDockerClient()).thenReturn(dockerClient);

    ListContainersCmd listCommand = mock(ListContainersCmd.class);
    when(dockerClient.listContainersCmd()).thenReturn(listCommand);
    Container matchingContainer = mock(Container.class);
    when(matchingContainer.getId()).thenReturn("matchingContainerId");
    when(matchingContainer.getNames()).thenReturn(new String[] {"matChing"});
    Container missMatchContainer = mock(Container.class);
    when(missMatchContainer.getNames()).thenReturn(new String[] {"missMatch"});
    when(listCommand.exec()).thenReturn(Arrays.asList(matchingContainer, missMatchContainer));

    dockerProvisioner.stopContainerMatching("matching");

    verify(dockerClient).stopContainerCmd(matchingContainer.getId());
  }
}
