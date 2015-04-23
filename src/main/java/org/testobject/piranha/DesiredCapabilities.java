package org.testobject.piranha;

import java.util.HashMap;
import java.util.Map;

public class DesiredCapabilities {

	private Map<String, String> capabilities = new HashMap<String, String>();
	
	public void setCapability(String key, String value) {
		capabilities.put(key, value);
	}
	
	Map<String, String> getCapabilities() {
		return capabilities;
	}
	
}
