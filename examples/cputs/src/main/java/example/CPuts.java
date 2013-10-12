package example;

import com.kenai.jffi.Platform;
import jnr.invoke.Signature;
import jnr.invoke.CallingConvention;
import jnr.invoke.DataDirection;
import jnr.invoke.Library;
import jnr.invoke.Native;
import jnr.invoke.NativeType;
import jnr.invoke.ParameterType;
import jnr.invoke.ResultType;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.nio.ByteBuffer;

public class CPuts {
    static {
        System.setProperty("jnr.invoke.compile.dump", "true");
    }

    public static void main(String[] args) throws Throwable {

        Signature arrayContext = Signature.getSignature(ResultType.primitive(NativeType.SINT, int.class),
                new ParameterType[]{ParameterType.array(byte[].class, DataDirection.IN)}, CallingConvention.DEFAULT, true);

        Library libc = Library.open(Platform.getPlatform().mapLibraryName("c"), Library.LAZY | Library.LOCAL);

        MethodHandle mh = Native.getMethodHandle(arrayContext, libc.getFunction("puts"));
        mh.invoke("Hello, World [byte array param]".getBytes());

        try {
            MethodHandle getBytes = MethodHandles.lookup().findVirtual(String.class, "getBytes", MethodType.methodType(byte[].class));
            MethodHandle cputs = MethodHandles.filterArguments(mh, 0, getBytes);
            cputs.invoke("Hello, World [String param]");
        } catch (NoSuchMethodException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }

        // Now try it using both direct and heap ByteBuffers
        Signature bufferContext = Signature.getSignature(ResultType.primitive(NativeType.SINT, int.class),
                new ParameterType[]{ParameterType.buffer(ByteBuffer.class, DataDirection.IN)}, CallingConvention.DEFAULT, true);
        MethodHandle bufferPuts = Native.getMethodHandle(bufferContext, libc.getFunction("puts"));
        bufferPuts.invoke(ByteBuffer.wrap("heap ByteBuffer output".getBytes()));

        ByteBuffer directBuffer = ByteBuffer.allocateDirect(1024);
        directBuffer.put("direct ByteBuffer output".getBytes());
        directBuffer.flip();
        bufferPuts.invoke(directBuffer);

    }
}
