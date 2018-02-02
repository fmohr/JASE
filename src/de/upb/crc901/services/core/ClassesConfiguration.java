package de.upb.crc901.services.core;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import jaicore.basic.FileUtil;

/***
 * Encapsulated configuration about classes, like wrappers names.
 * @author aminfaez
 *
 */
public class ClassesConfiguration extends HashMap<String, JsonNode> {
	
	/**
	 * Reads the cofiguration file from configPath into this map.
	 * @param configPath Filepath to configuration
	 * @throws IOException If the file can't be read or there is problems with parsing the content.
	 */
	public ClassesConfiguration(String configPath) throws IOException {
		super();
		String jsonString = FileUtil.readFileAsString(configPath);
		TypeReference<HashMap<String,JsonNode>> typeRef = new TypeReference<HashMap<String,JsonNode>>() {};

        ObjectMapper mapper = new ObjectMapper(); 
        HashMap<String,JsonNode> o = mapper.readValue(jsonString, typeRef);
        this.putAll(o);
	}
	/**
	 * Returns true if the configuration has the given classpath entry.
	 */
	public boolean classknown(String classpath) {
		return this.containsKey(classpath);
	}
	
	/**
	 * Returns true if in the configuration the given classpath entry has an wrapper entry.
	 */
	public boolean isWrapped(String classpath) {
		return classknown(classpath) && this.get(classpath).has("wrapper");
	}
	
	/**
	 * Returns the classpath of wrapper which the given classpath was assigned onto.
	 */
	public String getWrapperClasspath(String classPath) {
		return this.get(classPath).get("wrapper").asText();
	}
	
}
