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
import com.kenai.jffi.HeapInvocationBuffer;
import com.kenai.jffi.Invoker;
import com.kenai.jffi.ObjectParameterStrategy;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

import static jnr.invoke.AbstractFastNumericMethodGenerator.emitParameterStrategyLookup;
import static jnr.invoke.AbstractFastNumericMethodGenerator.hasPointerParameterStrategy;
import static jnr.invoke.CodegenUtils.ci;
import static jnr.invoke.NumberUtil.convertPrimitive;
import static jnr.invoke.NumberUtil.sizeof;

/**
 *
 */
final class BufferMethodGenerator extends BaseMethodGenerator {
    private static abstract class Operation {
        final String methodName;
        final Class primitiveClass;

        private Operation(String methodName, Class primitiveClass) {
            this.methodName = methodName;
            this.primitiveClass = primitiveClass;
        }
    }

    private static final class MarshalOp extends Operation {
        private MarshalOp(String methodName, Class primitiveClass) {
            super("put" + methodName, primitiveClass);
        }
    }

    private static final class InvokeOp extends Operation {
        private InvokeOp(String methodName, Class primitiveClass) {
            super("invoke" + methodName, primitiveClass);
        }
    }

    static final Map<NativeType, MarshalOp> marshalOps;
    static final Map<NativeType, InvokeOp> invokeOps;
    static {
        Map<NativeType, MarshalOp> mops = new EnumMap<NativeType, MarshalOp>(NativeType.class);
        Map<NativeType, InvokeOp> iops = new EnumMap<NativeType, InvokeOp>(NativeType.class);
        mops.put(NativeType.SCHAR, new MarshalOp("Byte", int.class));
        mops.put(NativeType.UCHAR, new MarshalOp("Byte", int.class));
        mops.put(NativeType.SSHORT, new MarshalOp("Short", int.class));
        mops.put(NativeType.USHORT, new MarshalOp("Short", int.class));
        mops.put(NativeType.SINT, new MarshalOp("Int", int.class));
        mops.put(NativeType.UINT, new MarshalOp("Int", int.class));
        mops.put(NativeType.SLONG_LONG, new MarshalOp("Long", long.class));
        mops.put(NativeType.ULONG_LONG, new MarshalOp("Long", long.class));
        mops.put(NativeType.FLOAT, new MarshalOp("Float", float.class));
        mops.put(NativeType.DOUBLE, new MarshalOp("Double", double.class));
        mops.put(NativeType.POINTER, new MarshalOp("Address", long.class));
        if (sizeof(NativeType.SLONG) == 4) {
            mops.put(NativeType.SLONG, new MarshalOp("Int", int.class));
            mops.put(NativeType.ULONG, new MarshalOp("Int", int.class));
        } else {
            mops.put(NativeType.SLONG, new MarshalOp("Long", long.class));
            mops.put(NativeType.ULONG, new MarshalOp("Long", long.class));
        }

        iops.put(NativeType.SCHAR, new InvokeOp("Int", int.class));
        iops.put(NativeType.UCHAR, new InvokeOp("Int", int.class));
        iops.put(NativeType.SSHORT, new InvokeOp("Int", int.class));
        iops.put(NativeType.USHORT, new InvokeOp("Int", int.class));
        iops.put(NativeType.SINT, new InvokeOp("Int", int.class));
        iops.put(NativeType.UINT, new InvokeOp("Int", int.class));
        iops.put(NativeType.VOID, new InvokeOp("Int", int.class));
        iops.put(NativeType.SLONG_LONG, new InvokeOp("Long", long.class));
        iops.put(NativeType.ULONG_LONG, new InvokeOp("Long", long.class));
        iops.put(NativeType.FLOAT, new InvokeOp("Float", float.class));
        iops.put(NativeType.DOUBLE, new InvokeOp("Double", double.class));
        iops.put(NativeType.POINTER, new InvokeOp("Address", long.class));
        if (sizeof(NativeType.SLONG) == 4) {
            iops.put(NativeType.SLONG, new InvokeOp("Int", int.class));
            iops.put(NativeType.ULONG, new InvokeOp("Int", int.class));
        } else {
            iops.put(NativeType.SLONG, new InvokeOp("Long", long.class));
            iops.put(NativeType.ULONG, new InvokeOp("Long", long.class));
        }
        marshalOps = Collections.unmodifiableMap(mops);
        invokeOps = Collections.unmodifiableMap(iops);
    }

