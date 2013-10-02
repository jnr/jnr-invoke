package example;

import com.kenai.jffi.Platform;
import jnr.invoke.*;

import java.lang.invoke.MethodHandle;

public class Getpid {
    static {
        System.setProperty("jnr.invoke.compile.dump", "true");
    }

    public static void main(String[] args) throws Throwable {

        CallContext callContext = CallContext.getCallContext(ResultType.primitive(NativeType.ULONG, long.class),
                new ParameterType[0], CallingConvention.DEFAULT, false);

        Library libc = Library.open(Platform.getPlatform().mapLibraryName("c"), Library.LAZY | Library.LOCAL);

        MethodHandle mh = Native.getMethodHandle(callContext, libc.getFunction("getpid"));
        System.out.println("pid = " + (long) mh.invokeExact());
    }
}
