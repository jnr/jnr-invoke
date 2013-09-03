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

public final class ParameterType extends SignatureType {
    private final DataDirection dataDirection;

    private ParameterType(NativeType nativeType, Class javaType, DataDirection dataDirection, com.kenai.jffi.Type jffiType) {
        super(nativeType, javaType, jffiType);
        this.dataDirection = dataDirection;
    }

    public static ParameterType primitive(NativeType nativeType, Class javaType) {
        return new ParameterType(nativeType, javaType, DataDirection.INOUT, Util.jffiType(nativeType));
    }

    static ParameterType object(NativeType nativeType, Class javaType, DataDirection dataDirection,
                                ObjectParameterStrategyLookup stategyLookup) {
        return new ParameterType(nativeType, javaType, dataDirection, Util.jffiType(nativeType));
    }

    public static interface ObjectParameterStrategyLookup {
        public ObjectParameterStrategy lookupStrategy(Object parameter);
    }

    DataDirection getDataDirection() {
        return dataDirection;
    }

    ToNativeConverter getToNativeConverter() {
        return null;
    }

    ToNativeContext getToNativeContext() {
        return null;
    }

    Class effectiveJavaType() {
        return javaType();
    }

    ParameterType asPrimitiveType() {
        return !getDeclaredType().isPrimitive() && Number.class.isAssignableFrom(getDeclaredType())
                ? new ParameterType(getNativeType(), AsmUtil.unboxedType(getDeclaredType()), getDataDirection(), jffiType())
                : this;
    }
}
