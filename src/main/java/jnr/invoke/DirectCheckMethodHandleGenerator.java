package jnr.invoke;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import static jnr.invoke.Util.*;

public class DirectCheckMethodHandleGenerator implements MethodHandleGenerator {
    private final MethodHandleGenerator[] primitiveGenerators = {
            new PrimitiveX86MethodHandleGenerator(),
            new PrimitiveNumericMethodHandleGenerator(),
    };

    @Override
    public MethodHandle createBoundHandle(CallContext callContext, CodeAddress function) {
        return MethodHandles.guardWithTest(createDirectCheckHandle(callContext.parameterTypeArray()),
                getPrimitiveHandle(callContext, function),
                new DefaultMethodHandleGenerator().createBoundHandle(callContext, function));
    }


    @Override
    public boolean isSupported(ResultType resultType, Collection<ParameterType> parameterTypes, CallingConvention callingConvention) {
        boolean isSupported = false;
        List<ParameterType> primitiveParameterTypes = asPrimitiveTypes(parameterTypes);
        for (MethodHandleGenerator g : primitiveGenerators) {
            isSupported |= g.isSupported(resultType, primitiveParameterTypes, callingConvention);
        }

        return isSupported && parameterTypes.size() <= 6;
    }

    private MethodHandle getPrimitiveHandle(CallContext callContext, CodeAddress nativeAddress) {
        CallContext primitiveContext = callContext.asPrimitiveContext();

        MethodHandle primitiveHandle = createPrimitiveMethodHandle(primitiveGenerators, primitiveContext, nativeAddress);
        for (int i = 0; i < callContext.getParameterCount(); i++) {
            if (callContext.getParameterType(i).getDirectAddressHandle() != null) {
                primitiveHandle = MethodHandles.filterArguments(primitiveHandle, i, callContext.getParameterType(i).getDirectAddressHandle());
            }
        }

        return primitiveHandle;
    }

    private static MethodHandle createPrimitiveMethodHandle(MethodHandleGenerator[] generators, CallContext callContext, CodeAddress nativeAddress) {
        for (MethodHandleGenerator g : generators) {
            if (g.isSupported(callContext.getResultType(), callContext.parameterTypeList(), callContext.getCallingConvention())) {
                return g.createBoundHandle(callContext, nativeAddress);
            }
        }

        throw new RuntimeException("internal error");
    }


    private static MethodHandle createDirectCheckHandle(ParameterType[] parameterTypes) {
        int objectCount = 0;
        for (ParameterType p : parameterTypes) {
            if (p.isObject()) {
                objectCount++;
            }
        }

        Class[] boolparams = new Class[objectCount];
        Arrays.fill(boolparams, boolean.class);
        MethodHandle isTrue = findStatic(AsmRuntime.class, "isTrue", MethodType.methodType(boolean.class, boolparams));
        for (int i = 0; i < parameterTypes.length; i++) {
            if (parameterTypes[i].getDirectCheckHandle() == null) {
                isTrue = MethodHandles.dropArguments(isTrue, i, parameterTypes[i].javaType());
            } else {
                isTrue = MethodHandles.filterArguments(isTrue, i, parameterTypes[i].getDirectCheckHandle());
            }
        }

        return isTrue;
    }
}
