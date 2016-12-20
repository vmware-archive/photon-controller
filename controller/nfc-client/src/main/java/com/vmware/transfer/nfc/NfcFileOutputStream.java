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

import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * NfcFileOutputStream implements a stream used to write a file to a NFC server.
 */
public class NfcFileOutputStream extends OutputStream {
  private static final byte[] EOF_MARKER = new byte[0];

  private final NfcClient nfcClient;
  private final ByteBuffer nfcMessage;
  private final ByteBuffer fileDataHdr;

  // when autoClose set to true, after finish streaming the file, close NfcClient.
  private final boolean autoClose;

  public NfcFileOutputStream(NfcClient nfcClient) {
    this(nfcClient, false);
  }

  public NfcFileOutputStream(NfcClient nfcClient, boolean autoClose) {
    this.nfcClient = nfcClient;
    nfcMessage = nfcClient.newNfcMsg(NfcClient.NFC_FILE_DATA);
    fileDataHdr = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN);
    this.autoClose = autoClose;
  }

  @Override
  public void write(int b) throws IOException {
    byte[] data = {(byte) b};
    write(data);
  }

  @Override
  public void write(byte[] b) throws IOException {
    write(b, 0, b.length);
  }

  /**
   * Write data. FILE_DATA message is sent immediately, so caller should buffer as necessary.
   */
  public void write(byte[] data, int offset, int length) throws IOException {
    assert data.length >= offset + length;
    while (length > 0) {
      int toWrite = Math.min(length, NfcClient.MAX_XFER_SIZE);
      writeInt(data, offset, toWrite);
      offset += toWrite;
      length -= toWrite;
    }
  }

  private void writeInt(byte[] data, int offset, int length) throws IOException {
    assert data.length >= offset + length;
    assert length <= NfcClient.MAX_XFER_SIZE;
    fileDataHdr.clear();
    fileDataHdr.putInt(NfcClient.FILE_DATA_HDR_MAGIC);
    fileDataHdr.putInt(length);
    fileDataHdr.flip();
    ByteBuffer dataBuffer = ByteBuffer.wrap(data, offset, length);
    nfcClient.sendNfcMsg(nfcMessage);
    nfcClient.writeFully(fileDataHdr);
    nfcClient.writeFully(dataBuffer);
  }

  @Override
  public void close() throws IOException {
    writeInt(EOF_MARKER, 0, 0);
    // Receive completion message from server
    ByteBuffer reply = this.nfcClient.readNfcResponse();
    this.nfcClient.validateReplyCode(reply, NfcClient.NFC_PUTFILE_DONE);
    if (autoClose) {
      this.nfcClient.close();
    }
  }
}
