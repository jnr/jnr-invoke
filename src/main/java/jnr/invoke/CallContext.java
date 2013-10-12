/*
 * Copyright (C) 2009-2013 Wayne Meissner
 *
 * This file is part of jffi.
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
 *
 * 
 * Alternatively, you can redistribute it and/or modify it under
 * the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU Lesser General Public License
 * for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this work.  If not, see <http://www.gnu.org/licenses/>.
 */

package jnr.invoke;

import java.lang.invoke.MethodType;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static jnr.invoke.Util.asPrimitiveTypes;
import static jnr.invoke.Util.javaTypeArray;

/**
 * Native function call context
 *
 * This class holds all the information that JFFI needs to correctly call a
 * native function, or to implement a callback from native code to java.
 */
public final class CallContext {

    public static final int SAVE_ERRNO    = 0x1;
    public static final int CDECL         = 0x2;
    public static final int STDCALL       = 0x4;
    public static final int FAULT_PROTECT = 0x8;
    public static final int DEFAULT = (SAVE_ERRNO | CDECL);
    private static final int VALID_FLAGS = (SAVE_ERRNO | CDECL | STDCALL | FAULT_PROTECT);

    /** The return type of this function */
    private final ResultType resultType;

    /** The parameter types of this function */
    private final ParameterType[] parameterTypes;

    private final int flags;

    private com.kenai.jffi.CallContext jffiContext;

    /**
     * Returns a {@link CallContext} instance.  This may return a previously cached instance that matches
     * the signature requested, and should be used in preference to instantiating new instances.
     *
     * @param resultType The return type of the native function.
     * @param parameterTypes The parameter types the function accepts.
     * @param flags the flags for the call.
     * @return An instance of CallContext
     */
    public static CallContext getCallContext(ResultType resultType, ParameterType[] parameterTypes, int flags) {
        return new CallContext(resultType, parameterTypes, flags);
    }

    /**
     * Returns a {@link CallContext} instance.  This may return a previously cached instance that matches
     * the signature requested, and should be used in preference to instantiating new instances.
     *
     * @param resultType The return type of the native function.
     * @param parameterTypes The parameter types the function accepts.
     * @param convention The calling convention of the function.
     * @param saveErrno Indicates that the errno should be saved
     * @return An instance of CallContext
     */
    public static CallContext getCallContext(ResultType resultType, ParameterType[] parameterTypes, CallingConvention convention, boolean saveErrno) {
        return new CallContext(resultType, parameterTypes, flags(convention) | (saveErrno ? SAVE_ERRNO : 0));
    }

    public static CallContext getCallContext(ResultType resultType, ParameterType[] parameterTypes, CallingConvention convention,
                                             boolean saveErrno, boolean faultProtect) {
        return new CallContext(resultType, parameterTypes, flags(convention) | (saveErrno ? SAVE_ERRNO : 0) | (faultProtect ? FAULT_PROTECT : 0));
    }

    /**
     * Returns a {@link CallContext} instance.  This may return a previously cached instance that matches
     * the signature requested, and should be used in preference to instantiating new instances.
     *
     * @param flags Flags.
     * @param resultType The return type of the native function.
     * @param parameterTypes The parameter types the function accepts.
     * @return An instance of CallContext
     */
    public static CallContext getCallContext(int flags, ResultType resultType, ParameterType... parameterTypes) {
        return new CallContext(resultType, parameterTypes, flags);
    }

    /**
     * Creates a new instance of <tt>Function</tt>.
     *
     * @param resultType The return type of the native function.
     * @param parameterTypes The parameter types the function accepts.
     */
    private CallContext(ResultType resultType, ParameterType[] parameterTypes, int flags) {
        this.resultType = resultType;
        this.parameterTypes = parameterTypes.clone();
        this.flags = flags & VALID_FLAGS;
    }

    /**
     * Gets the number of parameters the native function accepts.
     *
     * @return The number of parameters the native function accepts.
     */
    public final int getParameterCount() {
        return parameterTypes.length;
    }

    /**
     * Gets the native return type of this function.
     *
     * @return The native return type of this function.
     */
    public final ResultType getResultType() {
        return resultType;
    }

    /**
     * Gets the type of a parameter.
     *
     * @param index The index of the parameter in the function signature
     * @return The <tt>Type</tt> of the parameter.
     */
    public final ParameterType getParameterType(int index) {
        return parameterTypes[index];
    }

    public CallingConvention getCallingConvention() {
        return callingConvention(flags);
    }

    com.kenai.jffi.CallContext getNativeCallContext() {
        return jffiContext != null ? jffiContext : createNativeCallContext();
    }

    boolean saveErrno() {
        return (flags & SAVE_ERRNO) != 0;
    }

    private synchronized com.kenai.jffi.CallContext createNativeCallContext() {
        if (jffiContext != null) {
            return jffiContext;
        }
        com.kenai.jffi.Type[] nativeParamTypes = new com.kenai.jffi.Type[parameterTypes.length];

        for (int i = 0; i < nativeParamTypes.length; ++i) {
            nativeParamTypes[i] = parameterTypes[i].jffiType();
        }

        return jffiContext = com.kenai.jffi.CallContext.getCallContext(resultType.jffiType(),
                nativeParamTypes, jffiConvention(flags), saveErrno(), (flags & FAULT_PROTECT) != 0);
    }

    MethodType methodType() {
        return MethodType.methodType(resultType.javaType(), javaTypeArray(parameterTypes));
    }

    ParameterType[] parameterTypeArray() {
        return parameterTypes.clone();
    }

    List<ParameterType> parameterTypeList() {
        return Collections.unmodifiableList(Arrays.asList(parameterTypes));
    }

    CallContext asPrimitiveContext() {
        return CallContext.getCallContext(flags, resultType.asPrimitiveType(), asPrimitiveTypes(parameterTypes));
    }

    static com.kenai.jffi.CallingConvention jffiConvention(int flags) {
        return (flags & STDCALL) != 0 ? com.kenai.jffi.CallingConvention.STDCALL : com.kenai.jffi.CallingConvention.DEFAULT;
    }

    static CallingConvention callingConvention(int flags) {
        return (flags & STDCALL) != 0 ? CallingConvention.STDCALL : CallingConvention.DEFAULT;
    }

    private static int flags(CallingConvention callingConvention) {
        return callingConvention == CallingConvention.STDCALL ? STDCALL : CDECL;
    }
}
