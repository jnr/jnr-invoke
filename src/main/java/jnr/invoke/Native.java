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

import com.kenai.jffi.Function;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;

import java.io.PrintWriter;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Constructor;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import static jnr.invoke.CodegenUtils.p;
import static jnr.invoke.CodegenUtils.sig;
import static org.objectweb.asm.Opcodes.*;

public final class Native {
    public final static boolean DEBUG = Boolean.getBoolean("jnr.invoke.compile.dump");
    private static final AtomicLong nextClassID = new AtomicLong(0);

    private Native() {
    }

    public static MethodHandle getMethodHandle(Symbol nativeAddress, CallContext context) {
        AsmClassLoader classLoader = new AsmClassLoader(Native.class.getClassLoader());
        StubCompiler compiler = StubCompiler.newCompiler();
        final MethodGenerator[] generators = {
                new X86MethodGenerator(compiler),
                new FastIntMethodGenerator(),
                new FastLongMethodGenerator(),
                new FastNumericMethodGenerator(),
                new BufferMethodGenerator()
        };

        boolean debug = DEBUG;
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
        ClassVisitor cv = debug ? AsmUtil.newCheckClassAdapter(cw) : cw;

        AsmBuilder builder = new AsmBuilder(p(Native.class) + "$jnr$ffi$" + nextClassID.getAndIncrement(), cv, classLoader);

        cv.visit(V1_7, ACC_PUBLIC | ACC_FINAL, builder.getClassNamePath(), null, p(java.lang.Object.class), new String[0]);
        Function jffiFunction = new Function(nativeAddress.address(), context.getNativeCallContext());
        ResultType resultType = context.getResultType().asPrimitiveType();
        ParameterType[] parameterTypes = new ParameterType[context.getParameterCount()];
        for (int i = 0; i < parameterTypes.length; i++) {
            parameterTypes[i] = context.getParameterType(i).asPrimitiveType();
        }

        for (MethodGenerator g : generators) {
            if (g.isSupported(resultType, parameterTypes, context.getCallingConvention())) {
                g.generate(builder, "invokeNative", jffiFunction, resultType, parameterTypes,
                        !context.saveErrno);
                break;
            }
        }

        // Stash a strong ref to reference to the library, so it doesn't get garbage collected.
        builder.getObjectField(nativeAddress.getLibrary(), nativeAddress.getLibrary().getClass());

        // Create the constructor to set the instance fields
        {
            SkinnyMethodAdapter init = new SkinnyMethodAdapter(cv, ACC_PUBLIC, "<init>", sig(void.class), null, null);
            init.start();
            init.aload(0);
            init.invokespecial(p(java.lang.Object.class), "<init>", sig(void.class));
            init.voidreturn();
            init.visitMaxs(10, 10);
            init.visitEnd();
        }

        // Create the static class initializer to set the instance fields
        Map<String, Object> fields = builder.getObjectFieldMap();
        if (!fields.isEmpty()) {
            SkinnyMethodAdapter clinit = new SkinnyMethodAdapter(cv, ACC_PUBLIC | ACC_STATIC, "<clinit>", sig(void.class), null, null);
            clinit.start();
            AsmRuntime.setStaticClassData(builder.getClassNamePath(), fields);
            builder.emitStaticFieldInitialization(clinit, builder.getClassNamePath());
            clinit.voidreturn();
            clinit.visitMaxs(10, 10);
            clinit.visitEnd();
        }
        cv.visitEnd();


        try {
            byte[] bytes = cw.toByteArray();
            if (debug) {
                ClassVisitor trace = AsmUtil.newTraceClassVisitor(new PrintWriter(System.err));
                new ClassReader(bytes).accept(trace, 0);
            }

            Class implClass = classLoader.defineClass(builder.getClassNamePath().replace("/", "."), bytes);
            implClass.newInstance();

            // Attach any native method stubs - we have to delay this until the
            // implementation class is loaded for it to work.
            System.err.flush();
            System.out.flush();
            compiler.attach(implClass);

            Class[] ptypes = new Class[context.getParameterCount()];
            for (int i = 0; i < ptypes.length; i++) {
                ptypes[i] = parameterTypes[i].getDeclaredType();
            }

            MethodType methodType = MethodType.methodType(resultType.getDeclaredType(), ptypes);

            MethodHandle mh = MethodHandles.lookup().findStatic(implClass, "invokeNative", methodType);

            // Convert from the primitive result value to a boxed type if needed.
            if (!resultType.equals(context.getResultType())) {
                mh = MethodHandles.filterReturnValue(mh, NumberUtil.getBoxingHandle(resultType.getDeclaredType()));
            }

            // If needed, convert any boxed parameters to their corresponding primitive values before calling
            for (int i = 0; i < parameterTypes.length; i++) {
                if (!parameterTypes[i].equals(context.getParameterType(i))) {
                    mh = MethodHandles.filterArguments(mh, i, NumberUtil.getUnboxingHandle(context.getParameterType(i).getDeclaredType()));
                }
            }

            return mh;

        } catch (Throwable ex) {
            throw new RuntimeException(ex);
        }
    }
}
