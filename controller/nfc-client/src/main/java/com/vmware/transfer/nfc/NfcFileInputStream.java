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

package com.vmware.transfer.nfc;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * NfcFileInputStream implements a stream used to read a file from a NFC server.
 */
public class NfcFileInputStream extends InputStream {
  private static final Logger logger = LoggerFactory.getLogger(NfcClient.class);

  private final NfcClient nfcClient;
  // when autoClose set to true, after finish streaming the file, close NfcClient.
  private final boolean autoClose;
  private ByteBuffer msgBuffer;
  private ByteBuffer hdrBuffer;
  private ByteBuffer frameBuffer;
  private long fileSize;

  public NfcFileInputStream(NfcClient nfcClient) {
    this(nfcClient, false);
  }

  public NfcFileInputStream(NfcClient nfcClient, boolean autoClose) {
    this.nfcClient = nfcClient;
    this.hdrBuffer = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN);
    this.frameBuffer = ByteBuffer.allocate(NfcClient.MAX_XFER_SIZE);
    this.autoClose = autoClose;
  }

  public void init() throws IOException {
    msgBuffer = nfcClient.readNfcResponse();
    nfcClient.validateReplyCode(msgBuffer, NfcClient.NFC_FILE_PUT);
    int type = msgBuffer.getInt();
        /*int conversionFlags =*/
    msgBuffer.getInt();
    int pathLen = msgBuffer.getInt();
    fileSize = msgBuffer.getLong();
        /*long spaceRequired =*/
    msgBuffer.getLong();

    if (type != NfcClient.NFC_RAW) {
      throw new IOException("Invalid file type in NFC_FILE_PUT response");
    }

        /*String path =*/
    nfcClient.readCString(pathLen);
    readIncoming();
  }

  public long getFileSize() {
    return fileSize;
  }

  @Override
  public int read() throws IOException {
    byte[] data = new byte[1];
    int bytesRead = read(data);
    if (bytesRead <= 0) {
      return -1;
    }
    return data[0] & 0xff;
  }

  @Override
  public int read(byte[] data) throws IOException {
    return read(data, 0, data.length);
  }

  @Override
  public int read(byte[] b, int off, int len) throws IOException {
    if (frameBuffer != null && !frameBuffer.hasRemaining()) {
      readIncoming();
    }
    if (frameBuffer == null) {
      return -1; // EOF
    }
    assert frameBuffer.hasRemaining();
    int bytesToRead = Math.min(len, frameBuffer.remaining());
    frameBuffer.get(b, off, bytesToRead);
    return bytesToRead;
  }

  @Override
  public void close() throws IOException {
    if (autoClose) {
      this.nfcClient.close();
    }
  }

  private void readIncoming() throws IOException {
    nfcClient.readNfcResponse(msgBuffer);
    nfcClient.validateReplyCode(msgBuffer, NfcClient.NFC_FILE_DATA);
    hdrBuffer.clear();
    nfcClient.readFully(hdrBuffer);
    hdrBuffer.flip();
    int fileDataHeader = hdrBuffer.getInt();
    if (NfcClient.FILE_DATA_HDR_MAGIC != fileDataHeader) {
      logger.error("Invalid NFC message: Expected FILE_DATA, got {}", fileDataHeader);
      nfcClient.abort();
      throw new IOException("NFC protocol violation");
    }
    int frameBytes = hdrBuffer.getInt();
    if (frameBytes == 0) {
      // EOF
      frameBuffer = null;
      return;
    }
    frameBuffer.clear();
    frameBuffer.limit(frameBytes);
    nfcClient.readFully(frameBuffer);
    frameBuffer.flip();
  }
}
