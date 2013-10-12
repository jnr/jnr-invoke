package jnr.invoke;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.Collection;
import java.util.List;

import static jnr.invoke.CodegenUtils.params;
import static jnr.invoke.Util.*;

public class DirectCheckMethodHandleGenerator implements MethodHandleGenerator {
    private final MethodHandleGenerator[] primitiveGenerators = {
            new PrimitiveX86MethodHandleGenerator(),
            new PrimitiveNumericMethodHandleGenerator(),
    };

    @Override
    public MethodHandle createBoundHandle(Signature signature, CodeAddress function) {
        return MethodHandles.guardWithTest(createDirectCheckHandle(signature.parameterTypeArray()),
                getPrimitiveHandle(signature, function),
                new DefaultMethodHandleGenerator().createBoundHandle(signature, function));
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

    private MethodHandle getPrimitiveHandle(Signature signature, CodeAddress nativeAddress) {
        Signature primitiveContext = signature.asPrimitiveContext();

        MethodHandle primitiveHandle = createPrimitiveMethodHandle(primitiveGenerators, primitiveContext, nativeAddress);
        for (int i = 0; i < signature.getParameterCount(); i++) {
            if (signature.getParameterType(i).getDirectAddressHandle() != null) {
                primitiveHandle = MethodHandles.filterArguments(primitiveHandle, i, signature.getParameterType(i).getDirectAddressHandle());
            }
        }

        return primitiveHandle;
    }

    private static MethodHandle createPrimitiveMethodHandle(MethodHandleGenerator[] generators, Signature signature, CodeAddress nativeAddress) {
        for (MethodHandleGenerator g : generators) {
            if (g.isSupported(signature.getResultType(), signature.parameterTypeList(), signature.getCallingConvention())) {
                return g.createBoundHandle(signature, nativeAddress);
            }
        }

        throw new RuntimeException("internal error");
    }


    private static MethodHandle createDirectCheckHandle(ParameterType[] parameterTypes) {
        MethodHandle isTrue = findStatic(AsmRuntime.class, "isTrue", MethodType.methodType(boolean.class, params(boolean.class, countObjects(parameterTypes))));
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
