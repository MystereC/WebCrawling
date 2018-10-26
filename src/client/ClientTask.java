package client;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.channels.SocketChannel;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.BiFunction;

public class ClientTask implements BiFunction<String, String, String> {
	private class ChunkCharSupplier implements Supplier<IOException, Character> {

		private int remaining = 0;

		@Override
		public Character get() throws IOException {
			if (remaining == 0) {
				remaining = getChunkSize();
				getChunkBody();
			}
			current = body.get();
			remaining--;
			return current;
		}

		private void getChunkBody() throws IOException {
			buffer = ByteBuffer.allocate(remaining);
			while (targetServer.read(buffer) != 0)
				;
			buffer.rewind();
			body = charset.decode(buffer);
			body.rewind();
			setBufferForNextChunk();
		}

		private void setBufferForNextChunk() throws IOException {
			buffer = ByteBuffer.allocate(2);
			buffer.clear();
			targetServer.read(buffer);
		}

		private int getChunkSize() throws IOException {
			buffer = ByteBuffer.allocate(1);
			buffer.clear();
			StringBuilder sb = new StringBuilder();
			targetServer.read(buffer);
			buffer.flip();
			byte got;
			while (((got = buffer.get()) <= '9' && got >= '0') || (got >= 'a' && got <= 'f')) {
				sb.append((char) got);
				buffer.clear();
				targetServer.read(buffer);
				buffer.flip();
			}
			buffer.clear();
			targetServer.read(buffer);
			return Integer.valueOf(sb.toString(), 16);
		}
	}

	private static final char SEPARATOR = '\n';
	private ByteBuffer buffer;
	private SocketChannel targetServer;
	private static final Charset charset = StandardCharsets.UTF_8;
	private String host;
	private String path;
	private CharBuffer body;
	private char current;
	private Set<String> seenWords;
	private Set<String> seenUrls;
	private Supplier<IOException, Character> readMethod;

	@Override
	public String apply(String host, String path) {
		this.host = host;
		this.path = path;
		seenWords = new TreeSet<>();
		seenUrls = new TreeSet<>();
		try {
			targetServer = SocketChannel.open();
			targetServer.configureBlocking(true);
			targetServer.connect(new InetSocketAddress(host, 80));
			sendRequest();
			return treatResponse();
		} catch (IOException e1) {
			e1.printStackTrace();
			return null;
		}
	}

	private void sendRequest() {
		String request = makeRequest();
		buffer = ByteBuffer.allocate(request.length());
		buffer.clear();
		buffer.put(charset.encode(request));
		buffer.flip();
		buffer.rewind();
		try {
			targetServer.write(buffer);
		} catch (IOException e) {
			System.err.println("An error occured sending request to server:" + e.getMessage());
		}
	}

	private String makeRequest() {
		return "GET http://" + host + path + " HTTP/1.1\n" + "Host: " + host + "\n"
				+ "Accept: text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8\n"
				+ "Accept-Language: en-US,en;q=0.5\n"
				// + "Accept-Encoding: gzip, deflate\n" + "Connection: keep-alive\n"
				+ "Upgrade-Insecure-Requests: 1\n" + "Cache-Control: max-age=0\r\n\r\n";
	}

	private String treatResponse() throws IOException {
		treatHeader();
		treatBody();
		StringBuilder sb = new StringBuilder();
		sb.append(seenWords.size());
		for (String seen : seenWords) {
			sb.append(SEPARATOR);
			sb.append(seen);
		}
		sb.append(SEPARATOR);
		sb.append(seenUrls.size());
		for (String seen : seenUrls) {
			sb.append(SEPARATOR);
			sb.append(seen);
		}
		int length = sb.length();
		String begin = "" + length + SEPARATOR;
		sb.insert(0, begin);
		return sb.toString();
	}

	private void treatHeader() throws IOException {
		buffer = ByteBuffer.allocate(17);
		while (targetServer.read(buffer) != 0)
			;
		buffer.flip();
		buffer = ByteBuffer.allocate(1);
		HashMap<String, String> info = new HashMap<>();
		while (getLine(info))
			;
		if (info.containsKey("Content-Length")) {
			buffer = ByteBuffer.allocate(Integer.parseInt(info.get("Content-Length")));
			buffer.clear();
			while (targetServer.read(buffer) != 0)
				;
			buffer.flip();
			body = charset.decode(buffer);
			body.rewind();
			readMethod = () -> {
				current = body.get();
				return current;
			};
		} else if (info.containsKey("Transfer-Encoding")) {
			if (info.get("Transfer-Encoding").equals("chunked")) {
				readMethod = new ChunkCharSupplier();
			}
		}
	}

