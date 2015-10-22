package org.testobject.piranha;

import java.io.IOException;
import java.util.Scanner;

import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;

import org.apache.http.HttpEntity;
import org.apache.http.HttpHeaders;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;

public class Proxy extends NanoHTTPD {

	private final String url;
	private final CloseableHttpClient httpClient;

	public Proxy(int port, String baseUrl, String sessionId) {
		super(port);

		this.httpClient = HttpClients.createDefault();
		this.url = baseUrl + "/session/" + sessionId;
	}

	public static void main(String... args) {

		String url = "https://citrix.testobject.com/api/appium/wd/hub";

		WebTarget webTarget = ClientBuilder.newClient().target(url);

		System.out.println("Jersey");
		for (int i = 0; i < 5; i++) {
			long tic = System.currentTimeMillis();
			webTarget.request(MediaType.APPLICATION_JSON).get();
			long toc = System.currentTimeMillis();

			System.out.println(toc - tic);
		}

		CloseableHttpClient httpClient = HttpClients.createDefault();

		System.out.println("Apache client");
		for (int i = 0; i < 5; i++) {
			long tic = System.currentTimeMillis();

			HttpGet get = new HttpGet(url);

			get.setHeader(HttpHeaders.ACCEPT_ENCODING, "application/json-rpc");

			try {
				entityToString(httpClient.execute(get));

			} catch (IOException e) {
				throw new RuntimeException(e);
			}

			long toc = System.currentTimeMillis();

			System.out.println(toc - tic);
		}

	}

	@Override
	public Response serve(IHTTPSession session) {
		String command = session.readBody();

		long tic = System.currentTimeMillis();
		HttpPost p = new HttpPost(url);
		p.setEntity(new StringEntity(command,
				ContentType.create(MediaType.APPLICATION_FORM_URLENCODED)));

		p.setHeader(HttpHeaders.ACCEPT_ENCODING, "application/json-rpc");
		try {
			CloseableHttpResponse response = httpClient.execute(p);

			String responseAsString = entityToString(response);
			long toc = System.currentTimeMillis();
			System.out.println("'request with apache' took " + (toc - tic) + " to execute");

			return new NanoHTTPD.Response(Response.Status.OK, "application/json-rpc", responseAsString);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	private static String entityToString(CloseableHttpResponse response) {
		try {

			HttpEntity entity = response.getEntity();

			@SuppressWarnings("resource")
			String responseAsString = new Scanner(entity.getContent(), "UTF-8").useDelimiter("\\A").next();
			EntityUtils.consume(entity);
			return responseAsString;
		} catch (UnsupportedOperationException | IOException e) {
			throw new RuntimeException(e);
		} finally {
			try {
				response.close();
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
		}
	}

}
