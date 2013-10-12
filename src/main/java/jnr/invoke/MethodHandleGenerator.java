package jnr.invoke;

import java.lang.invoke.MethodHandle;
import java.util.Collection;

public interface MethodHandleGenerator {
    MethodHandle createBoundHandle(Signature signature, CodeAddress function);
    boolean isSupported(ResultType resultType, Collection<ParameterType> parameterTypes, CallingConvention callingConvention);
}
