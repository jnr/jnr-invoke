/*
 * Copyright (C) 2012-2013 Wayne Meissner
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
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

/**
 */
final class Util {
    static boolean getBooleanProperty(String propertyName, boolean defaultValue) {
        try {
            return Boolean.valueOf(System.getProperty(propertyName, Boolean.valueOf(defaultValue).toString()));
        } catch (SecurityException se) {
            return defaultValue;
        }
    }

    static int sizeof(NativeType nativeType) {
        return nativeType.size();
    }

    static Class[] javaTypeArray(SignatureType[] types) {
        Class[] javaTypes = new Class[types.length];

        for (int i = 0; i < types.length; ++i) {
            javaTypes[i] = types[i].javaType();
        }

        return javaTypes;
    }

    static MethodHandle getNotNullHandle() {
        return findStatic(AsmRuntime.class, "notNull", MethodType.methodType(boolean.class, Object.class));
    }

    static MethodHandle getIsNullHandle() {
        return findStatic(AsmRuntime.class, "isNull", MethodType.methodType(boolean.class, Object.class));
    }

    static MethodHandle findVirtual(Class klass, String methodName, MethodType methodType) {
        try {
            return Native.LOOKUP.findVirtual(klass, methodName, methodType);
        } catch (NoSuchMethodException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    static MethodHandle findStatic(Class klass, String methodName, MethodType methodType) {
        try {
            return Native.LOOKUP.findStatic(klass, methodName, methodType);
        } catch (NoSuchMethodException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    static List<ParameterType> asPrimitiveTypes(Collection<ParameterType> parameterTypes) {
        return Arrays.asList(convertParameterTypesToPrimitive(parameterTypes));
    }

    static ParameterType[] asPrimitiveTypes(ParameterType[] parameterTypes) {
        return convertParameterTypesToPrimitive(Arrays.asList(parameterTypes));
    }

    private static ParameterType[] convertParameterTypesToPrimitive(Collection<ParameterType> parameterTypes) {
        ParameterType[] primitiveParameterTypes = new ParameterType[parameterTypes.size()];
        int i = 0;
        for (ParameterType p : parameterTypes) {
            primitiveParameterTypes[i++] = p.asPrimitiveType();
        }

        return primitiveParameterTypes;
    }


    static MethodHandle getDirectCheckHandle(MethodHandle strategyLookup) {
        return MethodHandles.filterArguments(getStrategyIsDirectHandle(), 0, strategyLookup);
    }

    static MethodHandle getDirectAddressHandle(MethodHandle strategyLookup) {
        MethodHandle addressHandle = getStrategyAddressHandle()
                .asType(MethodType.methodType(long.class, ObjectParameterStrategy.class, strategyLookup.type().parameterType(0)));
        return MethodHandles.foldArguments(addressHandle, strategyLookup);
    }

    private static MethodHandle getStrategyIsDirectHandle() {
        return findVirtual(com.kenai.jffi.ObjectParameterStrategy.class, "isDirect", MethodType.methodType(boolean.class))
                .asType(MethodType.methodType(boolean.class, ObjectParameterStrategy.class));
    }

    private static MethodHandle getStrategyAddressHandle() {
        return findVirtual(com.kenai.jffi.ObjectParameterStrategy.class, "address", MethodType.methodType(long.class, Object.class));
    }
}
