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
}
