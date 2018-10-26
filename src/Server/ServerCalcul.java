package Server;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ServerCalcul implements Runnable{
	private static final Charset charset = StandardCharsets.UTF_8;
	private static final int PORT = 8088;
	private ServerSocketChannel clientServerSocket;
	private Selector clientSelector;
	private ByteBuffer buffer;
	private HashMap<String, Set<String>> index;
	private ArrayList<String> NewUrl;
	private ArrayList<String> urlVisited;
	public String tagetUrl;
	SocketChannel sc ;

	public String getTagetUrl() {
		return tagetUrl;
	}

	public void setTagetUrl(String tagetUrl) {
		this.tagetUrl = tagetUrl;
	}
	
	ServerCalcul() throws IOException {
		clientServerSocket = ServerSocketChannel.open();
		clientServerSocket.configureBlocking(false);
		clientServerSocket.bind(new InetSocketAddress(PORT));
		clientSelector = Selector.open();
		buffer = ByteBuffer.allocateDirect(512);
		clientServerSocket.register(clientSelector, SelectionKey.OP_ACCEPT);
		index = new HashMap<String, Set<String>>();
		
	}

	void acceptClient() throws IOException {
		sc = clientServerSocket.accept();
		if (sc == null) {
			System.out.println("Rien � accepter");
			return;
		}
		sc.configureBlocking(false);
		sc.register(clientSelector, SelectionKey.OP_READ);
		System.out.println("accept:" + sc);
	}

	void repeatClient(SelectionKey sk) throws IOException {
		sc = (SocketChannel) sk.channel();
		buffer.clear();
		if (sc.read(buffer) == -1) {
			System.out.println("connection" + sc + " closed");
			sk.cancel();
			sc.close();
			return;
		}
		buffer.flip();
		System.out.println(tagetUrl);
		sendURLToCli("www.meteofrance.com/accueil");
		
		
		//sendURLToCli("bonjou", sk);
		//NewUrl.add(tagetUrl);
		//ParcoursGraphe(sk,NewUrl ,urlVisited);
	}

	public void sendURLToCli(String url) throws IOException {
		
		ByteBuffer b = charset.encode(url);
		buffer.putInt(b.remaining());
		buffer.put(b);
		buffer.flip();
		sc.write(buffer);
	}

	public void run() {
		while (true) {
			
			try {
				clientSelector.select();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			for (SelectionKey sk : clientSelector.selectedKeys()) {
				if (sk.isAcceptable()) {
					try {
						acceptClient();
						System.out.println("url bien recu par le client  de calcul "+tagetUrl);
						sendURLToCli(tagetUrl);
						
					} catch (IOException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				} else if (sk.isReadable()) {
					try {
						repeatClient(sk);
					} catch (IOException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				}
			}
			clientSelector.selectedKeys().clear();
		}
	}

	public void ajouterIndex(Map<String, Set<String>> indMot, String mot, String url) {
		if (!indMot.containsKey(mot)) {
			indMot.put(mot, new HashSet<String>());
		}
		indMot.get(mot).add(url);
	}

	public Set<String> trouverList(Map<String, Set<String>> indMot, String mot) {
		return indMot.get(mot);
	}

	synchronized void ParcoursGraphe(ArrayList<String> NewUrl, ArrayList<String> urlVisited) throws IOException {
	
		
		int size = NewUrl.size();
		for (int i = 0; i < size; i++) {
			String url = NewUrl.get(i);
			sendURLToCli(url); // envoie au client l'url a traiter
			// String[] tabMot = GetMot(sk); // table de mot
			urlVisited.add(url); // on ajoute l'url dans la liste d'url visit�
			NewUrl.remove(i); // on l'enleve de la liste des url a a traiter
			sc.read(buffer); // on lis le buffer
			int length = buffer.getInt(); // on recupere la taille du bloc
			int lengthMot = buffer.getInt(); // on recupere la taille des mot
			int limit = buffer.limit();
			buffer.limit(buffer.position() + lengthMot);
			String s = charset.decode(buffer).toString(); // on recupere l ensemble de mot
			int lengthUrl = buffer.getInt(); // on recupere la taille des url
			buffer.limit(buffer.position() + lengthUrl);
			String s2 = charset.decode(buffer).toString(); // on recupere l ensemble de url
			buffer.limit();
			String[] valueMot = s.split(" "); // on enleve les espace des mot
			String[] valuUrl = s.split(" "); // on enleve les espace des urls
			List<String> mot = new ArrayList<String>();
			List<String> TempUrl = new ArrayList<String>();
			for (int x = 0; x < lengthMot; x++) {
				mot.add(valueMot[x]);
				ajouterIndex(index, mot.get(x), NewUrl.get(x)); // ajout dans l idenx des mot
			}
			for (int j = lengthMot; j < lengthUrl; j++) {
				TempUrl.add(valuUrl[j]);
				if (!urlVisited.contains(TempUrl.get(j))) {
					NewUrl.add(TempUrl.get(j));
					size = NewUrl.size();
				}
			}
		}
	}

	public String getFromUrlEncours(ArrayList<String> urlENcours, int position) {
		return urlENcours.get(position);
	}

	public static void main(String[] args) throws IOException {
		ServerCalcul s = new ServerCalcul();
		
		s.run();
		
	}
	
}