    @Override
    void generate(AsmBuilder builder, SkinnyMethodAdapter mv, LocalVariableAllocator localVariableAllocator, CallContext callContext, ResultType resultType, ParameterType[] parameterTypes, boolean ignoreError) {
        generateBufferInvocation(builder, mv, localVariableAllocator, callContext, resultType, parameterTypes);
    }

    public boolean isSupported(ResultType resultType, ParameterType[] parameterTypes, CallingConvention callingConvention) {
        // Buffer invocation supports everything
        return true;
    }

    private static void emitPrimitiveOp(final SkinnyMethodAdapter mv, ParameterType parameterType, ToNativeOp op) {
        MarshalOp marshalOp = marshalOps.get(parameterType.getNativeType());
        if (marshalOp == null) {
            throw new IllegalArgumentException("unsupported parameter type " + parameterType);
        }

        op.emitPrimitive(mv, marshalOp.primitiveClass, parameterType.getNativeType());
        mv.invokevirtual(HeapInvocationBuffer.class, marshalOp.methodName, void.class, marshalOp.primitiveClass);
    }

    void generateBufferInvocation(final AsmBuilder builder, final SkinnyMethodAdapter mv, LocalVariableAllocator localVariableAllocator, CallContext callContext, final ResultType resultType, final ParameterType[] parameterTypes) {
        // [ stack contains: Invoker, Function ]
        // Create a new InvocationBuffer
        mv.getstatic(builder.getClassNamePath(), builder.getObjectFieldName(callContext), ci(CallContext.class));
        mv.invokestatic(AsmRuntime.class, "newHeapInvocationBuffer", HeapInvocationBuffer.class, CallContext.class);
        // [ stack contains: Invoker, Function, HeapInvocationBuffer ]

        final LocalVariable[] parameters = AsmUtil.getParameterVariables(parameterTypes, true);
        final LocalVariable[] converted = new LocalVariable[parameterTypes.length];
        LocalVariable[] strategies = new LocalVariable[parameterTypes.length];

        for (int i = 0; i < parameterTypes.length; ++i) {
            mv.dup(); // dup ref to HeapInvocationBuffer

            converted[i] = loadAndConvertParameter(builder, mv, localVariableAllocator, parameters[i], parameterTypes[i]);

            final Class javaParameterType = parameterTypes[i].effectiveJavaType();
            ToNativeOp op = ToNativeOp.get(parameterTypes[i]);
            if (op != null && op.isPrimitive()) {
                emitPrimitiveOp(mv, parameterTypes[i], op);

            } else if (hasPointerParameterStrategy(parameterTypes[i])) {
                emitParameterStrategyLookup(mv, javaParameterType);
                mv.astore(strategies[i] = localVariableAllocator.allocate(ObjectParameterStrategy.class));

                mv.aload(converted[i]);
                mv.aload(strategies[i]);
                mv.pushInt(parameterTypes[i].getDataDirection().getArrayFlags());
                mv.invokevirtual(HeapInvocationBuffer.class, "putObject", void.class, Object.class, ObjectParameterStrategy.class, int.class);

            } else {
                throw new IllegalArgumentException("unsupported parameter type " + parameterTypes[i]);
            }
        }

        InvokeOp iop = invokeOps.get(resultType.getNativeType());
        if (iop == null) {
            throw new IllegalArgumentException("unsupported return type " + resultType.getDeclaredType());
        }

        mv.invokevirtual(Invoker.class, iop.methodName, iop.primitiveClass, CallContext.class, long.class, HeapInvocationBuffer.class);

        // narrow/widen the return value if needed
        convertPrimitive(mv, iop.primitiveClass, resultType.effectiveJavaType(), resultType.getNativeType());
        emitEpilogue(builder, mv, resultType, parameterTypes, parameters, converted);
    }
}
