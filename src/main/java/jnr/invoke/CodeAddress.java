package jnr.invoke;

public class CodeAddress {

    protected final long address;

    public CodeAddress(long address) {
        this.address = address;
    }

    public final long address() {
        return address;
    }
}
