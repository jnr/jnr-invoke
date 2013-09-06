package jnr.invoke;

public class Function {
    private final long address;

    public Function(long address) {
        this.address = address;
    }

    public final long address() {
        return address;
    }
}
