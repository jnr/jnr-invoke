package jnr.invoke;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;

import static jnr.invoke.AsmUtil.*;
import static jnr.invoke.NumberUtil.narrow;
import static jnr.invoke.NumberUtil.widen;

/**
 * Emits appropriate asm code to convert the parameter to a native value
 */
abstract class ToNativeOp {
    private final boolean isPrimitive;

    protected ToNativeOp(boolean primitive) {
        isPrimitive = primitive;
    }

    final boolean isPrimitive() {
        return isPrimitive;
    }

    abstract void emitPrimitive(SkinnyMethodAdapter mv, Class primitiveClass, NativeType nativeType);

    private static final Map<Class, ToNativeOp> operations;
    static {
        Map<Class, ToNativeOp> m = new IdentityHashMap<Class, ToNativeOp>();
        for (Class c : new Class[] { byte.class, char.class, short.class, int.class, long.class, boolean.class }) {
            m.put(c, new Integral(c));
            m.put(boxedType(c), new Integral(boxedType(c)));
        }
        m.put(float.class, new Float32(float.class));
        m.put(Float.class, new Float32(Float.class));
        m.put(double.class, new Float64(float.class));
        m.put(Double.class, new Float64(Float.class));

        operations = Collections.unmodifiableMap(m);
    }

    static ToNativeOp get(ParameterType type) {
        ToNativeOp op = operations.get(type.effectiveJavaType());
        if (op != null) {
            return op;

        } else {
          return null;
        }
    }

    static abstract class Primitive extends ToNativeOp {
        protected final Class javaType;
        protected Primitive(Class javaType) {
            super(true);
            this.javaType = javaType;
        }
    }


    static class Integral extends Primitive {
        Integral(Class javaType) {
            super(javaType);
        }

        @Override
        public void emitPrimitive(SkinnyMethodAdapter mv, Class primitiveClass, NativeType nativeType) {
            if (javaType.isPrimitive()) {
                NumberUtil.convertPrimitive(mv, javaType, primitiveClass, nativeType);
            } else {
                unboxNumber(mv, javaType, primitiveClass, nativeType);
            }
        }
    }

    static class Float32 extends Primitive {
        Float32(Class javaType) {
            super(javaType);
        }

        @Override
        void emitPrimitive(SkinnyMethodAdapter mv, Class primitiveClass, NativeType nativeType) {
            if (!javaType.isPrimitive()) {
                unboxNumber(mv, javaType, float.class);
            }
            if (primitiveClass != float.class) {
                mv.invokestatic(Float.class, "floatToRawIntBits", int.class, float.class);
                widen(mv, int.class, primitiveClass);
            }
        }
    }

    static class Float64 extends Primitive {
        Float64(Class javaType) {
            super(javaType);
        }

        @Override
        void emitPrimitive(SkinnyMethodAdapter mv, Class primitiveClass, NativeType nativeType) {
            if (!javaType.isPrimitive()) {
                unboxNumber(mv, javaType, double.class);
            }
            if (primitiveClass != double.class) {
                mv.invokestatic(Double.class, "doubleToRawLongBits", long.class, double.class);
                narrow(mv, long.class, primitiveClass);
            }
        }
    }
}
