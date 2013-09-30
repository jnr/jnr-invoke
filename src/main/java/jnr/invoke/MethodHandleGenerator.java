package jnr.invoke;

import java.lang.invoke.MethodHandle;
import java.util.Collection;

public interface MethodHandleGenerator {
    MethodHandle createBoundHandle(CallContext callContext, CodeAddress function);
    boolean isSupported(ResultType resultType, Collection<ParameterType> parameterTypes, CallingConvention callingConvention);
}
