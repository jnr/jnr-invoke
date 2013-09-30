/*
 * Copyright (C) 2013 Wayne Meissner
 *
 * This file is part of the JNR project.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package jnr.invoke;

import java.lang.invoke.MethodHandle;

public final class ParameterType extends SignatureType {
    private final DataDirection dataDirection;
    private final MethodHandle lookupObjectStrategy;

    private ParameterType(NativeType nativeType, Class javaType, DataDirection dataDirection) {
        this(nativeType, javaType, dataDirection, nativeType.jffiType());
    }

    private ParameterType(NativeType nativeType, Class javaType, DataDirection dataDirection, com.kenai.jffi.Type jffiType) {
        this(nativeType, javaType, dataDirection, jffiType, null);
    }

    private ParameterType(NativeType nativeType, Class javaType, DataDirection dataDirection, com.kenai.jffi.Type jffiType,
                          MethodHandle lookupObjectStrategy) {
        super(nativeType, javaType, jffiType);
        this.dataDirection = dataDirection;
        this.lookupObjectStrategy = lookupObjectStrategy;
    }


    public static ParameterType primitive(NativeType nativeType, Class javaType) {
        return new ParameterType(nativeType, javaType, DataDirection.INOUT);
    }

    static ParameterType object(Class javaType, DataDirection dataDirection, MethodHandle lookupObjectStrategy) {
        return new ParameterType(NativeType.POINTER, javaType, dataDirection, NativeType.POINTER.jffiType(), lookupObjectStrategy);
    }

    DataDirection getDataDirection() {
        return dataDirection;
    }

    public MethodHandle getObjectStrategyHandle() {
        return lookupObjectStrategy;
    }

    ParameterType asPrimitiveType() {
        return this;
    }
}
