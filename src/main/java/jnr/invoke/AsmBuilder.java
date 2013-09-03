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

import com.kenai.jffi.Function;
import com.kenai.jffi.ObjectParameterInfo;
import org.objectweb.asm.ClassVisitor;

import java.lang.reflect.Modifier;
import java.util.*;

import static jnr.invoke.AsmUtil.boxedType;
import static jnr.invoke.AsmUtil.unboxNumber;
import static jnr.invoke.CodegenUtils.ci;
import static org.objectweb.asm.Opcodes.ACC_FINAL;
import static org.objectweb.asm.Opcodes.ACC_PRIVATE;
import static org.objectweb.asm.Opcodes.ACC_STATIC;

/**
 *
 */
class AsmBuilder {
    private final String classNamePath;
    private final ClassVisitor classVisitor;
    private final AsmClassLoader classLoader;

    private final ObjectNameGenerator functionId = new SimpleObjectNameGenerator("functionAddress");
    private final ObjectNameGenerator genericObjectId = new InferringObjectNameGenerator();

    private final Map<Long, ObjectField> functionAddresses = new HashMap<Long, ObjectField>();
    private final Map<Object, ObjectField> genericObjects = new IdentityHashMap<Object, ObjectField>();
    private final List<ObjectField> objectFields = new ArrayList<ObjectField>();

    AsmBuilder(String classNamePath, ClassVisitor classVisitor, AsmClassLoader classLoader) {
        this.classNamePath = classNamePath;
        this.classVisitor = classVisitor;
        this.classLoader = classLoader;
    }

    public String getClassNamePath() {
        return classNamePath;
    }

    ClassVisitor getClassVisitor() {
        return classVisitor;
    }

    public AsmClassLoader getClassLoader() {
        return classLoader;
    }

    private static interface ObjectNameGenerator {
        String generateName(Class cls);
    }

    private static final class SimpleObjectNameGenerator implements ObjectNameGenerator {
        private final String baseName;
        private int value;
        SimpleObjectNameGenerator(String baseName) {
            this.baseName = baseName;
            this.value = 0;
        }

        public String generateName(Class klass) {
            return baseName + "_" + ++value;
        }
    }

    private static final class InferringObjectNameGenerator implements ObjectNameGenerator {
        private final Map<Class, Long> classCount = new IdentityHashMap<>();
        public String generateName(Class klass) {
            Long count = classCount.get(klass);
            classCount.put(klass, count = count != null ? count + 1 : 1);
            return klass.getName().replace('.', '_') + '_' + count;
        }
    }

    <T> ObjectField addField(Map<T, ObjectField> map, T value, Class klass, ObjectNameGenerator objectNameGenerator) {
        ObjectField field = new ObjectField(objectNameGenerator.generateName(klass), value, klass);
        objectFields.add(field);
        map.put(value, field);
        return field;
    }

    <T> ObjectField getField(Map<T, ObjectField> map, T value, Class klass, ObjectNameGenerator objectNameGenerator) {
        ObjectField field = map.get(value);
        return field != null ? field : addField(map, value, klass, objectNameGenerator);
    }

    String getFunctionAddressFieldName(Function function) {
        return getField(functionAddresses, function.getFunctionAddress(), long.class, functionId).name;
    }

    String getFromNativeConverterName(FromNativeConverter converter) {
        return getFromNativeConverterField(converter).name;
    }

    String getToNativeConverterName(ToNativeConverter converter) {
        return getToNativeConverterField(converter).name;
    }

    private static Class nearestClass(Object obj, Class defaultClass) {
        return Modifier.isPublic(obj.getClass().getModifiers()) ? obj.getClass() : defaultClass;
    }

    ObjectField getToNativeConverterField(ToNativeConverter converter) {
        return getObjectField(converter, nearestClass(converter, ToNativeConverter.class));
    }

    ObjectField getFromNativeConverterField(FromNativeConverter converter) {
        return getObjectField(converter, nearestClass(converter, FromNativeConverter.class));
    }

    ObjectField getToNativeContextField(ToNativeContext context) {
        return getObjectField(context, nearestClass(context, ToNativeContext.class));
    }

    ObjectField getFromNativeContextField(FromNativeContext context) {
        return getObjectField(context, nearestClass(context, FromNativeContext.class));
    }

    String getObjectParameterInfoName(ObjectParameterInfo info) {
        return getObjectField(info, ObjectParameterInfo.class).name;
    }

    String getObjectFieldName(Object obj, Class klass) {
        return getObjectField(obj, klass).name;
    }

    String getObjectFieldName(Object obj) {
        return getObjectField(obj).name;
    }

    ObjectField getObjectField(Object obj, Class klass) {
        return getField(genericObjects, obj, klass, genericObjectId);
    }

    ObjectField getObjectField(Object obj) {
        return getField(genericObjects, obj, obj.getClass(), genericObjectId);
    }

    public static final class ObjectField {
        public final String name;
        public final Object value;
        public final Class klass;

        public ObjectField(String fieldName, Object fieldValue, Class fieldClass) {
            this.name = fieldName;
            this.value = fieldValue;
            this.klass = fieldClass;
        }
    }

    ObjectField[] getObjectFieldArray() {
        return objectFields.toArray(new ObjectField[objectFields.size()]);
    }

    Object[] getObjectFieldValues() {
        Object[] fieldObjects = new Object[objectFields.size()];
        int i = 0;
        for (ObjectField f : objectFields) {
            fieldObjects[i++] = f.value;
        }
        return fieldObjects;
    }

    Map<String, Object> getObjectFieldMap() {
        Map<String, Object> m = new HashMap<>();
        for (ObjectField f : objectFields) {
            m.put(f.name, f.value);
        }

        return m;
    }

    void emitStaticFieldInitialization(SkinnyMethodAdapter clinit, String classID) {
        if (!objectFields.isEmpty()) {
            clinit.ldc(classID);
            clinit.invokestatic(AsmRuntime.class, "getStaticClassData", Map.class, String.class);
            for (ObjectField f : objectFields) {
                getClassVisitor().visitField(ACC_PRIVATE | ACC_FINAL | ACC_STATIC, f.name, ci(f.klass), null, null).visitEnd();
                clinit.dup();
                clinit.ldc(f.name);
                clinit.invokeinterface(Map.class, "get", Object.class, Object.class);
                if (f.klass.isPrimitive()) {
                    Class boxedType = boxedType(f.klass);
                    clinit.checkcast(boxedType);
                    unboxNumber(clinit, boxedType, f.klass);
                } else {
                    clinit.checkcast(f.klass);
                }
                clinit.putstatic(getClassNamePath(), f.name, ci(f.klass));
            }

            clinit.pop();
            clinit.ldc(classID);
            clinit.invokestatic(AsmRuntime.class, "removeStaticClassData", void.class, String.class);
        }
    }
}
