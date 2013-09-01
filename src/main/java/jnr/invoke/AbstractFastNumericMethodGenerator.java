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

import com.kenai.jffi.Invoker;
import com.kenai.jffi.CallContext;
import com.kenai.jffi.ObjectParameterInfo;
import org.objectweb.asm.Label;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.*;
import java.util.Map;

import static jnr.invoke.AsmUtil.*;
import static jnr.invoke.CodegenUtils.ci;
import static jnr.invoke.CodegenUtils.p;
import static jnr.invoke.NumberUtil.*;

/**
 *
 */
abstract class AbstractFastNumericMethodGenerator extends BaseMethodGenerator {

    public void generate(final AsmBuilder builder, final SkinnyMethodAdapter mv,
                         LocalVariableAllocator localVariableAllocator, CallContext callContext,
                         final ResultType resultType, final ParameterType[] parameterTypes,
                         boolean ignoreError) {
        // [ stack contains: Invoker, Function ]
        final Class nativeIntType = getInvokerType();
        final LocalVariable objCount = localVariableAllocator.allocate(int.class);
        final LocalVariable[] parameters = AsmUtil.getParameterVariables(parameterTypes);
        final LocalVariable[] converted = new LocalVariable[parameterTypes.length];
        int pointerCount = 0;

        // Load, convert, and un-box parameters
        for (int i = 0; i < parameterTypes.length; ++i) {
            converted[i] = loadAndConvertParameter(builder, mv, localVariableAllocator, parameters[i], parameterTypes[i]);

            Class javaParameterType = parameterTypes[i].effectiveJavaType();
            ToNativeOp op = ToNativeOp.get(parameterTypes[i]);
            if (op != null && op.isPrimitive()) {
                op.emitPrimitive(mv, getInvokerType(), parameterTypes[i].getNativeType());

            } else if (hasPointerParameterStrategy(parameterTypes[i])) {
                pointerCount = emitDirectCheck(mv, javaParameterType, nativeIntType, converted[i], objCount, pointerCount);

            } else {
                throw new IllegalArgumentException("unsupported parameter type " + parameterTypes[i].getDeclaredType());
            }
        }

        // stack now contains [ IntInvoker, Function, int/long args ]
        Label hasObjects = new Label();
        Label convertResult = new Label();
        if (pointerCount > 0) {
            mv.iload(objCount);
            mv.ifne(hasObjects);
        }
        mv.invokevirtual(p(Invoker.class),
                getInvokerMethodName(resultType, parameterTypes, ignoreError),
                getInvokerSignature(parameterTypes.length, nativeIntType));

        if (pointerCount > 0) mv.label(convertResult);

        Class javaReturnType = resultType.effectiveJavaType();
        Class nativeReturnType = nativeIntType;

        // Convert the result from long/int to the correct return type
        if (Float.class == javaReturnType || float.class == javaReturnType) {
            narrow(mv, nativeIntType, int.class);
            mv.invokestatic(Float.class, "intBitsToFloat", float.class, int.class);
            nativeReturnType = float.class;

        } else if (Double.class == javaReturnType || double.class == javaReturnType) {
            widen(mv, nativeIntType, long.class);
            mv.invokestatic(Double.class, "longBitsToDouble", double.class, long.class);
            nativeReturnType = double.class;

        }

        // box and/or narrow/widen the return value if needed
        final Class unboxedResultType = unboxedReturnType(javaReturnType);
        convertPrimitive(mv, nativeReturnType, unboxedResultType, resultType.getNativeType());
        emitEpilogue(builder, mv, resultType, parameterTypes, parameters, converted, null);

        /* --  method returns above - below is an alternative path -- */

        // Now implement heap object support
        if (pointerCount > 0) {
            mv.label(hasObjects);

            if (int.class == nativeIntType) {
                // For int invoker, need to convert all the int args to long
                LocalVariable[] tmp = new LocalVariable[parameterTypes.length];
                for (int i = parameterTypes.length - 1; i > 0; i--) {
                    tmp[i] = localVariableAllocator.allocate(int.class);
                    mv.istore(tmp[i]);
                }
                if (parameterTypes.length > 0) mv.i2l();
                // Now reload them back onto the parameter stack, and widen to long
                for (int i = 1; i < parameterTypes.length; i++) {
                    mv.iload(tmp[i]);
                    mv.i2l();
                }
            }

            mv.iload(objCount);
            // Need to load all the converters onto the stack
            LocalVariable[] strategies = new LocalVariable[parameterTypes.length];
            for (int i = 0; i < parameterTypes.length; i++) {
                Class javaParameterType = parameterTypes[i].effectiveJavaType();
                if (hasPointerParameterStrategy(parameterTypes[i])) {
                    mv.aload(converted[i]);
                    emitParameterStrategyLookup(mv, javaParameterType);
                    mv.astore(strategies[i] = localVariableAllocator.allocate(ObjectParameterStrategy.class));

                    mv.aload(converted[i]);
                    mv.aload(strategies[i]);
                    mv.aload(0);

                    ObjectParameterInfo info = ObjectParameterInfo.create(i, parameterTypes[i].getDataDirection().getArrayFlags());

                    mv.getfield(builder.getClassNamePath(), builder.getObjectParameterInfoName(info),
                            ci(ObjectParameterInfo.class));
                }
            }
            mv.invokevirtual(p(Invoker.class),
                    getObjectParameterMethodName(parameterTypes.length),
                    getObjectParameterMethodSignature(parameterTypes.length, pointerCount));
            narrow(mv, long.class, nativeIntType);
            mv.go_to(convertResult);
        }
    }

