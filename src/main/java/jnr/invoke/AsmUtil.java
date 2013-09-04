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

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;

import java.io.OutputStream;
import java.io.PrintWriter;
import java.lang.invoke.MethodHandle;
import java.lang.reflect.Constructor;

import static jnr.invoke.CodegenUtils.*;
import static jnr.invoke.NumberUtil.*;
import static jnr.invoke.Util.sizeof;

final class AsmUtil {
    private AsmUtil() {}
    
    public static MethodVisitor newTraceMethodVisitor(MethodVisitor mv) {
        try {
            Class<? extends MethodVisitor> tmvClass = Class.forName("org.objectweb.asm.util.TraceMethodVisitor").asSubclass(MethodVisitor.class);
            Constructor<? extends MethodVisitor> c = tmvClass.getDeclaredConstructor(MethodVisitor.class);
            return c.newInstance(mv);
        } catch (Throwable t) {
            return mv;
        }
    }

    public static ClassVisitor newTraceClassVisitor(ClassVisitor cv, OutputStream out) {
        return newTraceClassVisitor(cv, new PrintWriter(out, true));
    }

    public static ClassVisitor newTraceClassVisitor(ClassVisitor cv, PrintWriter out) {
        try {

            Class<? extends ClassVisitor> tmvClass = Class.forName("org.objectweb.asm.util.TraceClassVisitor").asSubclass(ClassVisitor.class);
            Constructor<? extends ClassVisitor> c = tmvClass.getDeclaredConstructor(ClassVisitor.class, PrintWriter.class);
            return c.newInstance(cv, out);
        } catch (Throwable t) {
            return cv;
        }
    }

