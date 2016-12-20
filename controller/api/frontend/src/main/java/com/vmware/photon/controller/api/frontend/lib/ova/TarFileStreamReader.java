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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Iterator;

/**
 * This class iterates trough the files that are part of a TAR archive. It assumes an OVA TAR because no directories are
 * supported.
 */
public class TarFileStreamReader implements Iterable<TarFileStreamReader.TarFile> {
  public static final int TAR_FILE_GRANULARITY = 512;
  private static final Logger logger = LoggerFactory.getLogger(TarFileStreamReader.class);
  private static final String TAR_SIGNATURE = "ustar";
  private static final DataField NAME_FIELD = new DataField(0, 100);
  private static final DataField OCTAL_BYTE_LENGTH_FIELD = new DataField(124, 12);
  private static final DataField SIGNATURE_FIELD = new DataField(257, 5);

  private final DataInputStream inputStream;
  private TarFile lastFile = null;
  private TarFile nextFile = null;

  /*
   * Constructor.
   * @param inputStream - Raw tar file stream.
   */
  public TarFileStreamReader(InputStream inputStream) {
    this.inputStream = new DataInputStream(inputStream);
  }

  /*
   * Decode a tar file header. The stream is assumed to be at the beginning of the header.
   */
  private TarFile decodeNextFile() throws IOException {
    byte[] buffer = new byte[TAR_FILE_GRANULARITY];

    // A header must be present for an OVA component file to be available..
    this.inputStream.readFully(buffer);

    // If last two empty sections of the TAR file were reached, exit.
    String signature = SIGNATURE_FIELD.getFirstFieldValue(buffer);
    if (!TAR_SIGNATURE.equals(signature)) {
      logger.error("Invalid file signature: '{}'", signature);
      logger.error("Input stream buffer content: [{}]", buffer);
      return null;
    }

    // Get embedded file name.
    TarFile fileInfo = new TarFile();
    fileInfo.name = NAME_FIELD.getFirstFieldValue(buffer);

    // Get embedded file length.
    String octalByteLength = OCTAL_BYTE_LENGTH_FIELD.getFirstFieldValue(buffer);
    long fileByteLength = Long.parseLong(octalByteLength.trim(), 8/* octal base */);

    // Get embedded file content.
    fileInfo.content = getFileContentStream(fileByteLength);

    return fileInfo;
  }

  /*
   * Create stream into the file contents.
   */
  private InputStream getFileContentStream(final long byteLength) throws IOException {
    return new InputStream() {
      long bytesOffset = 0;

      @Override
      public int read() throws IOException {
        if (bytesOffset >= byteLength) {
          return -1;
        }
        int byteRead = inputStream.read();
        if (byteRead >= 0) {
          bytesOffset += 1;
        }
        return byteRead;
      }

      @Override
      public int read(byte b[], int off, int len) throws IOException {
        if (bytesOffset >= byteLength) {
          return -1;
        }
        if (len > byteLength - bytesOffset) {
          len = (int) (byteLength - bytesOffset);
        }
        int bytesRead = inputStream.read(b, off, len);
        if (bytesRead >= 0) {
          bytesOffset += bytesRead;
        }
        return bytesRead;
      }

      @Override
      public void close() throws IOException {
        // Calculate total fragment size in the archive, including padding.
        long totalByteLength = ((byteLength + TAR_FILE_GRANULARITY - 1) / TAR_FILE_GRANULARITY) * TAR_FILE_GRANULARITY;

        // Skip until the end of the fragment. Raise error if unexpected end of archive is reached: the stream was
        // cut-short or the header was corrupted.
        while (bytesOffset < totalByteLength) {
          bytesOffset += inputStream.skip(totalByteLength - bytesOffset);

          if (bytesOffset < totalByteLength) {
            if (inputStream.read() < 0) {
              throw new IOException("Unexpected end of file.");
            }
            bytesOffset += 1;
          }
        }
      }
    };
  }

  /*
   * Create an iterator trough the File components of the TAR archive.
   */
  @Override
  public Iterator<TarFile> iterator() {
    return new Iterator<TarFile>() {

      /*
       * Complete current read and decode the next file header.
       */
      @Override
      public boolean hasNext() {
        try {
          if (lastFile != null) {
            lastFile.content.close();
            lastFile = null;
          }
          if (nextFile == null) {
            nextFile = decodeNextFile();
          }
        } catch (IOException e) {
          return false;
        }
        return nextFile != null;
      }

      /*
       * Retrieve next file in the TAR archive.
       */
      @Override
      public TarFile next() {
        TarFile next = null;
        if (hasNext()) {
          next = nextFile;
          lastFile = nextFile;
          nextFile = null;
        }
        return next;
      }
    };
  }

  /**
   * Represents a file as a stream.
   */
  public static class TarFile {
    public String name; // File name.
    public InputStream content; // File content as stream.
  }
}
