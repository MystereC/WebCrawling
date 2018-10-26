package Server;

import java.io.FileInputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;

public class ServerInterface implements Runnable {
	private static final Charset charset = StandardCharsets.UTF_8;
	private static final int USER_PORT = 8090;
	ServerSocketChannel usersServerSocket;
	Selector userSelector;
	private ByteBuffer buffer;
	private String targetUrl;
	private ArrayList<String> URlist = new ArrayList<String>();
	 ServerCalcul targetServeur;
	private static boolean flag;
   
	
	public ServerInterface() throws IOException {
		usersServerSocket = ServerSocketChannel.open();
		userSelector = Selector.open();
		configure(usersServerSocket,userSelector,USER_PORT);
		buffer = ByteBuffer.allocateDirect(3000);
		targetServeur= new ServerCalcul();
		this.flag = false;
	}

	private void configure(ServerSocketChannel serverSocket,Selector selector,int port) throws IOException {
		serverSocket.configureBlocking(false);
		serverSocket.bind(new InetSocketAddress(port));
		serverSocket.register(selector, SelectionKey.OP_ACCEPT);
	}

	void accept() throws IOException {
		// accepter les connections des utilisateurs
		SocketChannel userSocket = usersServerSocket.accept();
		if (userSocket == null) {
			return;
		}
		userSocket.configureBlocking(false);
		userSocket.register(userSelector, SelectionKey.OP_READ);
	}

	void repeat(SelectionKey sk) throws IOException {
		SocketChannel userSocket = (SocketChannel) sk.channel();
		buffer.clear();
		if (userSocket.read(buffer) == -1) {
			System.out.println("connection " + userSocket + " closed");
			sk.cancel();
			userSocket.close();
			return;
		}
		buffer.flip();
		if (!flag) {
			StringBuffer request = new StringBuffer();
			request.append(charset.decode(buffer));
			String accueilHtml = new String(Files.readAllBytes(Paths.get("src/accueil.html")), charset);
			String accueilHttp = "HTTP/1.1 200 OK\r\nContent-Type: text/html\r\nContent-Length:" + accueilHtml.length()
					+ "\r\n\r\n" + accueilHtml;
			buffer.clear();
			buffer.put(accueilHttp.getBytes());
			buffer.flip();
			userSocket.write(buffer);
			String HttpResponse = request.toString();
			targetUrl = getFormParameter("LienDeLaPage", HttpResponse);
			if (verifURl(targetUrl)) {
				
				ajouterLien(URlist, targetUrl);
				
               targetServeur.setTagetUrl(targetUrl);
				afficherList(URlist);
				flag=true;
              
			}
		} else {
			StringBuffer request2 = new StringBuffer();
			request2.append(charset.decode(buffer));
			String HttpResponse2 = request2.toString();
			/*
			 * FileWriter fw = new FileWriter("src/index.html",true); fw.
			 * write(" <a href=\"#\" target=\"_blank\"> ici </a> pour affihcher</body>\r\n"
			 * + "\r\n" + "</html>") fw.close();;
			 */
			FileInputStream fis2 = new FileInputStream("src/index.html");
			int size2 = fis2.available();
			byte[] b2 = new byte[size2];
			fis2.read(b2);
			String html2 = new String(b2);
			String httpResp2 = "HTTP\1.1 200 OK\r\nContent-Type: text/html\r\nContent-Length:" + html2.length()
					+ "\r\n\r\n" + html2;
			buffer.clear();
			buffer.put(httpResp2.getBytes());
			buffer.flip();
			userSocket.write(buffer);
			
			
		}
	}

	static String unescapeHTTPCharacters(String s) {
		return s.replace("%20", " ").replace("%3C", "<").replace("%3E", ">").replace("%23", "#").replace("%25", "%")
				.replace("%7B", "{").replace("%7D", "}").replace("%7C", "|").replace("%5C", "\\").replace("%5E", "^")
				.replace("%7E", "~").replace("%5B", "[").replace("%5D", "]").replace("%60", "`").replace("%3B", ";")
				.replace("%2F", "/").replace("%3F", "?").replace("%3A", ":").replace("%40", "@").replace("%3D", "=")
				.replace("%26", "&").replace("%24", "$");
	}

	public void run() {
		while (true) {
			try {
				userSelector.select();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} // selection d'une cl�
			for (SelectionKey sk : userSelector.selectedKeys()) {
				if (sk.isAcceptable()) {
					try {
						accept();
					} catch (IOException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				} else if (sk.isReadable()) {
					try {
						repeat(sk);
					} catch (IOException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				}
			}
			userSelector.selectedKeys().clear();
		}
	}

	public static String getFormParameter(String nameFormValue, String s) {
		int begin = s.indexOf(nameFormValue) + nameFormValue.length() + 1;
		String escapedParameter = s.substring(begin).split(" ")[0];
		return unescapeHTTPCharacters(escapedParameter);
	}

	synchronized static void ajouterLien(ArrayList<String> l, String s) {
		if (!l.contains(s)) {
			l.add(s);
			for (int i = 0; i < l.size(); i++) {
				System.out.println("bien ajouté");
			}
		}
	}

	synchronized static boolean verifURl(String s) {
		try {
			new URL(s).toURI();
			return true;
		} catch (URISyntaxException e) {
			
			return false;
		} catch (MalformedURLException e) {
			
			return false;
		}
	}

	synchronized void afficherList(ArrayList<String> l) {
		for (int i = 0; i < l.size(); i++) {
			System.out.println(l.get(i));
			
		}
	}

	public ArrayList<String> getURlist() {
		return URlist;
	}

	public static void main(String[] args) throws IOException, InterruptedException {
		ServerInterface s = new ServerInterface();
		
		
		Thread t= new Thread(s);
		Thread t2= new Thread(s.targetServeur);
		t.start();
		t2.start();
		t.join();
		t2.join();		
	}
}
