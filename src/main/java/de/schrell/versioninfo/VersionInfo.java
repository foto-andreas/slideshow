package de.schrell.versioninfo;

import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.util.Properties;

public class VersionInfo implements Serializable {

	private static final long serialVersionUID = 3147071937883636969L;

	private static final String GROUP_ID = "groupId";
	private static final String ARTIFACT_ID = "artifactId";
	private static final String VERSION = "version";

	private static final String UNKNOWN = "<unknown>";

	private Properties prop;

	public VersionInfo()
	{
		try
		{
			try (InputStream resourceAsStream = this.getClass().getResourceAsStream("/version.properties")) {
				prop = new Properties();
				prop.load(resourceAsStream);
			}
		} catch (final IOException e) {
			prop.setProperty(GROUP_ID, UNKNOWN);
			prop.setProperty(ARTIFACT_ID, UNKNOWN);
			prop.setProperty(VERSION, UNKNOWN);
		}
	}

	public String getArtifactId() {
		return prop.getProperty(ARTIFACT_ID);
	}

	public String getGroupId() {
		return prop.getProperty(GROUP_ID);
	}

	public String getVersion() {
		return prop.getProperty(VERSION);
	}

}
