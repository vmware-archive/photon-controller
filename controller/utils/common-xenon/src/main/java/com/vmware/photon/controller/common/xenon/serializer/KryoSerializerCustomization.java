/*
 * Copyright 2016 VMware, Inc. All Rights Reserved.
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

package com.vmware.photon.controller.common.xenon.serializer;

import com.vmware.xenon.common.serialization.KryoSerializers;

import com.esotericsoftware.kryo.Kryo;

import java.util.BitSet;

/**
 * This class defines customization for Kryo serialization.
 */
public class KryoSerializerCustomization extends ThreadLocal<Kryo> {

  @Override
  protected Kryo initialValue() {
    Kryo kryo = KryoSerializers.create(true);
    kryo.addDefaultSerializer(BitSet.class, new BitSetSerializer());
    return kryo;
  }
}
