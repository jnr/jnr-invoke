/*
 * Copyright (C) 2008-2010 Wayne Meissner
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
import com.kenai.jffi.*;

import java.nio.*;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Utility methods that are used at runtime by generated code.
 */
public final class AsmRuntime {
    private AsmRuntime() {}

    private static final Map<String, Map<String, Object>> staticClassDataMap = new ConcurrentHashMap<String, Map<String, Object>>();

    public static Map<String, Object> getStaticClassData(String classID) {
        Map<String, Object> m = staticClassDataMap.get(classID);
        if (m != null) {
            return m;
        }
        return Collections.emptyMap();
    }

    public static void removeStaticClassData(String classID) {
        staticClassDataMap.remove(classID);
    }

    static void setStaticClassData(String classID, Map<String, Object> staticClassData) {
        staticClassDataMap.put(classID, staticClassData);
    }

    public static UnsatisfiedLinkError newUnsatisifiedLinkError(String msg) {
        return new UnsatisfiedLinkError(msg);
    }

    public static HeapInvocationBuffer newHeapInvocationBuffer(Function function) {
        return new HeapInvocationBuffer(function);
    }

    public static HeapInvocationBuffer newHeapInvocationBuffer(CallContext callContext) {
        return new HeapInvocationBuffer(callContext);
    }

    public static HeapInvocationBuffer newHeapInvocationBuffer(CallContext callContext, int objCount) {
        return new HeapInvocationBuffer(callContext, objCount);
    }

    public static long longValue(Buffer ptr) {
        return ptr != null && ptr.isDirect() ? MemoryIO.getInstance().getDirectBufferAddress(ptr) : 0L;
    }

    public static int intValue(Buffer ptr) {
        return ptr != null && ptr.isDirect()  ? (int) MemoryIO.getInstance().getDirectBufferAddress(ptr) : 0;
    }
}
