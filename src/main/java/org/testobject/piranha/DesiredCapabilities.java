package org.testobject.piranha;

import java.util.HashMap;
import java.util.Map;

public class DesiredCapabilities {

	private Map<String, Object> capabilities = new HashMap<String, Object>();
	
	public void setCapability(String key, Object value) {
		capabilities.put(key, value);
	}
	
	Map<String, Object> getCapabilities() {
		return capabilities;
	}
	
}
