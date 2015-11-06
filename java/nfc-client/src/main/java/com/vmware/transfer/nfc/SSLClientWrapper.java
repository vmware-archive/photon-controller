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

import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLEngineResult;
import javax.net.ssl.SSLSession;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;

/**
 * SSLClientWrapper.
 */
public class SSLClientWrapper {
  private SSLEngine engine;

  private ByteBuffer clearIn;
  private ByteBuffer clearOut;
  private ByteBuffer cipherIn;
  private ByteBuffer cipherOut;

  private ReadableByteChannel rawInputChannel;
  private WritableByteChannel rawOutputChannel;

  private InputStream wrappedInputStream;
  private OutputStream wrappedOutputStream;

  public SSLClientWrapper(SSLEngine engine, Socket socket) throws IOException {
    this.engine = engine;
    rawInputChannel = Channels.newChannel(socket.getInputStream());
    rawOutputChannel = Channels.newChannel(socket.getOutputStream());

    SSLSession session = engine.getSession();
    clearIn = ByteBuffer.allocate(session.getApplicationBufferSize());
    clearOut = ByteBuffer.allocate(session.getApplicationBufferSize());
    cipherIn = ByteBuffer.allocate(session.getPacketBufferSize());
    cipherOut = ByteBuffer.allocate(session.getPacketBufferSize());

    clearIn.flip();
    cipherIn.flip();

    wrappedInputStream = new WrapperInputStream();
    wrappedOutputStream = new WrapperOutputStream();
  }

  public void processHandshake() throws IOException {
    while (true) {
      SSLEngineResult result;
      switch (engine.getHandshakeStatus()) {
        case NOT_HANDSHAKING:
        case FINISHED:
          return;
        case NEED_TASK:
          Runnable task = engine.getDelegatedTask();
          task.run();
          break;
        case NEED_WRAP:
          clearOut.flip();
          result = engine.wrap(clearOut, cipherOut);
          clearOut.compact();
          cipherOut.flip();
          rawOutputChannel.write(cipherOut);
          cipherOut.compact();
          break;
        case NEED_UNWRAP:
          clearIn.compact();
          result = engine.unwrap(cipherIn, clearIn);
          clearIn.flip();
          while (result.getStatus() == SSLEngineResult.Status.BUFFER_UNDERFLOW) {
            cipherIn.compact();
            rawInputChannel.read(cipherIn);
            cipherIn.flip();
            clearIn.compact();
            result = engine.unwrap(cipherIn, clearIn);
            clearIn.flip();
          }
          if (result.getStatus() == SSLEngineResult.Status.BUFFER_OVERFLOW) {
            // Caller must read something to make room in clearIn
            return;
          }
          break;
      }
    }
  }

  public InputStream getInputStream() {
    return wrappedInputStream;
  }

  public OutputStream getOutputStream() {
    return wrappedOutputStream;
  }

  private class WrapperInputStream extends InputStream {
    @Override
    public int read(byte[] b) throws IOException {
      return read(b, 0, b.length);
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
      processHandshake();

      if (clearIn.remaining() < len) {
        processIncoming(); // eagerly fetch data
      }
      int length = len;
      if (length > clearIn.remaining()) {
        length = clearIn.remaining();
      }
      clearIn.get(b, off, length);
      return length;
    }

    @Override
    public int read() throws IOException {
      processHandshake();

      while (!clearIn.hasRemaining()) {
        processIncoming();
      }

      return clearIn.get();
    }

    private void processIncoming() throws IOException {
      cipherIn.compact();
      rawInputChannel.read(cipherIn);
      cipherIn.flip();
      clearIn.compact();
      SSLEngineResult result = engine.unwrap(cipherIn, clearIn);
      while (result.getStatus() == SSLEngineResult.Status.BUFFER_UNDERFLOW) {
        cipherIn.compact();
        rawInputChannel.read(cipherIn);
        cipherIn.flip();
        result = engine.unwrap(cipherIn, clearIn);
      }
      clearIn.flip();
    }
  }

  private class WrapperOutputStream extends OutputStream {
    @Override
    public void write(byte[] b) throws IOException {
      write(b, 0, b.length);
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {
      processHandshake();

      int offset = off;
      int length = len;
      while (length > 0) {
        int room = clearOut.remaining();
        if (room > length) {
          room = length;
        }
        clearOut.put(b, offset, room);
        length -= room;
        offset += room;
        flush();
      }
    }

    @Override
    public void write(int b) throws IOException {
      processHandshake();

      while (clearOut.remaining() == 0) {
        flush();
      }
      clearOut.put((byte) b);
      flush();
    }

    @Override
    public void flush() throws IOException {
      clearOut.flip();
      SSLEngineResult result = engine.wrap(clearOut, cipherOut);
      cipherOut.flip();
      while (result.getStatus() == SSLEngineResult.Status.BUFFER_OVERFLOW) {
        rawOutputChannel.write(cipherOut);
        cipherOut.compact();
        result = engine.wrap(clearOut, cipherOut);
        cipherOut.flip();
      }
      if (cipherOut.hasRemaining()) {
        rawOutputChannel.write(cipherOut);
      }
      cipherOut.compact();
      clearOut.compact();
    }
  }
}
