package org.testobject.piranha;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.Invocation;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.Form;
import javax.ws.rs.core.MediaType;

import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.io.FileUtils;
import org.glassfish.jersey.apache.connector.ApacheConnectorProvider;
import org.glassfish.jersey.client.ClientConfig;
import org.glassfish.jersey.client.authentication.HttpAuthenticationFeature;
import org.testobject.piranha.TestObjectDevice.DeviceContainer;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

public class TestObjectApi {

	private final String baseUrl;

	public TestObjectApi(String baseUrl) {
		this.baseUrl = baseUrl;
	}	
	
	public List<TestObjectDevice> listDevices(String apiKey) {
		HttpAuthenticationFeature authFeature = HttpAuthenticationFeature.basicBuilder()
				.credentials("testobject-api", apiKey).build();

		Client client = ClientBuilder.newClient().register(authFeature);
		String descriptors = client.target(baseUrl + "rest/descriptors/descriptors-api")
				.request(MediaType.APPLICATION_JSON).get(String.class);

		List<DeviceContainer> deviceList = new Gson().fromJson(descriptors, new TypeToken<List<DeviceContainer>>() {}.getType());

		String availableDescriptors = client.target(baseUrl + "rest/descriptors/availableDescriptors-api")
				.request(MediaType.APPLICATION_JSON).get(String.class);

		List<String> available = new Gson().fromJson(availableDescriptors, new TypeToken<List<String>>() {}.getType());

		List<TestObjectDevice> devices = new LinkedList<>();
		for (DeviceContainer deviceContainer : deviceList) {
			devices.add(new TestObjectDevice(deviceContainer, available.contains(deviceContainer.id)));
		}

		return devices;		
	}
	
	public String regenerateApiKey(String user, String password, String project) {
		ClientConfig clientConfig = new ClientConfig();
		clientConfig.connectorProvider(new ApacheConnectorProvider());
		Client client = ClientBuilder.newClient(clientConfig);

		WebTarget target = client.target(baseUrl + "rest");

		Form form = new Form();
		form.param("user", user);
		form.param("password", password);

		target.path("users").path("login").request(MediaType.APPLICATION_JSON_TYPE)
				.post(Entity.entity(form, MediaType.APPLICATION_FORM_URLENCODED_TYPE), String.class);

		String apiKeyResponse = target.path("users").path("testobject").path("projects").path(project)
				.path("apiKey/appium").request(MediaType.APPLICATION_JSON_TYPE)
				.post(Entity.entity("", MediaType.APPLICATION_JSON_TYPE), String.class);

		return jsonToMap(apiKeyResponse).get("id");
	}
	
	private static Map<String, String> jsonToMap(String json) {
		Gson gson = new Gson();
		Type stringStringMap = new TypeToken<Map<String, String>>() {
		}.getType();
		return gson.fromJson(json, stringStringMap);
	}
	
	public int uploadApp(String apiKey, File appFile) {
		return uploadApp(apiKey, appFile, false);
	}

	public int uploadFrameworkApp(String apiKey, File appFile) {
		return uploadApp(apiKey, appFile, true);
	}	
	
	private int uploadApp(String apiKey, File appFile, boolean isFramework) {
		WebTarget storageTarget = createAuthenticatingClient(apiKey);

		String md5 = md5(appFile);
		Integer existingAppId = getExistingApp(storageTarget, md5);
		if(existingAppId != null){
			return existingAppId;
		} else {
			return uploadFile(appFile, isFramework, storageTarget, md5);
		}
	}

	private static int uploadFile(File appFile, boolean isFramework, WebTarget storageTarget, String md5) {
		Invocation.Builder invocationBuilder = storageTarget.path("upload").request(MediaType.TEXT_PLAIN);

		try {
			if (isFramework) {
				invocationBuilder.header("App-Type", "framework");
			}
			invocationBuilder.header("App-Identifier", md5);

			String appId = invocationBuilder
					.post(Entity.entity(FileUtils.openInputStream(appFile), MediaType.APPLICATION_OCTET_STREAM),
							String.class);
			return Integer.valueOf(appId);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	private Integer getExistingApp(WebTarget storageTarget, String md5) {
		System.out.println(storageTarget.path("app").queryParam("appIdentifier", md5).getUri());
		String response = storageTarget.path("app").queryParam("appIdentifier", md5).request().get(String.class);
		List<TestObjectApp> testObjectApps = new Gson().fromJson(response, new TypeToken<List<TestObjectApp>>() {}.getType());

		return testObjectApps != null && testObjectApps.isEmpty() == false ? testObjectApps.get(0).getId() : null;
	}

	private String md5(File appFile) {
		try (FileInputStream fis = new FileInputStream(appFile)){
			return DigestUtils.md5Hex(fis);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	private WebTarget createAuthenticatingClient(String apiKey){
		HttpAuthenticationFeature authFeature = HttpAuthenticationFeature.basicBuilder()
				.credentials("testobject-api", apiKey).build();

		Client client = ClientBuilder.newClient().register(authFeature);
		return client.target(baseUrl + "storage");
	}
	
}
