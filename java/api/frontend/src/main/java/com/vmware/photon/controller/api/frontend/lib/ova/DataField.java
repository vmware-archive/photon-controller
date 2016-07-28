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

package com.vmware.photon.controller.api.frontend.lib.ova;

import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.util.Arrays;

/**
 * Class representing coordinates of a string of interest in an array.
 */
public class DataField {
  private int offset;
  private int length;

  public DataField(int offset, int length) {
    this.offset = offset;
    this.length = length;
  }

  /**
   * Parse printable characters from input stream.
   *
   * @param dataField   The data field to specify buffer array.
   * @param inputStream The input stream of file.
   * @return Parsed string from input stream.
   * @throws IOException
   */
  public static String getString(DataField dataField, InputStream inputStream)
      throws IOException {
    byte[] bytes = new byte[dataField.getRequiredReadAhead()];
    int totalRead = 0;

    inputStream.mark(bytes.length);
    while (totalRead < bytes.length) {
      int read = inputStream.read(bytes, totalRead, bytes.length - totalRead);
      if (-1 == read) {
        break;
      }

      totalRead += read;
    }

    inputStream.reset();
    return dataField.getConcatenateFieldValue(bytes);
  }

  /**
   * Get first section of printable characters stopped on first non-printable character.
   *
   * @param buffer
   * @return
   * @throws UnsupportedEncodingException
   */
  public String getFirstFieldValue(byte[] buffer) throws UnsupportedEncodingException {
    // Allow only printable characters.
    int charsLen;
    for (charsLen = offset; charsLen < offset + length; charsLen += 1) {
      if (buffer[charsLen] < 32 || buffer[charsLen] >= 127) {
        break;
      }
    }
    return new String(Arrays.copyOfRange(buffer, offset, charsLen), "UTF-8");
  }

  /**
   * Concatenate all printable characters.
   *
   * @param buffer
   * @return
   * @throws UnsupportedEncodingException
   */
  public String getConcatenateFieldValue(byte[] buffer) throws UnsupportedEncodingException {
    // Allow only printable characters.
    int charsLen;
    for (charsLen = offset; charsLen < offset + length; charsLen += 1) {
      if (buffer[charsLen] < 32 || buffer[charsLen] >= 127) {
        continue;
      }
    }
    return new String(Arrays.copyOfRange(buffer, offset, charsLen), "UTF-8");
  }

  public int getRequiredReadAhead() {
    return offset + length;
  }
}
