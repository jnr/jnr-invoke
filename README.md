jnr-invoke [![Build Status](https://travis-ci.org/jnr/jnr-invoke.png)](https://travis-ci.org/jnr/jnr-invoke)
======

[jnr-invoke](https://github.com/jnr/jnr-invoke) is a java library for loading native libraries without writing JNI code by hand, or using tools such as SWIG.

Example
------

    import com.kenai.jffi.Platform;
    import jnr.invoke.*;
    
    import java.lang.invoke.MethodHandle;
    
    public class Getpid {
        public static void main(String[] args) throws Throwable {
            
            // Create a function signature for long getpid()
            Signature signature = Signature.getSignature(ResultType.primitive(NativeType.ULONG, long.class),
                    new ParameterType[0], Signature.CDECL);
                                
            Library libc = Library.open(Platform.getPlatform().mapLibraryName("c"), Library.LAZY | Library.LOCAL);
    
            MethodHandle mh = Native.getMethodHandle(libc.findSymbol("getpid"), signature);
            System.out.println("pid = " + mh.invoke());
        }
    }
