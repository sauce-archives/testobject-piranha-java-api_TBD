package org.testobject.piranha;

import java.io.IOException;
import java.lang.reflect.Type;
import java.net.ServerSocket;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import javax.ws.rs.InternalServerErrorException;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

public class TestObjectPiranha {

	//	public static String TESTOBJECT_BASE_URL = "http://localhost:7070/";

	public static String TESTOBJECT_APP_BASE_URL = "https://app.testobject.com:443/api/";
	public static String TESTOBJECT_CITRIX_BASE_URL = "https://citrix.testobject.com:443/api/";
	//	public static String TESTOBJECT_BASE_URL = "http://branches.testobject.org/api/";

	//	public static void main(String... args) throws IOException {
	//
	//		int bla = uploadApp("F09A1BE7F6C148DBB6E200AB8D2EDAF9", new File("/home/aluedeke/Downloads/billiger-de-resigned.ipa"));
	//		System.out.println(bla);
	//		 File appFile = new
	//		 File("/home/leonti/development/citrix/ForTestObject/gotomeeting-5.0.799.1290-SNAPSHOT.apk");
	//		 int id =
	//		 TestObjectPiranha.uploadApp("42995311C3724F21A9266E24643DA754",
	//		 appFile);
	//		 System.out.println("App id is: " + id);
	//
	//		 File appFrameworkFile = new
	//		 File("/home/leonti/development/citrix/ForTestObject/piranha-android-server-5.0.30-SNAPSHOT.apk");
	//		 int id =
	//		 TestObjectPiranha.uploadFrameworkApp("42995311C3724F21A9266E24643DA754",
	//		 appFrameworkFile);
	//		 System.out.println("Framework id is: " + id);
	//
	//		 DesiredCapabilities capabilities = new DesiredCapabilities();
	//		 capabilities.setCapability("testobject_api_key",
	//		 "42995311C3724F21A9266E24643DA754");
	//		 capabilities.setCapability("testobject_app_id", "1");
	//		 capabilities.setCapability("testobject_framework_app_id", "2");
	//		 capabilities.setCapability("testobject_device",
	//		 "Sony_Xperia_T_real");
	//
	//		 Map<String, String> piranhaCaps = new HashMap<String, String>();
	//		 piranhaCaps.put("className",
	//		 "com.citrixonline.universal.ui.activities.LauncherActivity");
	//		 piranhaCaps.put("intent",
	//		 "com.citrixonline.piranha.androidserver/com.citrixonline.piranha.androidserver.PiranhaAndroidInstrumentation");
	//		 piranhaCaps.put("packageName",
	//		 "com.citrixonline.android.gotomeeting");
	//
	//		 capabilities.setCapability("piranha_params", new
	//		 GsonBuilder().create().toJson(piranhaCaps));
	//
	//		 TestObjectPiranha testObjectPiranha = new
	//		 TestObjectPiranha(capabilities);
	//		 testObjectPiranha.close();
	//
	//		 for (TestObjectDevice device : listDevices()) {
	//		 System.out.println(device);
	//		 }
	//
	//	}

	private final String baseUrl;
	private final Client client = ClientBuilder.newClient();
	private final WebTarget webTarget;

	private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

	private String sessionId;
	private Proxy proxy;
	private int port;

	private String liveViewURL;
	private String testReportURL;

	public TestObjectPiranha(DesiredCapabilities desiredCapabilities) {
		this(TESTOBJECT_APP_BASE_URL, desiredCapabilities);
	}

	public TestObjectPiranha(String baseUrl, DesiredCapabilities desiredCapabilities) {

		this.baseUrl = baseUrl;
		this.webTarget = client.target(baseUrl + "piranha");

		Map<String, Map<String, String>> fullCapabilities = new HashMap<String, Map<String, String>>();
		fullCapabilities.put("desiredCapabilities", desiredCapabilities.getCapabilities());

		String capsAsJson = new GsonBuilder().create().toJson(fullCapabilities);

		try {
			String response = webTarget.path("session").request(MediaType.TEXT_PLAIN)
					.post(Entity.entity(capsAsJson, MediaType.APPLICATION_JSON), String.class);

			Map<String, String> map = jsonToMap(response);
			sessionId = map.get("sessionId");
			liveViewURL = map.get("testLiveViewUrl");
			testReportURL = map.get("testReportUrl");

		} catch (InternalServerErrorException e) {
			rethrow(e);
		}

		startProxyServer(sessionId);
		startKeepAlive(sessionId);
	}

	private void startKeepAlive(final String sessionId) {
		scheduler.scheduleAtFixedRate(new Runnable() {
			@Override
			public void run() {
				try {
					webTarget.path("session").path(sessionId).path("keepalive")
							.request(MediaType.APPLICATION_JSON)
							.post(Entity.entity("", MediaType.APPLICATION_JSON), String.class);
				} catch (Exception e) {
					System.out.println("KeepAlive exception " + e);
				}
			}
		}, 10, 10, TimeUnit.SECONDS);
	}

	private void startProxyServer(String sessionId) {
		port = findFreePort();

		proxy = new Proxy(port, baseUrl + "piranha", sessionId);
		try {
			proxy.start();
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	public String getSessionId() {
		return sessionId;
	}

	private static int findFreePort() {
		int port;
		try {
			ServerSocket socket = new ServerSocket(0);
			port = socket.getLocalPort();
			socket.close();
		} catch (Exception e) {
			port = -1;
		}
		return port;
	}

	public int getPort() {
		return port;
	}

	private void rethrow(InternalServerErrorException e) {
		String response = e.getResponse().readEntity(String.class);

		throw new RuntimeException(response);
	}

	private static Map<String, String> jsonToMap(String json) {
		Gson gson = new Gson();
		Type stringStringMap = new TypeToken<Map<String, String>>() {}.getType();
		return gson.fromJson(json, stringStringMap);
	}

	public String getTestReportURL() {
		return testReportURL;
	}

	public String getLiveViewURL() {
		return liveViewURL;
	}

	public void close() {
		scheduler.shutdown();
		deleteSession();

		try {
			proxy.stop();
		} catch (Exception e) {
			// ignored
		}

	}

	private void deleteSession() {
		try {
			webTarget.path("session/" + sessionId).request(MediaType.APPLICATION_JSON).delete();
		} catch (InternalServerErrorException e) {
			rethrow(e);
		}
	}

	public static TestObjectApi api() {
		return new TestObjectApi(TESTOBJECT_CITRIX_BASE_URL);
	}

	public static TestObjectApi apiPublic() {
		return new TestObjectApi(TESTOBJECT_APP_BASE_URL);
	}

}