	private boolean getLine(HashMap<String, String> info) throws IOException {
		String key;
		String value;
		if ((key = getUntil(':')) != null) {
			buffer.clear();
			targetServer.read(buffer);
			buffer.clear();
			if ((value = getUntil('\r')) != null) {
				buffer.clear();
				targetServer.read(buffer);
				buffer.clear();
				info.put(key, value);
				return true;
			}
			throw new IOException("Problem with HTTP header: key found without matching value");
		} else {
			buffer.clear();
			targetServer.read(buffer);
			buffer.clear();
			return false;
		}
	}

	private String getUntil(char limit) throws IOException {
		targetServer.read(buffer);
		buffer.flip();
		char current = charset.decode(buffer).get();
		if (current != '\r') {
			StringBuilder sb = new StringBuilder();
			while (current != limit) {
				sb.append(current);
				buffer.clear();
				targetServer.read(buffer);
				buffer.rewind();
				current = charset.decode(buffer).get();
			}
			return sb.toString();
		} else
			return null;
	}

	private void treatBody() throws IOException {
		getBodyCharacter();
		while (body.remaining() != 0) {
			trash();
			seenWords.add(collectWord());
		}
	}

	private char getBodyCharacter() throws IOException {
		return readMethod.get();
	}

	private boolean trash() throws IOException {
		boolean done = false;
		boolean trash = false;
		while (!done && body.hasRemaining()) {
			done = true;
			if (trashWhiteSpace() || trashTag()) {
				trash = true;
				done = false;
			}
		}
		return trash;
	}

	private String collectWord() throws IOException {
		StringBuilder sb = new StringBuilder();
		boolean trash = false;
		try {
			trash = trash();
		} catch (BufferUnderflowException e) {
			trash = true;
		}
		while (!trash) {
			sb.append(current);
			getBodyCharacter();
			try {
				trash = trash();
			} catch (BufferUnderflowException e) {
				trash = true;
			}
		}
		return unescapeHTML(sb.toString());
	}