    public static ClassVisitor newTraceClassVisitor(PrintWriter out) {
        try {

            Class<? extends ClassVisitor> tmvClass = Class.forName("org.objectweb.asm.util.TraceClassVisitor").asSubclass(ClassVisitor.class);
            Constructor<? extends ClassVisitor> c = tmvClass.getDeclaredConstructor(PrintWriter.class);
            return c.newInstance(out);
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }

    public static ClassVisitor newCheckClassAdapter(ClassVisitor cv) {
        try {
            Class<? extends ClassVisitor> tmvClass = Class.forName("org.objectweb.asm.util.CheckClassAdapter").asSubclass(ClassVisitor.class);
            Constructor<? extends ClassVisitor> c = tmvClass.getDeclaredConstructor(ClassVisitor.class);
            return c.newInstance(cv);
        } catch (Throwable t) {
            return cv;
        }
    }

    public static Class unboxedType(Class boxedType) {
        if (boxedType == Byte.class) {
            return byte.class;

        } else if (boxedType == Short.class) {
            return short.class;

        } else if (boxedType == Integer.class) {
            return int.class;

        } else if (boxedType == Long.class) {
            return long.class;

        } else if (boxedType == Float.class) {
            return float.class;

        } else if (boxedType == Double.class) {
            return double.class;

        } else if (boxedType == Boolean.class) {
            return boolean.class;

        } else {
            return boxedType;
        }
    }

    public static Class boxedType(Class type) {
        if (type == byte.class) {
            return Byte.class;
        } else if (type == short.class) {
            return Short.class;
        } else if (type == int.class) {
            return Integer.class;
        } else if (type == long.class) {
            return Long.class;
        } else if (type == float.class) {
            return Float.class;
        } else if (type == double.class) {
            return Double.class;
        } else if (type == boolean.class) {
            return Boolean.class;
        } else {
            return type;
        }
    }

    
    static void emitReturnOp(SkinnyMethodAdapter mv, Class returnType) {
        if (!returnType.isPrimitive()) {
            mv.areturn();
        } else if (long.class == returnType) {
            mv.lreturn();
        } else if (float.class == returnType) {
            mv.freturn();
        } else if (double.class == returnType) {
            mv.dreturn();
        } else if (void.class == returnType) {
            mv.voidreturn();
        } else {
            mv.ireturn();
        }
    }

    /**
     * Calculates the size of a local variable
     *
     * @param type The type of parameter
     * @return The size in parameter units
     */
    static int calculateLocalVariableSpace(Class type) {
        return long.class == type || double.class == type ? 2 : 1;
    }

    /**
     * Calculates the size of a local variable
     *
     * @param type The type of parameter
     * @return The size in parameter units
     */
    static int calculateLocalVariableSpace(SignatureType type) {
        return calculateLocalVariableSpace(type.getDeclaredType());
    }

    /**
     * Calculates the size of a list of types in the local variable area.
     *
     * @param types The type of parameter
     * @return The size in parameter units
     */
    static int calculateLocalVariableSpace(Class... types) {
        int size = 0;

        for (int i = 0; i < types.length; ++i) {
            size += calculateLocalVariableSpace(types[i]);
        }

        return size;
    }

    /**
     * Calculates the size of a list of types in the local variable area.
     *
     * @param types The type of parameter
     * @return The size in parameter units
     */
    static int calculateLocalVariableSpace(SignatureType... types) {
        int size = 0;

        for (SignatureType type : types) {
            size += calculateLocalVariableSpace(type);
        }

        return size;
    }

    private static void unboxBoolean(final SkinnyMethodAdapter mv, Class boxedType, final Class nativeType) {
        mv.invokevirtual(p(boxedType), "booleanValue", "()Z");
        widen(mv, boolean.class, nativeType);
    }

    static void unboxNumber(final SkinnyMethodAdapter mv, final Class boxedType, final Class unboxedType,
                                  final NativeType nativeType) {

        if (Number.class.isAssignableFrom(boxedType)) {

            switch (nativeType) {
                case SCHAR:
                case UCHAR:
                    mv.invokevirtual(p(boxedType), "byteValue", "()B");
                    convertPrimitive(mv, byte.class, unboxedType, nativeType);
                    break;

                case SSHORT:
                case USHORT:
                    mv.invokevirtual(p(boxedType), "shortValue", "()S");
                    convertPrimitive(mv, short.class, unboxedType, nativeType);
                    break;

                case SINT:
                case UINT:
                case SLONG:
                case ULONG:
                case POINTER:
                    if (sizeof(nativeType) == 4) {
                        mv.invokevirtual(p(boxedType), "intValue", "()I");
                        convertPrimitive(mv, int.class, unboxedType, nativeType);
                    } else {
                        mv.invokevirtual(p(boxedType), "longValue", "()J");
                        convertPrimitive(mv, long.class, unboxedType, nativeType);
                    }
                    break;

                case SLONG_LONG:
                case ULONG_LONG:
                    mv.invokevirtual(p(boxedType), "longValue", "()J");
                    narrow(mv, long.class, unboxedType);
                    break;

                case FLOAT:
                    mv.invokevirtual(p(boxedType), "floatValue", "()F");
                    break;

                case DOUBLE:
                    mv.invokevirtual(p(boxedType), "doubleValue", "()D");
                    break;
            }


        } else if (Boolean.class.isAssignableFrom(boxedType)) {
            unboxBoolean(mv, Boolean.class, unboxedType);

        } else {
            throw new IllegalArgumentException("unsupported boxed type: " + boxedType);
        }
    }


    static void unboxNumber(final SkinnyMethodAdapter mv, final Class boxedType, final Class nativeType) {

        if (Number.class.isAssignableFrom(boxedType)) {

            if (byte.class == nativeType) {
                mv.invokevirtual(p(boxedType), "byteValue", "()B");

            } else if (short.class == nativeType) {
                mv.invokevirtual(p(boxedType), "shortValue", "()S");

            } else if (int.class == nativeType) {
                mv.invokevirtual(p(boxedType), "intValue", "()I");

            } else if (long.class == nativeType) {
                mv.invokevirtual(p(boxedType), "longValue", "()J");

            } else if (float.class == nativeType) {
                mv.invokevirtual(p(boxedType), "floatValue", "()F");

            } else if (double.class == nativeType) {
                mv.invokevirtual(p(boxedType), "doubleValue", "()D");

            } else {
                throw new IllegalArgumentException("unsupported Number subclass: " + boxedType);
            }

        } else if (Boolean.class.isAssignableFrom(boxedType)) {
            unboxBoolean(mv, Boolean.class, nativeType);

        } else {
            throw new IllegalArgumentException("unsupported boxed type: " + boxedType);
        }
    }

    static void boxValue(AsmBuilder builder, SkinnyMethodAdapter mv, Class boxedType, Class unboxedType) {
        if (boxedType == unboxedType || boxedType.isPrimitive()) {

        } else if (Boolean.class.isAssignableFrom(boxedType)) {
            narrow(mv, unboxedType, boolean.class);
            mv.invokestatic(Boolean.class, "valueOf", Boolean.class, boolean.class);

        } else if (Number.class.isAssignableFrom(boxedType) && boxedType(unboxedType) == boxedType) {
            mv.invokestatic(boxedType, "valueOf", boxedType, unboxedType);

        } else {
            throw new IllegalArgumentException("cannot box value of type " + unboxedType + " to " + boxedType);
        }
    }

    static LocalVariable[] getParameterVariables(ParameterType[] parameterTypes) {
        return getParameterVariables(parameterTypes, false);
    }

    static LocalVariable[] getParameterVariables(ParameterType[] parameterTypes, boolean isStatic) {
        LocalVariable[] lvars = new LocalVariable[parameterTypes.length];
        int lvar = isStatic ? 0 : 1;
        for (int i = 0; i < parameterTypes.length; i++) {
            lvars[i] = new LocalVariable(parameterTypes[i].getDeclaredType(), lvar);
            lvar += calculateLocalVariableSpace(parameterTypes[i]);
        }

        return lvars;
    }

    static void load(SkinnyMethodAdapter mv, Class parameterType, LocalVariable parameter) {
        if (!parameterType.isPrimitive()) {
            mv.aload(parameter);

        } else if (long.class == parameterType) {
            mv.lload(parameter);

        } else if (float.class == parameterType) {
            mv.fload(parameter);

        } else if (double.class == parameterType) {
            mv.dload(parameter);

        } else {
            mv.iload(parameter);
        }

    }


    static void store(SkinnyMethodAdapter mv, Class type, LocalVariable var) {
        if (!type.isPrimitive()) {
            mv.astore(var);

        } else if (long.class == type) {
            mv.lstore(var);

        } else if (double.class == type) {
            mv.dstore(var);

        } else if (float.class == type) {
            mv.fstore(var);

        } else {
            mv.istore(var);
        }
    }

    static void emitReturn(AsmBuilder builder, SkinnyMethodAdapter mv, Class returnType, Class nativeIntType) {
        if (returnType.isPrimitive()) {

            if (long.class == returnType) {
                mv.lreturn();

            } else if (float.class == returnType) {
                mv.freturn();

            } else if (double.class == returnType) {
                mv.dreturn();

            } else if (void.class == returnType) {
                mv.voidreturn();

            } else {
                mv.ireturn();
            }

        } else {
            boxValue(builder, mv, returnType, nativeIntType);
            mv.areturn();
        }
    }

    static void tryfinally(SkinnyMethodAdapter mv, Runnable codeBlock, Runnable finallyBlock) {
        Label before = new Label(), after = new Label(), ensure = new Label(), done = new Label();
        mv.trycatch(before, after, ensure, null);
        mv.label(before);
        codeBlock.run();
        mv.label(after);
        if (finallyBlock != null) finallyBlock.run();
        mv.go_to(done);
        if (finallyBlock != null) {
            mv.label(ensure);
            finallyBlock.run();
            mv.athrow();
        }
        mv.label(done);
    }

    static void emitFromNativeConversion(AsmBuilder builder, SkinnyMethodAdapter mv, ResultType fromNativeType, Class nativeClass) {
        // If there is a result converter, retrieve it and put on the stack
        MethodHandle fromNativeConverter = fromNativeType.getFromNativeConverter();
        if (fromNativeConverter != null) {
            convertPrimitive(mv, nativeClass, fromNativeConverter.type().parameterType(0), fromNativeType.getNativeType());
            AsmBuilder.ObjectField fromNativeConverterField = builder.getObjectField(fromNativeConverter);
            mv.getstatic(builder.getClassNamePath(), fromNativeConverterField.name, ci(fromNativeConverterField.klass));
            mv.swap();
            mv.invokevirtual(MethodHandle.class, "invokeExact", fromNativeConverter.type().returnType(), fromNativeConverter.type().parameterType(0));

        } else if (!fromNativeType.getDeclaredType().isPrimitive()) {
            Class unboxedType = unboxedType(fromNativeType.getDeclaredType());
            convertPrimitive(mv, nativeClass, unboxedType, fromNativeType.getNativeType());
            boxValue(builder, mv, fromNativeType.getDeclaredType(), unboxedType);

        }
    }
}
