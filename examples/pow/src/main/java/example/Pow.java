package example;

import com.kenai.jffi.Platform;
import jnr.invoke.*;

import java.lang.invoke.MethodHandle;

public class Pow {
    static {
        System.setProperty("jnr.invoke.compile.dump", "true");
    }

    public static void main(String[] args) throws Throwable {

        CallContext callContext = CallContext.getCallContext(ResultType.primitive(NativeType.DOUBLE, Double.class),
                new ParameterType[] { ParameterType.primitive(NativeType.DOUBLE, Double.class), ParameterType.primitive(NativeType.DOUBLE, double.class) },
                CallingConvention.DEFAULT, false);

        Library libm = Library.open(Platform.getPlatform().mapLibraryName("m"), Library.LAZY | Library.LOCAL);

        MethodHandle mh = Native.getMethodHandle(libm.findSymbol("pow"), callContext);
        System.out.println("pow = " + (Double) mh.invokeExact((Double) 2.0d, 3.0d));
    }
}