	// ---------------- /!\ UGLY CODE! CLOSE YOUR EYES! /!\ ---------------------
	private String unescapeHTML(String string) {
		return string.replace("&exclamation;", "!").replace("&quot;", "\"").replace("&percent;", "%")
				.replace("&amp;", "&").replace("&apos;", "'").replace("&add;", "+").replace("&lt;", "<")
				.replace("&equal;", "=").replace("&gt;", ">").replace("&iexcl;", "Â¡").replace("&cent;", "Â¢")
				.replace("&pound;", "Â£").replace("&curren;", "Â¤").replace("&yen;", "Â¥").replace("&brvbar;", "Â¦")
				.replace("&sect;", "Â§").replace("&uml;", "Â¨").replace("&copy;", "Â©").replace("&ordf;", "Âª")
				.replace("&laquo;", "Â«").replace("&not;", "Â¬").replace("&reg;", "Â®").replace("&macr;", "Â¯")
				.replace("&deg;", "Â°").replace("&plusmn;", "Â±").replace("&sup2;", "Â²").replace("&sup3;", "Â³")
				.replace("&acute;", "Â´").replace("&micro;", "Âµ").replace("&para;", "Â¶").replace("&middot;", "Â·")
				.replace("&cedil;", "Â¸").replace("&sup1;", "Â¹").replace("&ordm;", "Âº").replace("&raquo;", "Â»")
				.replace("&frac14;", "Â¼").replace("&frac12;", "Â½").replace("&frac34;", "Â¾").replace("&iquest;", "Â¿")
				.replace("&Agrave;", "Ã€").replace("&Aacute;", "Ã�").replace("&Acirc;", "Ã‚").replace("&Atilde;", "Ãƒ")
				.replace("&Auml;", "Ã„").replace("&Aring;", "Ã…").replace("&AElig;", "Ã†").replace("&Ccedil;", "Ã‡")
				.replace("&Egrave;", "Ãˆ").replace("&Eacute;", "Ã‰").replace("&Ecirc;", "ÃŠ").replace("&Euml;", "Ã‹")
				.replace("&Igrave;", "ÃŒ").replace("&Iacute;", "Ã�").replace("&Icirc;", "ÃŽ").replace("&Iuml;", "Ã�")
				.replace("&ETH;", "Ã�").replace("&Ntilde;", "Ã‘").replace("&Ograve;", "Ã’").replace("&Oacute;", "Ã“")
				.replace("&Ocirc;", "Ã”").replace("&Otilde;", "Ã•").replace("&Ouml;", "Ã–").replace("&times;", "Ã—")
				.replace("&Oslash;", "Ã˜").replace("&Ugrave;", "Ã™").replace("&Uacute;", "Ãš").replace("&Ucirc;", "Ã›")
				.replace("&Uuml;", "Ãœ").replace("&Yacute;", "Ã�").replace("&THORN;", "Ãž").replace("&szlig;", "ÃŸ")
				.replace("&agrave;", "Ã ").replace("&aacute;", "Ã¡").replace("&acirc;", "Ã¢").replace("&atilde;", "Ã£")
				.replace("&auml;", "Ã¤").replace("&aring;", "Ã¥").replace("&aelig;", "Ã¦").replace("&ccedil;", "Ã§")
				.replace("&egrave;", "Ã¨").replace("&eacute;", "Ã©").replace("&ecirc;", "Ãª").replace("&euml;", "Ã«")
				.replace("&igrave;", "Ã¬").replace("&iacute;", "Ã­").replace("&icirc;", "Ã®").replace("&iuml;", "Ã¯")
				.replace("&eth;", "Ã°").replace("&ntilde;", "Ã±").replace("&ograve;", "Ã²").replace("&oacute;", "Ã³")
				.replace("&ocirc;", "Ã´").replace("&otilde;", "Ãµ").replace("&ouml;", "Ã¶").replace("&divide;", "Ã·")
				.replace("&oslash;", "Ã¸").replace("&ugrave;", "Ã¹").replace("&uacute;", "Ãº").replace("&ucirc;", "Ã»")
				.replace("&uuml;", "Ã¼").replace("&yacute;", "Ã½").replace("&thorn;", "Ã¾").replace("&yuml;", "Ã¿")
				.replace("&OElig;", "Å’").replace("&oelig;", "Å“").replace("&Scaron;", "Å ").replace("&scaron;", "Å¡")
				.replace("&Yuml;", "Å¸").replace("&fnof;", "Æ’").replace("&circ;", "Ë†").replace("&tilde;", "Ëœ")
				.replace("&Alpha;", "Î‘").replace("&Beta;", "Î’").replace("&Gamma;", "Î“").replace("&Delta;", "Î”")
				.replace("&Epsilon;", "Î•").replace("&Zeta;", "Î–").replace("&Eta;", "Î—").replace("&Theta;", "Î˜")
				.replace("&Iota;", "Î™").replace("&Kappa;", "Îš").replace("&Lambda;", "Î›").replace("&Mu;", "Îœ")
				.replace("&Nu;", "Î�").replace("&Xi;", "Îž").replace("&Omicron;", "ÎŸ").replace("&Pi;", "Î ")
				.replace("&Rho;", "Î¡").replace("&Sigma;", "Î£").replace("&Tau;", "Î¤").replace("&Upsilon;", "Î¥")
				.replace("&Phi;", "Î¦").replace("&Chi;", "Î§").replace("&Psi;", "Î¨").replace("&Omega;", "Î©")
				.replace("&alpha;", "Î±").replace("&beta;", "Î²").replace("&gamma;", "Î³").replace("&delta;", "Î´")
				.replace("&epsilon;", "Îµ").replace("&zeta;", "Î¶").replace("&eta;", "Î·").replace("&theta;", "Î¸")
				.replace("&iota;", "Î¹").replace("&kappa;", "Îº").replace("&lambda;", "Î»").replace("&mu;", "Î¼")
				.replace("&nu;", "Î½").replace("&xi;", "Î¾").replace("&omicron;", "Î¿").replace("&pi;", "Ï€")
				.replace("&rho;", "Ï�").replace("&sigmaf;", "Ï‚").replace("&sigma;", "Ïƒ").replace("&tau;", "Ï„")
				.replace("&upsilon;", "Ï…").replace("&phi;", "Ï†").replace("&chi;", "Ï‡").replace("&psi;", "Ïˆ")
				.replace("&omega;", "Ï‰").replace("&thetasym;", "Ï‘").replace("&upsih;", "Ï’").replace("&piv;", "Ï–")
				.replace("&ndash;", "â€“").replace("&mdash;", "â€”").replace("&horbar;", "â€•")
				.replace("&lsquo;", "â€˜").replace("&rsquo;", "â€™").replace("&sbquo;", "â€š").replace("&ldquo;", "â€œ")
				.replace("&rdquo;", "â€�").replace("&bdquo;", "â€ž").replace("&dagger;", "â€ ")
				.replace("&Dagger;", "â€¡").replace("&bull;", "â€¢").replace("&hellip;", "â€¦")
				.replace("&permil;", "â€°").replace("&prime;", "â€²").replace("&Prime;", "â€³")
				.replace("&lsaquo;", "â€¹").replace("&rsaquo;", "â€º").replace("&oline;", "â€¾")
				.replace("&frasl;", "â�„").replace("&euro;", "â‚¬").replace("&image;", "â„‘").replace("&weierp;", "â„˜")
				.replace("&real;", "â„œ").replace("&trade;", "â„¢").replace("&alefsym;", "â„µ").replace("&larr;", "â†�")
				.replace("&uarr;", "â†‘").replace("&rarr;", "â†’").replace("&darr;", "â†“").replace("&harr;", "â†”")
				.replace("&crarr;", "â†µ").replace("&lArr;", "â‡�").replace("&uArr;", "â‡‘").replace("&rArr;", "â‡’")
				.replace("&dArr;", "â‡“").replace("&hArr;", "â‡”").replace("&forall;", "âˆ€").replace("&part;", "âˆ‚")
				.replace("&exist;", "âˆƒ").replace("&empty;", "âˆ…").replace("&nabla;", "âˆ‡").replace("&isin;", "âˆˆ")
				.replace("&notin;", "âˆ‰").replace("&ni;", "âˆ‹").replace("&prod;", "âˆ�").replace("&sum;", "âˆ‘")
				.replace("&minus;", "âˆ’").replace("&lowast;", "âˆ—").replace("&radic;", "âˆš").replace("&prop;", "âˆ�")
				.replace("&infin;", "âˆž").replace("&ang;", "âˆ ").replace("&and;", "âˆ§").replace("&or;", "âˆ¨")
				.replace("&cap;", "âˆ©").replace("&cup;", "âˆª").replace("&int;", "âˆ«").replace("&there4;", "âˆ´")
				.replace("&sim;", "âˆ¼").replace("&cong;", "â‰…").replace("&asymp;", "â‰ˆ").replace("&ne;", "â‰ ")
				.replace("&equiv;", "â‰¡").replace("&le;", "â‰¤").replace("&ge;", "â‰¥").replace("&sub;", "âŠ‚")
				.replace("&sup;", "âŠƒ").replace("&nsub;", "âŠ„").replace("&sube;", "âŠ†").replace("&supe;", "âŠ‡")
				.replace("&oplus;", "âŠ•").replace("&otimes;", "âŠ—").replace("&perp;", "âŠ¥").replace("&sdot;", "â‹…")
				.replace("&lceil;", "âŒˆ").replace("&rceil;", "âŒ‰").replace("&lfloor;", "âŒŠ")
				.replace("&rfloor;", "âŒ‹").replace("&lang;", "âŒ©").replace("&rang;", "âŒª").replace("&loz;", "â—Š")
				.replace("&spades;", "â™ ").replace("&clubs;", "â™£").replace("&hearts;", "â™¥")
				.replace("&diams;", "â™¦").replace("&nbsp;", "");
	}

