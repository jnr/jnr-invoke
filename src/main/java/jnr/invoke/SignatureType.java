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

public abstract class SignatureType {
    private final NativeType nativeType;
    private final Class javaType;
    protected final com.kenai.jffi.Type jffiType;
    protected final int size;
    protected final int alignment;

    SignatureType(NativeType nativeType, Class javaType, com.kenai.jffi.Type jffiType) {
        this.nativeType = nativeType;
        this.javaType = javaType;
        this.jffiType = jffiType;
        this.size = jffiType.size();
        this.alignment = jffiType.alignment();
    }

    public int size() {
        return size;
    }

    public int alignment() {
        return alignment;
    }

    public NativeType getNativeType() {
        return nativeType;
    }

    public Class javaType() {
        return javaType;
    }

    public Class getDeclaredType() {
        return javaType;
    }

    com.kenai.jffi.Type jffiType() {
        return jffiType;
    }





    static com.kenai.jffi.Type jffiType(NativeType nativeType) {
        switch (nativeType) {
            case SCHAR:
                return com.kenai.jffi.Type.SCHAR;

            case UCHAR:
                return com.kenai.jffi.Type.UCHAR;

            case SSHORT:
                return com.kenai.jffi.Type.SSHORT;

            case USHORT:
                return com.kenai.jffi.Type.USHORT;

            case SINT:
                return com.kenai.jffi.Type.SINT;

            case UINT:
                return com.kenai.jffi.Type.UINT;

            case SLONG:
                return com.kenai.jffi.Type.SLONG;

            case ULONG:
                return com.kenai.jffi.Type.ULONG;

            case SLONG_LONG:
                return com.kenai.jffi.Type.SLONG_LONG;

            case ULONG_LONG:
                return com.kenai.jffi.Type.ULONG_LONG;

            case FLOAT:
                return com.kenai.jffi.Type.FLOAT;
            case DOUBLE:
                return com.kenai.jffi.Type.DOUBLE;

            case POINTER:
                return com.kenai.jffi.Type.POINTER;

            default:
                throw new UnsupportedOperationException();
        }
    }
}
