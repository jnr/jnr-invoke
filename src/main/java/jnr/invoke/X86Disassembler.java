/*
 * Copyright (C) 2012-2013 Wayne Meissner
 *
 * This file is part of the JNR project.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package jnr.invoke;

import com.kenai.jffi.CallContext;
import com.kenai.jffi.Library;
import com.kenai.jffi.MemoryIO;
import com.kenai.jffi.Type;

import java.io.File;
import java.nio.charset.Charset;
import java.util.Arrays;

/**
 *
 */
class X86Disassembler {

    public enum Syntax { INTEL, ATT }

    public enum Mode { I386, X86_64 }

    private final UDis86 udis86;
    final long ud;


    static final class SingletonHolder {
        static final UDis86 INSTANCE;
        static {
            Library lib;
            UDis86 udis86 = new UDis86();
            for (String path : new String[] { "/usr/local/lib", "/opt/local/lib", "/usr/lib" }) {
                lib = Library.getCachedInstance(new File(path, System.mapLibraryName("udis86")).getAbsolutePath(),
                        Library.LOCAL | Library.NOW);
                if (lib != null) {
                    udis86.ud_init = function(lib, "ud_init", Type.VOID, Type.POINTER);
                    udis86.ud_set_mode = function(lib, "ud_set_mode", Type.VOID, Type.POINTER, Type.SCHAR);
                    udis86.ud_set_input_buffer = function(lib, "ud_set_input_buffer", Type.VOID, Type.POINTER, Type.POINTER, Type.SLONG);
                    udis86.ud_set_syntax = function(lib, "ud_set_syntax", Type.VOID, Type.POINTER, Type.POINTER);
                    udis86.ud_disassemble = function(lib, "ud_disassemble", Type.SINT, Type.POINTER);
                    udis86.ud_insn_asm = function(lib, "ud_insn_asm", Type.POINTER, Type.POINTER);
                    udis86.ud_insn_off = function(lib, "ud_insn_off", Type.SLONG_LONG, Type.POINTER);
                    udis86.intel = lib.getSymbolAddress("ud_translate_intel");
                    udis86.att = lib.getSymbolAddress("ud_translate_att");
                    break;
                }
            }
            INSTANCE = udis86;
        }

        private static Function function(Library library, String name, Type resultType, Type... parameterTypes) {
            long address = library.getSymbolAddress(name);
            if (address == 0L) {
                throw new UnsatisfiedLinkError("cannot find symbol " + name);
            }
            CallContext callContext = com.kenai.jffi.CallContext.getCallContext(resultType, parameterTypes, com.kenai.jffi.CallingConvention.DEFAULT, true);
            return new Function(address, callContext);
        }
    }

    static boolean isAvailable() {
        try {
            return SingletonHolder.INSTANCE != null;
        } catch (Throwable ex) {
            return false;
        }
    }

    static X86Disassembler create() {
        return new X86Disassembler(SingletonHolder.INSTANCE);
    }

    private X86Disassembler(UDis86 udis86) {
        this.udis86 = udis86;
        this.ud = MemoryIO.getInstance().allocateMemory(1024, true);
        this.udis86.ud_init.invoke(ud);
        this.udis86.ud_set_syntax.invoke(ud, udis86.intel);
    }


    public void setMode(Mode mode) {
        udis86.ud_set_mode.invoke(ud, mode == Mode.I386 ? 32 : 64);
    }

    public void setInputBuffer(long buffer, int size) {
        udis86.ud_set_input_buffer.invoke(ud, buffer, size);
    }

    public boolean disassemble() {
        return (udis86.ud_disassemble.invoke(ud) & 0xff) != 0;
    }

    public String insn() {
        long ptr = udis86.ud_insn_asm.invoke(ud);
        return ptr != 0L
            ? new String(MemoryIO.getInstance().getZeroTerminatedByteArray(ptr), Charset.defaultCharset())
            : null;
    }

    public long offset() {
        return udis86.ud_insn_off.invoke(ud);
    }

    private static final class Function {
        com.kenai.jffi.CallContext callContext;
        long address;

        private Function(long address, com.kenai.jffi.CallContext callContext) {
            this.callContext = callContext;
            this.address = address;
        }

        private long invoke(long... params) {
            if (callContext.getParameterCount() != params.length) {
                throw new IllegalArgumentException(String.format("incorrect arity, (%d for %d)",
                        params.length, callContext.getParameterCount()));
            }
            switch (callContext.getParameterCount()) {
                case 0:
                    return com.kenai.jffi.Invoker.getInstance().invokeN0(callContext, address);
                case 1:
                    return com.kenai.jffi.Invoker.getInstance().invokeN1(callContext, address, params[0]);
                case 2:
                    return com.kenai.jffi.Invoker.getInstance().invokeN2(callContext, address, params[0], params[1]);
                case 3:
                    return com.kenai.jffi.Invoker.getInstance().invokeN3(callContext, address, params[0], params[1], params[2]);
            }
            throw new RuntimeException("unsupported function arity: " + callContext.getParameterCount());
        }

    }
    public static final class UDis86 {
        Function ud_init;     // (X86Disassembler ud)
        Function ud_set_mode; // (X86Disassembler ud, @u_int8_t int mode);
        Function ud_set_input_buffer; // (X86Disassembler ud, Pointer data, @size_t long len);
        Function ud_set_syntax; // (X86Disassembler ud, @intptr_t long translator);
        Function ud_disassemble; // (X86Disassembler ud);
        Function ud_insn_asm; // (X86Disassembler ud);
        Function ud_insn_off; // (X86Disassembler ud);
        long intel;
        long att;
    }
}
