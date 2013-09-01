/*
 * Copyright (C) 2011-2013 Wayne Meissner
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

import com.kenai.jffi.CallContext;
import com.kenai.jffi.Platform;
import com.kenai.jffi.Type;

import java.nio.*;

import static jnr.invoke.CodegenUtils.ci;
import static jnr.invoke.FastIntMethodGenerator.isFastIntType;
import static jnr.invoke.Util.getBooleanProperty;

/**
 *
 */
class FastNumericMethodGenerator extends AbstractFastNumericMethodGenerator {
    private static final boolean ENABLED = getBooleanProperty("jnr.invoke.fast-numeric.enabled", true);
    private static final int MAX_PARAMETERS = getMaximumParameters();
    private static final String[] signatures;

    private static final String[] methodNames = {
        "invokeN0", "invokeN1", "invokeN2", "invokeN3", "invokeN4", "invokeN5", "invokeN6"
    };

    static {
        signatures = new String[MAX_PARAMETERS + 1];
        for (int i = 0; i <= MAX_PARAMETERS; i++) {
            StringBuilder sb = new StringBuilder();
            sb.append('(').append(ci(CallContext.class)).append(ci(long.class));
            for (int n = 0; n < i; n++) {
                sb.append('J');
            }
            signatures[i] = sb.append(")J").toString();
        }
    }

    public boolean isSupported(ResultType resultType, ParameterType[] parameterTypes, CallingConvention callingConvention) {
        final int parameterCount = parameterTypes.length;

        if (!ENABLED) {
            return false;
        }

        if (callingConvention != CallingConvention.DEFAULT || parameterCount > MAX_PARAMETERS) {
            return false;
        }
        final Platform platform = Platform.getPlatform();

        // Only supported on i386 and amd64 arches
        if (platform.getCPU() != Platform.CPU.I386 && platform.getCPU() != Platform.CPU.X86_64) {
            return false;
        }

        if (platform.getOS().equals(Platform.OS.WINDOWS)) {
            return false;
        }

        for (ParameterType parameterType : parameterTypes) {
            if (!isFastNumericParameter(platform, parameterType)) {
                return false;
            }
        }

        return isFastNumericResult(platform, resultType);
    }


    @Override
    String getInvokerMethodName(ResultType resultType, ParameterType[] parameterTypes, boolean ignoreErrno) {
        final int parameterCount = parameterTypes.length;

        if (parameterCount <= MAX_PARAMETERS && parameterCount <= methodNames.length) {
            return methodNames[parameterCount];

        } else {
            throw new IllegalArgumentException("invalid fast-numeric parameter count: " + parameterCount);
        }
    }

    @Override
    String getInvokerSignature(int parameterCount, Class nativeIntType) {

        if (parameterCount <= MAX_PARAMETERS && parameterCount <= signatures.length) {
            return signatures[parameterCount];

        } else {
            throw new IllegalArgumentException("invalid fast-numeric parameter count: " + parameterCount);
        }
    }

    @Override
    Class getInvokerType() {
        return long.class;
    }

    private static boolean isNumericType(Platform platform, SignatureType type) {
        return isFastIntType(platform, type)
                || type.getNativeType() == NativeType.SLONG || type.getNativeType() == NativeType.ULONG
                || type.getNativeType() == NativeType.SLONG_LONG || type.getNativeType() == NativeType.ULONG_LONG
                || type.getNativeType() == NativeType.FLOAT || type.getNativeType() == NativeType.DOUBLE
                ;
    }

    static boolean isFastNumericResult(Platform platform, ResultType type) {
        return isNumericType(platform, type)
                || NativeType.VOID == type.getNativeType()
                || NativeType.POINTER == type.getNativeType()
                ;
    }

    static boolean isFastNumericParameter(Platform platform, ParameterType parameterType) {
        return isNumericType(platform, parameterType)
            || (parameterType.getNativeType() == NativeType.POINTER && isSupportedPointerParameterType(parameterType.effectiveJavaType()));
    }

    private static boolean isSupportedPointerParameterType(Class javaParameterType) {
        return ByteBuffer.class.isAssignableFrom(javaParameterType)
                || ShortBuffer.class.isAssignableFrom(javaParameterType)
                || IntBuffer.class.isAssignableFrom(javaParameterType)
                || (LongBuffer.class.isAssignableFrom(javaParameterType) && Type.SLONG.size() == 8)
                || FloatBuffer.class.isAssignableFrom(javaParameterType)
                || DoubleBuffer.class.isAssignableFrom(javaParameterType)
                || byte[].class == javaParameterType
                || short[].class == javaParameterType
                || int[].class == javaParameterType
                || (long[].class == javaParameterType && Type.SLONG.size() == 8)
                || float[].class == javaParameterType
                || double[].class == javaParameterType
                || boolean[].class == javaParameterType
                ;
    }


    static int getMaximumParameters() {
        try {
            com.kenai.jffi.Invoker.class.getDeclaredMethod("invokeN6", CallContext.class, long.class,
                    long.class, long.class, long.class, long.class, long.class, long.class);
            return 6;
        } catch (Throwable t) {
            return 0;
        }
    }
}
