/*
 * Copyright (C) 2009 Wayne Meissner
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

import static jnr.invoke.SignatureType.jffiType;

/**
 * Native function call context
 *
 * This class holds all the information that JFFI needs to correctly call a
 * native function, or to implement a callback from native code to java.
 */
public final class CallContext {

    /** The return type of this function */
    final ResultType resultType;

    /** The parameter types of this function */
    final ParameterType[] parameterTypes;

    final CallingConvention callingConvention;

    final boolean saveErrno;

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
        return new CallContext(resultType, parameterTypes, convention, saveErrno);
    }

    public static CallContext getCallContext(ResultType resultType, ParameterType[] parameterTypes, CallingConvention convention,
                                             boolean saveErrno, boolean faultProtect) {
        return new CallContext(resultType, parameterTypes, convention, saveErrno, faultProtect);
    }

    /**
     * Creates a new instance of <tt>Function</tt> with default calling convention.
     *
     * @param resultType The return type of the native function.
     * @param parameterTypes The parameter types the function accepts.
     */
    private CallContext(ResultType resultType, ParameterType... parameterTypes) {
        this(resultType, parameterTypes, CallingConvention.DEFAULT, true);
    }

    /**
     * Creates a new instance of <tt>Function</tt>.
     *
     * <tt>Function</tt> instances created with this constructor will save the
     * C errno contents after each call.
     *
     * @param resultType The return type of the native function.
     * @param parameterTypes The parameter types the function accepts.
     * @param convention The calling convention of the function.
     */
    private CallContext(ResultType resultType, ParameterType[] parameterTypes, CallingConvention convention) {
        this(resultType, parameterTypes, convention, true);
    }

    public CallContext(ResultType resultType, ParameterType[] parameterTypes, CallingConvention convention, boolean saveErrno) {
        this(resultType, parameterTypes, convention, saveErrno, false);
    }

    /**
     * Creates a new instance of <tt>Function</tt>.
     *
     * @param resultType The return type of the native function.
     * @param parameterTypes The parameter types the function accepts.
     * @param convention The calling convention of the function.
     * @param saveErrno Whether the errno should be saved or not
     */
    CallContext(ResultType resultType, ParameterType[] parameterTypes, CallingConvention convention,
                boolean saveErrno, boolean faultProtect) {
        this.resultType = resultType;
        this.parameterTypes = parameterTypes.clone();
        this.callingConvention = convention;
        this.saveErrno = false;
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
        return callingConvention;
    }

    com.kenai.jffi.CallContext getNativeCallContext() {
        com.kenai.jffi.Type[] nativeParamTypes = new com.kenai.jffi.Type[parameterTypes.length];

        for (int i = 0; i < nativeParamTypes.length; ++i) {
            nativeParamTypes[i] = jffiType(parameterTypes[i].getNativeType());
        }

        return com.kenai.jffi.CallContext.getCallContext(jffiType(resultType.getNativeType()),
                nativeParamTypes, jffiConvention(callingConvention), saveErrno);
    }


    public static final com.kenai.jffi.CallingConvention jffiConvention(CallingConvention callingConvention) {
        return callingConvention == CallingConvention.DEFAULT ? com.kenai.jffi.CallingConvention.DEFAULT : com.kenai.jffi.CallingConvention.STDCALL;
    }
}
