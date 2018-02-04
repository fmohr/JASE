package de.upb.crc901.services.typeserializers;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;

import de.upb.crc901.services.core.IOntologySerializer;
import de.upb.crc901.services.core.JASEDataObject;
import weka.core.DenseInstance;
import weka.core.json.JSONNode;

@SuppressWarnings({ "unchecked", "rawtypes" })
public class ArrayListOntologySerializer implements IOntologySerializer<ArrayList<String>> {

	private static final List<String> supportedTypes = Arrays.asList(new String[] {"StringList"});
	
	@Override
	public ArrayList<String> unserialize(JASEDataObject json) {
		if(json.getObject().isArray()) {
			// iterate over array elements and add them into an array:
			ArrayList<String> returnedList = new ArrayList<>();
			for(JsonNode node : json.getObject()) {
				if(!node.isTextual()) {
					throw new RuntimeException("Syntx error: " + node + " isn't of type string.");
				}
				returnedList.add(node.asText());
			}
			return returnedList;
		}
		else {
			// the encapsulated JSONNode object isn't an Array.
			throw new RuntimeException("Syntx error: Type was specified to be an array but the json node isn't one.");
		}
		
	}

	@Override
	public JASEDataObject serialize(ArrayList<String> list) {
		ObjectMapper mapper = new ObjectMapper();
		ArrayNode arrayNode = mapper.createArrayNode();
		JASEDataObject jdo = new JASEDataObject("StringList", arrayNode);
		for(Object o : list) {
			String s = "\"" + o.toString() + "\"";
			try {
				JsonNode stringNode = mapper.readTree(s);
				arrayNode.add(stringNode);
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
			
		}
		return jdo;
	}

	@Override
	public Collection<String> getSupportedSemanticTypes() {
		return supportedTypes;
	}

}
