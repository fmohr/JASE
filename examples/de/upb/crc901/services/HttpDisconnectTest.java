package de.upb.crc901.services;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.URL;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.SynchronousQueue;
import java.util.stream.Collectors;

import org.junit.runner.notification.StoppedByUserException;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

public class HttpDisconnectTest implements HttpHandler {

	private static long lastAliveMessage = System.currentTimeMillis();

	class StreamListener implements Runnable {

		HttpExchange t;

		public StreamListener(HttpExchange t) {
			super();
			this.t = t;
		}

		@Override
		public void run() {
			try {
				while (true) {
					InputStream is = t.getRequestBody();
					int result = 0;
					do {

						/* read bytes from stream */
						byte[] content = new byte[100];
						result = is.read(content);
						List<String> lines = Arrays.asList(new String(content).split("\n")).stream().map(l -> l.trim()).filter(l -> !l.isEmpty()).collect(Collectors.toList());

						/* recognize that the client is still listening */
						if (lines.contains("alive")) {
							lastAliveMessage = System.currentTimeMillis();
							System.out.println("Server: Staying alive.");
						}
					} while (result >= 0);
					try {
						Thread.sleep(300);
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
				}
			} catch (IOException e) {
				if (e.getMessage().startsWith("end of stream")) {
					System.out.println("Stream has ended. Shutting down.");
				} else
					e.printStackTrace();
				CloseServer();
			}
		}
	}

	class Killer implements Runnable {

		@Override
		public void run() {
			try {
				while (true) {

					Thread.sleep(10 * 1000);
					System.out.println("Killer: Checking whether we are still alive.");
					
					/* if the client has not sent any alive signal for 10 seconds, interrupt my activity */
					if (System.currentTimeMillis() - lastAliveMessage > 10000) {
						System.out.println("Killer: Apparently, the client is not connected anymore. I will kill the server now!");
						CloseServer();
					}
				}
			} catch (InterruptedException e) {
				System.out.println("Killer received interrupt, shutting down.");
			}
		}
	}

	@Override
	public void handle(HttpExchange t) throws IOException {

		/* launch observer. The observer is responsible for continuously reading data coming from the client, in particular the alive messages */
		new Thread(new StreamListener(t)).start();
		new Thread(new Killer()).start();
		
		// System.out.println("Done calculating:");
		// t.sendResponseHeaders(200, "done".getBytes().length);
		// System.out.println("Wrote headers");
		// OutputStream stream = t.getResponseBody();
		// stream.write("done".getBytes());
		// System.out.println("Wrote body");
	}

	static HttpServer server;

	static void StartServer() throws IOException {
		server = HttpServer.create(new InetSocketAddress(8000), 100);

		// Set an Executor for the multi-threading
		server.setExecutor(java.util.concurrent.Executors.newFixedThreadPool(16));

		server.createContext("/", new HttpDisconnectTest());
		server.start();
		System.out.println("Server is up ...");
	}

	static void CloseServer() {
		System.exit(0);
	}

	// CLIENT
	public static void main(String[] a) throws IOException, InterruptedException {
		// URL url = new URL("http://localhost:5000/test");

		URL url = new URL("http://localhost:8000/test");
		StartServer();

		HttpURLConnection con = (HttpURLConnection) url.openConnection();
		con.setConnectTimeout(2000);
		con.setChunkedStreamingMode(1 << 20); // 1 MByte buffer
		con.setRequestProperty("Content-Type", "application/json");
		con.setRequestMethod("POST");
		con.setDoOutput(true);
		System.out.println("Sending data started");
		/* send data */
		OutputStream out = con.getOutputStream();
		out.write("\nLorem ipsum dolor sit amet.".getBytes());
		out.flush();

		System.out.println("Sent data; now start sleeping.");
		for (int i = 0; i < 5; i++) {
			Thread.sleep(1000);
			out.write("\nalive".getBytes());
			out.flush();
			System.out.println("Client: Sending listening info.");
		}

		System.out.println("Ceasing to send listening info.");

		// con.disconnect();

		// out.close();
		// System.out.println("Disconnected; now reading return stream.");
		// try {
		// int responseCode = con.getResponseCode();
		// if(responseCode == 200) {
		// try (InputStream in = con.getInputStream()){
		// String returnString = IOUtils.toString(in, Charset.defaultCharset());
		// System.out.println("Server Returned: " + returnString);
		// }catch(IOException ex) {
		// ex.printStackTrace();
		// }
		// catch(Exception ex) {
		// ex.printStackTrace();
		// }
		// }
		// } finally {
		// CloseServer();
		// }
	}
}
