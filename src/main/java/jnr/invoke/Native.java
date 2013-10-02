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
import java.lang.invoke.MethodHandles;
import java.util.concurrent.atomic.AtomicLong;

public final class Native {

    static final boolean DEBUG = Boolean.getBoolean("jnr.invoke.compile.dump");
    static final MethodHandles.Lookup LOOKUP = MethodHandles.lookup();
    static final AtomicLong nextClassID = new AtomicLong(0);
    static final String STUB_NAME = "invokeNative";

    private Native() {
    }

    public static MethodHandle getMethodHandle(CallContext callContext, CodeAddress nativeAddress) {

        MethodHandle mh = getPrimitiveMethodHandle(callContext, nativeAddress);
        if (mh == null) {
            throw new UnsupportedOperationException("cannot generate handle for " + callContext);
        }

        return mh;
    }

    private static MethodHandle getPrimitiveMethodHandle(CallContext callContext, CodeAddress nativeAddress) {
        MethodHandleGenerator[] generators = {
                new PrimitiveX86MethodHandleGenerator(),
                new PrimitiveNumericMethodHandleGenerator(),
                new DefaultMethodHandleGenerator()
        };

        for (MethodHandleGenerator g : generators) {
            if (g.isSupported(callContext.getResultType(), callContext.parameterTypeList(), callContext.getCallingConvention())) {
                return g.createBoundHandle(callContext, nativeAddress);
            }
        }

        return null;
    }
}