    private static void addStrategyParameterType(Map<Class<? extends ObjectParameterStrategy>, Method> map,
                                                 Class<? extends ObjectParameterStrategy> strategyClass, Class parameterType) {
        try {
            Method addressMethod = strategyClass.getDeclaredMethod("address", parameterType);
            if (Modifier.isPublic(addressMethod.getModifiers()) && Modifier.isPublic(addressMethod.getDeclaringClass().getModifiers())) {
                map.put(strategyClass, addressMethod);
            }

        } catch (NoSuchMethodException ignored) {}
    }


    static Class<? extends ObjectParameterStrategy> emitParameterStrategyLookup(SkinnyMethodAdapter mv, Class javaParameterType) {
        throw new RuntimeException("no conversion strategy for: " + javaParameterType);
    }

    static boolean hasPointerParameterStrategy(ParameterType parameterType) {
        return false;
    }

    static void emitParameterStrategyAddress(SkinnyMethodAdapter mv, Class nativeIntType,
                                             Class<? extends ObjectParameterStrategy> strategyClass, LocalVariable strategy, LocalVariable parameter) {
        // Get the native address (will return zero for heap objects)
        mv.aload(strategy);
        mv.aload(parameter);
        mv.invokevirtual(ObjectParameterStrategy.class, "address", long.class, Object.class);

        narrow(mv, long.class, nativeIntType);
    }

    static int emitDirectCheck(SkinnyMethodAdapter mv, Class javaParameterClass, Class nativeIntType,
                               LocalVariable parameter, LocalVariable objCount, int pointerCount) {
        if (pointerCount < 1) {
            mv.iconst_0();
            mv.istore(objCount);
        }

        Label next = new Label();
        Label nullPointer = new Label();
        mv.ifnull(nullPointer);
        if (Buffer.class.isAssignableFrom(javaParameterClass)) {
            mv.aload(parameter);
            mv.invokestatic(AsmRuntime.class, "longValue", long.class, Buffer.class);
            narrow(mv, long.class, nativeIntType);
            mv.aload(parameter);
            mv.invokevirtual(Buffer.class, "isDirect", boolean.class);
            mv.iftrue(next);

        } else if (javaParameterClass.isArray() && javaParameterClass.getComponentType().isPrimitive()) {
            // address of arrays is always zero - they have to be handled as objects
            if (long.class == nativeIntType) mv.lconst_0(); else mv.iconst_0();

        } else {
            throw new UnsupportedOperationException("unsupported parameter type: " + javaParameterClass);
        }

        mv.iinc(objCount, 1);
        mv.go_to(next);

        mv.label(nullPointer);
        if (long.class == nativeIntType) mv.lconst_0(); else mv.iconst_0();
        mv.label(next);

        return ++pointerCount;
    }

    static String getObjectParameterMethodName(int parameterCount) {
        return "invokeN" + parameterCount;
    }

    static String getObjectParameterMethodSignature(int parameterCount, int pointerCount) {
        StringBuilder sb = new StringBuilder();
        sb.append('(').append(ci(CallContext.class)).append(ci(long.class));
        for (int i = 0; i < parameterCount; i++) {
            sb.append('J');
        }
        sb.append('I'); // objCount
        for (int n = 0; n < pointerCount; n++) {
            sb.append(ci(Object.class));
            sb.append(ci(ObjectParameterStrategy.class));
            sb.append(ci(ObjectParameterInfo.class));
        }
        sb.append(")J");
        return sb.toString();
    }

    abstract String getInvokerMethodName(ResultType resultType, ParameterType[] parameterTypes,
                                         boolean ignoreErrno);

    abstract String getInvokerSignature(int parameterCount, Class nativeIntType);
    abstract Class getInvokerType();
}
