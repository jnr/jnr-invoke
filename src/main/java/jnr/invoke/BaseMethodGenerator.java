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
import com.kenai.jffi.Function;
import com.kenai.jffi.Invoker;

import java.lang.invoke.MethodHandle;

import static jnr.invoke.AsmUtil.*;
import static jnr.invoke.CodegenUtils.*;
import static org.objectweb.asm.Opcodes.*;

/**
 *
 */
abstract class BaseMethodGenerator implements MethodGenerator {

    public void generate(AsmBuilder builder, String functionName, Function function,
                         ResultType resultType, ParameterType[] parameterTypes, boolean ignoreError) {
        Class[] javaParameterTypes = new Class[parameterTypes.length];
        for (int i = 0; i < parameterTypes.length; i++) {
            javaParameterTypes[i] = parameterTypes[i].getDeclaredType();
        }

        SkinnyMethodAdapter mv = new SkinnyMethodAdapter(builder.getClassVisitor(), ACC_PUBLIC | ACC_FINAL | ACC_STATIC,
                functionName,
                sig(resultType.getDeclaredType(), javaParameterTypes), null, null);
        mv.start();

        // Retrieve the jffi Invoker instance
        mv.getstatic(builder.getClassNamePath(), builder.getObjectFieldName(Invoker.getInstance(), com.kenai.jffi.Invoker.class), ci(com.kenai.jffi.Invoker.class));

        // retrieve the call context and function address
        mv.getstatic(builder.getClassNamePath(), builder.getObjectFieldName(function.getCallContext()), ci(CallContext.class));
        mv.getstatic(builder.getClassNamePath(), builder.getFunctionAddressFieldName(function), ci(long.class));

        LocalVariableAllocator localVariableAllocator = new LocalVariableAllocator(parameterTypes);

        generate(builder, mv, localVariableAllocator, function.getCallContext(), resultType, parameterTypes, ignoreError);

        mv.visitMaxs(100, localVariableAllocator.getSpaceUsed());
        mv.visitEnd();
    }

    abstract void generate(AsmBuilder builder, SkinnyMethodAdapter mv, LocalVariableAllocator localVariableAllocator, CallContext callContext, ResultType resultType, ParameterType[] parameterTypes,
                           boolean ignoreError);

    static LocalVariable loadAndConvertParameter(AsmBuilder builder, SkinnyMethodAdapter mv,
                                                 LocalVariableAllocator localVariableAllocator,
                                                 LocalVariable parameter, ParameterType parameterType) {

        MethodHandle toNativeConverter = parameterType.getToNativeConverter();
        if (toNativeConverter != null) {
            AsmBuilder.ObjectField toNativeConverterField = builder.getObjectField(toNativeConverter);
            mv.getstatic(builder.getClassNamePath(), toNativeConverterField.name, ci(toNativeConverterField.klass));
        }
        load(mv, parameterType.getDeclaredType(), parameter);

        if (toNativeConverter != null) {
            mv.invokevirtual(MethodHandle.class, "invokeExact", toNativeConverter.type().returnType(),
                toNativeConverter.type().parameterType(0));
        }

        if (toNativeConverter != null && (parameterType.getPostInvoke() != null || !parameterType.nativeJavaType().isPrimitive())) {
            LocalVariable converted = localVariableAllocator.allocate(toNativeConverter.type().returnType());
            store(mv, toNativeConverter.type().returnType(), converted);
            load(mv, toNativeConverter.type().returnType(), converted);
            return converted;
        }

        return parameter;
    }

    static boolean isPostInvokeRequired(ParameterType[] parameterTypes) {
        for (ParameterType parameterType : parameterTypes) {
            if (parameterType.getPostInvoke() != null) {
                return true;
            }
        }

        return false;
    }

    static void emitEpilogue(final AsmBuilder builder, final SkinnyMethodAdapter mv, final ResultType resultType,
                             final ParameterType[] parameterTypes,
                             final LocalVariable[] parameters, final LocalVariable[] converted) {
        if (isPostInvokeRequired(parameterTypes)) {
            tryfinally(mv, new Runnable() {
                        public void run() {
                            emitFromNativeConversion(builder, mv, resultType, resultType.nativeJavaType());
                            // ensure there is always at least one instruction inside the try {} block
                            mv.nop();
                        }
                    },
                    new Runnable() {
                        public void run() {
                            emitPostInvoke(builder, mv, parameterTypes, parameters, converted);
                        }
                    }
            );
        } else {
            emitFromNativeConversion(builder, mv, resultType, resultType.nativeJavaType());
        }
        emitReturnOp(mv, resultType.getDeclaredType());
    }

    static void emitPostInvoke(AsmBuilder builder, final SkinnyMethodAdapter mv, ParameterType[] parameterTypes,
                               LocalVariable[] parameters, LocalVariable[] converted) {
        for (int i = 0; i < converted.length; ++i) {
            if (converted[i] != null && parameterTypes[i].getPostInvoke() != null) {
                MethodHandle postInvoke = parameterTypes[i].getPostInvoke();
                AsmBuilder.ObjectField postInvokeField = builder.getObjectField(postInvoke);
                mv.getstatic(builder.getClassNamePath(), postInvokeField.name, ci(postInvokeField.klass));
                mv.aload(parameters[i]);
                mv.aload(converted[i]);
                mv.invokevirtual(MethodHandle.class, "invokeExact", postInvoke.type().returnType(),
                        postInvoke.type().parameterType(0), postInvoke.type().parameterType(1));
            }
        }
    }
}
