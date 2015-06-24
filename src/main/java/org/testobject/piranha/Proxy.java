package org.testobject.piranha;

import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;

public class Proxy extends NanoHTTPD {

	private final WebTarget webTarget;

	public Proxy(int port, String baseUrl, String sessionId) {
		super(port);
		this.webTarget = ClientBuilder.newClient().target(baseUrl).path("session").path(sessionId);
	}

	@Override
	public Response serve(IHTTPSession session) {

		String command = session.readBody();

		String response = webTarget.request("application/json-rpc").post(Entity.entity(command, MediaType.APPLICATION_FORM_URLENCODED), String.class);

		return new NanoHTTPD.Response(Response.Status.OK, "application/json-rpc", response);
	}

}
