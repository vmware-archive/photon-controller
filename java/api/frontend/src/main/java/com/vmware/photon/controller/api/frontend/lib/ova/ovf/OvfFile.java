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

package com.vmware.photon.controller.api.frontend.lib.ova.ovf;

import com.vmware.photon.controller.api.frontend.exceptions.internal.InvalidOvfException;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Class representing the OVF configuration file.
 */
public class OvfFile {
  private final Document document;
  private final String vmPath;

  /**
   * Constructor.
   *
   * @param stream
   */
  public OvfFile(InputStream stream) throws InvalidOvfException {
    try {
      DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
      DocumentBuilder db = dbf.newDocumentBuilder();
      document = db.parse(stream);
      vmPath = getVmPath();
    } catch (IOException | SAXException | ParserConfigurationException e) {
      throw new InvalidOvfException("Failed to parse OVF file.", e);
    }
  }

  /**
   * Retrieve OVF virtual disks.
   *
   * @return
   */
  public List<OvfMetadata.VirtualDisk> getVirtualDisks() throws InvalidOvfException {
    ArrayList<OvfMetadata.VirtualDisk> list = new ArrayList<>();
    try {
      for (Element element : getElementsForPath("/Envelope/DiskSection/Disk")) {
        list.add(new OvfMetadata.VirtualDisk(this, element));
      }
    } catch (XPathExpressionException e) {
      throw new InvalidOvfException("Missing disk section.");
    }
    return list;
  }

  /**
   * Retrieve OVF virtual networks.
   *
   * @return
   */
  public List<OvfMetadata.VirtualNetwork> getVirtualNetworks() throws InvalidOvfException {
    ArrayList<OvfMetadata.VirtualNetwork> list = new ArrayList<>();
    try {
      for (Element element : getElementsForPath("/Envelope/NetworkSection/Network")) {
        list.add(new OvfMetadata.VirtualNetwork(this, element));
      }
    } catch (XPathExpressionException e) {
      throw new InvalidOvfException("Missing network section.");
    }
    return list;
  }

  /**
   * Retrieve OVF file references.
   *
   * @return
   */
  public List<OvfMetadata.FileReference> getFileReferences() throws InvalidOvfException {
    ArrayList<OvfMetadata.FileReference> list = new ArrayList<>();
    try {
      for (Element element : getElementsForPath("/Envelope/References/File")) {
        list.add(new OvfMetadata.FileReference(this, element));
      }
    } catch (XPathExpressionException e) {
      throw new InvalidOvfException("Missing file references.", e);
    }
    return list;
  }

  /**
   * Retrieve OVF VM devices specification.
   *
   * @return
   */
  public List<Device> getDevices() throws InvalidOvfException {
    ArrayList<Device> list = new ArrayList<>();
    try {
      for (Element element : getElementsForPath(vmPath + "/VirtualHardwareSection/Item")) {
        list.add(new Device(this, element));
      }
    } catch (XPathExpressionException e) {
      throw new InvalidOvfException("Error reading VM devices.", e);
    }
    return list;
  }

  /**
   * Retrieve OVF VMware custom configuration settings.
   *
   * @return
   */
  public Map<String, String> getConfig() throws InvalidOvfException {
    HashMap<String, String> map = new HashMap<>();
    try {
      for (Element element : getElementsForPath(vmPath + "/VirtualHardwareSection/Config")) {
        map.put(element.getAttribute("vmw:key"), element.getAttribute("vmw:value"));
      }
    } catch (XPathExpressionException e) {
      throw new InvalidOvfException("Error reading configurations.", e);
    }
    return map;
  }

  /**
   * Retrieve OVF VMware custom extra-configuration settings.
   *
   * @return
   */
  public Map<String, String> getExtraConfig() throws InvalidOvfException {
    HashMap<String, String> map = new HashMap<>();
    try {
      for (Element element : getElementsForPath(vmPath + "/VirtualHardwareSection/ExtraConfig")) {
        map.put(element.getAttribute("vmw:key"), element.getAttribute("vmw:value"));
      }
    } catch (XPathExpressionException e) {
      throw new InvalidOvfException("Missing devices description.", e);
    }
    return map;
  }

  /**
   * Get the path to the VirtualSystem element. VirtualSystem describes a VM. Only one VM per OVF is supported.
   *
   * @return
   */
  private String getVmPath() {
    Exception innerException = null;
    try {
      String vmPath = "/Envelope/VirtualSystem";
      if (getElementsForPath(vmPath).size() == 1) {
        return vmPath;
      }
      vmPath = "/Envelope/VirtualSystemCollection/VirtualSystem";
      if (getElementsForPath(vmPath).size() == 1) {
        return vmPath;
      }
      if (getElementsForPath(vmPath).size() > 1) {
        throw new RuntimeException("OVF with more than one VM not supported.");
      }
    } catch (XPathExpressionException e) {
      innerException = e;
    }
    throw new RuntimeException("Error finding VM information.", innerException);
  }

  private List<Element> getElementsForPath(String path) throws XPathExpressionException {
    XPath xPath = XPathFactory.newInstance().newXPath();
    NodeList nodes = (NodeList) xPath.evaluate(path, document.getDocumentElement(), XPathConstants.NODESET);

    ArrayList<Element> elements = new ArrayList<>();
    for (int i = 0; i < nodes.getLength(); ++i) {
      elements.add((Element) nodes.item(i));
    }
    return elements;
  }

  /**
   * Helper class representing the OVF xml element.
   */
  public static class OvfElement {
    OvfFile ovfFile;
    Element element;

    OvfElement(OvfFile ovfFile, Element element) {
      this.ovfFile = ovfFile;
      this.element = element;
    }
  }
}
