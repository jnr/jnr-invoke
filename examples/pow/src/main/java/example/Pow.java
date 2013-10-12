package example;

import com.kenai.jffi.Platform;
import jnr.invoke.*;

import java.lang.invoke.MethodHandle;

public class Pow {
    static {
        System.setProperty("jnr.invoke.compile.dump", "true");
    }

    public static void main(String[] args) throws Throwable {

        Signature signature = Signature.getSignature(ResultType.primitive(NativeType.DOUBLE, double.class),
                new ParameterType[]{
                        ParameterType.primitive(NativeType.DOUBLE, double.class),
                        ParameterType.primitive(NativeType.DOUBLE, double.class)
                },
                CallingConvention.DEFAULT, false);

        Library libm = Library.open(Platform.getPlatform().mapLibraryName("m"), Library.LAZY | Library.LOCAL);

        MethodHandle mh = Native.getMethodHandle(signature, libm.getFunction("pow"));
        System.out.println("pow = " + (double) mh.invokeExact((double) 2.0d, 3.0d));
    }
}
