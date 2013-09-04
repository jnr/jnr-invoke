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

public class ResultType extends SignatureType {
    private final MethodHandle resultConverter;

    public static ResultType primitive(NativeType nativeType, Class javaType) {
        return new ResultType(nativeType, javaType, Util.jffiType(nativeType), null);
    }

    public static ResultType primitive(NativeType nativeType, Class javaType, MethodHandle resultConverter) {
        return new ResultType(nativeType, javaType, Util.jffiType(nativeType), resultConverter);
    }


    ResultType(NativeType nativeType, Class javaType, com.kenai.jffi.Type jffiType, MethodHandle resultConverter) {
        super(nativeType, javaType, jffiType);
        this.resultConverter = resultConverter;
    }

    Class nativeJavaType() {
        return resultConverter != null ? resultConverter.type().parameterType(0) : getDeclaredType();
    }

    MethodHandle getFromNativeConverter() {
        return resultConverter;
    }

    ResultType asPrimitiveType() {
        return !getDeclaredType().isPrimitive() && Number.class.isAssignableFrom(getDeclaredType())
                ? new ResultType(getNativeType(), AsmUtil.unboxedType(getDeclaredType()), jffiType(), resultConverter)
                : this;
    }
}
