package jnr.invoke;

/**
 *
 */
final class NullObjectParameterStrategy extends ObjectParameterStrategy {
    static final ObjectParameterStrategy NULL = new NullObjectParameterStrategy();

    NullObjectParameterStrategy() {
        super(DIRECT);
    }

    @Override
    public long address(Object parameter) {
        return 0;
    }

    @Override
    public Object object(Object parameter) {
        throw new NullPointerException("null reference");
    }

    @Override
    public int offset(Object parameter) {
        throw new NullPointerException("null reference");
    }

    @Override
    public int length(Object parameter) {
        throw new NullPointerException("null reference");
    }
}
