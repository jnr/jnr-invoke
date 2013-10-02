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
import com.kenai.jffi.ObjectParameterInfo;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;

import java.io.OutputStreamWriter;
import java.lang.invoke.MethodHandle;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

import static jnr.invoke.AsmUtil.*;
import static jnr.invoke.CodegenUtils.*;
import static jnr.invoke.Native.*;
import static jnr.invoke.NumberUtil.convertPrimitive;
import static jnr.invoke.Util.javaTypeArray;
import static jnr.invoke.Util.sizeof;
import static org.objectweb.asm.Opcodes.*;

/**
 *
 */
final class DefaultMethodHandleGenerator implements MethodHandleGenerator {
    @Override
    public MethodHandle createBoundHandle(jnr.invoke.CallContext callContext, CodeAddress nativeAddress) {
        AsmClassLoader classLoader = new AsmClassLoader(Native.class.getClassLoader());

        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
        ClassVisitor cv = DEBUG ? AsmUtil.newCheckClassAdapter(cw) : cw;

        AsmBuilder builder = new AsmBuilder(p(Native.class) + "$jnr$ffi$" + nextClassID.getAndIncrement(), cv, classLoader);

        cv.visit(V1_7, ACC_PUBLIC | ACC_FINAL, builder.getClassNamePath(), null, p(java.lang.Object.class), new String[0]);
        ResultType resultType = callContext.getResultType().asPrimitiveType();

        generate(builder, STUB_NAME, callContext.getNativeCallContext(), nativeAddress.address(), resultType, callContext.parameterTypeArray());

        // Stash a strong ref to the library, so it doesn't get garbage collected.
        builder.getObjectField(nativeAddress);

        emitDefaultConstructor(cv);
        emitStaticFieldInitialization(builder, cv);

        cv.visitEnd();


        try {
            Class implClass = classLoader.defineClass(builder.getClassNamePath().replace("/", "."), cw.toByteArray(),
                    DEBUG ? new OutputStreamWriter(System.err) : null);

            return LOOKUP.findStatic(implClass, STUB_NAME, callContext.methodType());

        } catch (Throwable ex) {
            throw new RuntimeException(ex);
        }
    }

    public boolean isSupported(ResultType resultType, Collection<ParameterType> parameterTypes, CallingConvention callingConvention) {
        for (ParameterType parameterType : parameterTypes) {
            if (!parameterType.javaType().isPrimitive() && parameterType.getObjectStrategyHandle() == null) {
                return false;
            }
        }

        return resultType.javaType().isPrimitive();
    }

    private static void generate(AsmBuilder builder, String functionName, CallContext callContext, long function,
                         ResultType resultType, ParameterType[] parameterTypes) {

        SkinnyMethodAdapter mv = new SkinnyMethodAdapter(builder.getClassVisitor(), ACC_PUBLIC | ACC_FINAL | ACC_STATIC,
                functionName, sig(resultType.javaType(), javaTypeArray(parameterTypes)), null, null);
        mv.start();

        // Retrieve the jffi Invoker instance
        mv.getstatic(builder.getClassNamePath(), builder.getObjectFieldName(Invoker.getInstance(), com.kenai.jffi.Invoker.class), ci(com.kenai.jffi.Invoker.class));

        // retrieve the call context and function address
        mv.getstatic(builder.getClassNamePath(), builder.getObjectFieldName(callContext), ci(CallContext.class));
        mv.ldc(function);

        LocalVariableAllocator localVariableAllocator = new LocalVariableAllocator(parameterTypes);

        // [ stack contains: Invoker, Function ]
        // Create a new InvocationBuffer
        mv.getstatic(builder.getClassNamePath(), builder.getObjectFieldName(callContext), ci(CallContext.class));
        mv.invokestatic(AsmRuntime.class, "newHeapInvocationBuffer", HeapInvocationBuffer.class, CallContext.class);
        // [ stack contains: Invoker, Function, HeapInvocationBuffer ]

        final LocalVariable[] parameters = AsmUtil.getParameterVariables(parameterTypes, true);

        for (int i = 0; i < parameterTypes.length; ++i) {
            MarshalOp marshalOp = getMarshalOp(parameterTypes[i].nativeType());

            mv.dup(); // HeapInvocationBuffer
            load(mv, parameterTypes[i].javaType(), parameters[i]);

            if (parameterTypes[i].getObjectStrategyHandle() != null) {

                mv.getstatic(builder.getClassNamePath(), builder.getObjectFieldName(parameterTypes[i].getObjectStrategyHandle()), ci(MethodHandle.class));
                load(mv, parameterTypes[i].javaType(), parameters[i]);
                mv.invokevirtual(MethodHandle.class, "invokeExact", ObjectParameterStrategy.class, parameterTypes[i].javaType());
                mv.getstatic(builder.getClassNamePath(),
                        builder.getObjectFieldName(ObjectParameterInfo.create(i, parameterTypes[i].getDataDirection().getArrayFlags())),
                        ci(ObjectParameterInfo.class));
                mv.invokevirtual(HeapInvocationBuffer.class, "putObject", void.class, Object.class, com.kenai.jffi.ObjectParameterStrategy.class, ObjectParameterInfo.class);

            } else {

                convertPrimitive(mv, parameterTypes[i].javaType(), marshalOp.getPrimitiveClass(), parameterTypes[i].nativeType());
                mv.invokevirtual(HeapInvocationBuffer.class, marshalOp.getMethodName(), void.class, marshalOp.getPrimitiveClass());
            }
        }

        InvokeOp iop = getInvokeOp(resultType);

        mv.invokevirtual(Invoker.class, iop.getMethodName(), iop.getPrimitiveClass(), CallContext.class, long.class, HeapInvocationBuffer.class);

        // narrow/widen the return value if needed
        convertPrimitive(mv, iop.getPrimitiveClass(), resultType.javaType(), resultType.nativeType());
        emitReturnOp(mv, resultType.javaType());

        mv.visitMaxs(100, localVariableAllocator.getSpaceUsed());
        mv.visitEnd();
    }

    private static InvokeOp getInvokeOp(ResultType resultType) {
        InvokeOp iop = invokeOps.get(resultType.nativeType());
        if (iop == null) {
            throw new IllegalArgumentException("unsupported return type " + resultType.javaType());
        }
        return iop;
    }

    private static MarshalOp getMarshalOp(NativeType nativeType) {
        MarshalOp marshalOp = marshalOps.get(nativeType);
        if (marshalOp == null) {
            throw new IllegalArgumentException("unsupported parameter type " + nativeType);
        }

        return marshalOp;
    }

    private static abstract class Operation {
        private final String methodName;
        private final Class primitiveClass;

        private Operation(String methodName, Class primitiveClass) {
            this.methodName = methodName;
            this.primitiveClass = primitiveClass;
        }

        public String getMethodName() {
            return methodName;
        }

        public Class getPrimitiveClass() {
            return primitiveClass;
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

    private static final Map<NativeType, MarshalOp> marshalOps;
    private static final Map<NativeType, InvokeOp> invokeOps;
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

}
