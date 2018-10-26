package client;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

public class TestServer {
	public static void main(String[] args) throws IOException {
		ServerSocketChannel serverSocket = ServerSocketChannel.open();
		serverSocket.configureBlocking(true);
		serverSocket.bind(new InetSocketAddress("localhost", 8888));
		SocketChannel clientSocket = serverSocket.accept();
		clientSocket.configureBlocking(true);
		CharBuffer clear = CharBuffer.wrap("www.meteofrance.com/accueil");
		Charset charset = StandardCharsets.UTF_8;
		ByteBuffer encoded = charset.encode(clear);
		clientSocket.write(encoded);
		encoded = ByteBuffer.allocate(10000000);
		while (clientSocket.read(encoded) >0) {
			encoded.rewind();
			System.out.print(charset.decode(encoded).toString().split("\0")[0]);
		}
	}
}