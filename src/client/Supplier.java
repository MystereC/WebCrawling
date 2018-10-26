package client;

public interface Supplier<E extends Exception, T> {
	public T get() throws E;
}