	private boolean trashWhiteSpace() throws IOException {
		if (Character.isWhitespace(current)) {
			while (body.hasRemaining() && Character.isWhitespace(getBodyCharacter()))
				;
			return true;
		} else
			return false;
	}

	private boolean trashTag() throws IOException {
		if (current == '<') {
			trashCssAndJsButSaveLinks();
			while (getBodyCharacter() != '>')
				;
			getBodyCharacter();
			return true;
		} else
			return false;
	}

	private void trashCssAndJsButSaveLinks() throws IOException {
		switch (getBodyCharacter()) {
		case 's':
			switch (getBodyCharacter()) {
			case 't':
				trashCss();
				break;
			case 'c':
				trashJs();
				break;
			}
			break;
		case 'a':
			getHref();
			break;
		}
	}

	private void trashCss() throws IOException {
		if (nextInBodyIs("yle"))
			while (getBodyCharacter() != '<')
				;
	}

	private boolean nextInBodyIs(String match) throws IOException {
		if (body.remaining() >= match.length()) {
			for (int i = 0; i < match.length(); i++) {
				if (getBodyCharacter() != match.charAt(i))
					return false;
			}
			return true;
		}
		return false;
	}

	private void trashJs() throws IOException {
		if (nextInBodyIs("ript")) {
			while (getBodyCharacter() != '>')
				;
			getEndOfJs();
		}
	}

	private void getHref() throws IOException {
		if (nextInBodyIs(" href=\"")) {
			StringBuilder sb = new StringBuilder();
			while (getBodyCharacter() != '\"')
				sb.append(current);
			seenUrls.add(sb.toString());
		}
	}

	private void getEndOfJs() throws IOException {
		boolean done = false;
		while (!done) {
			while (getBodyCharacter() != '<')
				;
			if (nextInBodyIs("/script"))
				done = true;
		}
	}

	public static void main(String[] args) throws IOException {
		System.out.println(new ClientTask().apply("www.meteofrance.com", "/accueil"));
		// System.out.println(new ClientTask().apply("example.com", ""));
		// System.out.println(new ClientTask().apply("www.lemonde.fr", ""));
	}
}