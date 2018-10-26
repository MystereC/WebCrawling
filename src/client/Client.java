package client;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.channels.SocketChannel;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

public class Client {
	private static final int inputSize = 512;
	private static final Charset charset = StandardCharsets.UTF_8;
	SocketChannel server;
	ClientTask task;
	ByteBuffer inputBuffer;
	ByteBuffer outputBuffer;
	CharBuffer clear;

	private Client() {
		inputBuffer = ByteBuffer.allocate(inputSize);
		task = new ClientTask();
	}

	public void connect(String host, int port) throws IOException {
		server = SocketChannel.open();
		SocketAddress serverAddress = new InetSocketAddress(host, port);
		server.connect(serverAddress);
		server.configureBlocking(true);
		while (server.isConnected()) {
			work();
		}
	}

	private void work() throws IOException {
		inputBuffer.clear();
		int read = server.read(inputBuffer);
		if (read > 0) {
			inputBuffer.rewind();
			clear = charset.decode(inputBuffer);
			int slashIndex = clear.toString().indexOf('/');
			String url = clear.toString().split("\0")[0], host, path;
			if (slashIndex != -1) {
				host = url.substring(0, slashIndex);
				path = url.substring(slashIndex);
			} else {
				host = url;
				path = "";
			}
			clear = CharBuffer.wrap(task.apply(host, path));
			outputBuffer = charset.encode(clear);
			outputBuffer.rewind();
			while (outputBuffer.remaining()>0)
				server.write(outputBuffer);
		} else
			server.close();
	}

	public static void main(String[] args) throws IOException {
		new Client().connect("localhost", 8088);
	}

}
