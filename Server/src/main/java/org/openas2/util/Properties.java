package org.openas2.util;

import java.util.HashMap;
import java.util.Map;

public class Properties
{
	public static String APP_VERSION_PROP = "app.version";
	public static String APP_TITLE_PROP = "app.title";
	public static String APP_BASE_DIR_PROP = "app.base.dir";
	
	private static Map<String, String> _properties = new HashMap<String, String>();
	
	public static void setProperties(Map<String, String> map)
	{
		_properties = map;
	}

	public static void setProperty(String prop, String val)
	{
		_properties.put(prop, val);
	}

	public static Map<String, String> getProperties()
	{
		return _properties;
	}

	public static String getProperty(String key, String fallback)
	{
		String val = _properties.get(key);
		if (val == null)
		{
			return fallback;
		}
		return val;
	}


}
