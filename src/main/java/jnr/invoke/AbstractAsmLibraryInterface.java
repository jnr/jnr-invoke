/*
 * Copyright (C) 2008-2013 Wayne Meissner
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

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 *
 */
public abstract class AbstractAsmLibraryInterface {
    protected static final com.kenai.jffi.Invoker ffi = com.kenai.jffi.Invoker.getInstance();
    private static final Map<String, Map<String, Object>> staticClassDataMap = Collections.synchronizedMap(new HashMap<String, Map<String, Object>>());
//
//    protected AbstractAsmLibraryInterface() {
//    }

    protected static Map<String, Object> getStaticClassData(String classID) {
        Map<String, Object> m = staticClassDataMap.get(classID);
        if (m != null) {
            return m;
        }
        return Collections.emptyMap();
    }

    protected static void removeStaticClassData(String classID) {
        staticClassDataMap.remove(classID);
    }

    static void setStaticClassData(String classID, Map<String, Object> staticClassData) {
        staticClassDataMap.put(classID, staticClassData);
    }
}
