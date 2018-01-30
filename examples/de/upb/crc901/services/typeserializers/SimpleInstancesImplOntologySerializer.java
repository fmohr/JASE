package de.upb.crc901.services.typeserializers;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import de.upb.crc901.services.core.IOntologySerializer;
import de.upb.crc901.services.core.JASEDataObject;
import jaicore.ml.core.SimpleInstancesImpl;

public class SimpleInstancesImplOntologySerializer implements IOntologySerializer<SimpleInstancesImpl>  {
	
	private static final List<String> supportedTypes = Arrays.asList(new String[] {"Instances"});
	
	public  SimpleInstancesImpl unserialize(final JASEDataObject jdo) {
		return new SimpleInstancesImpl(jdo.getObject());
	}

	@Override
	public JASEDataObject serialize(SimpleInstancesImpl object) {
		try {
			JsonNode node = new ObjectMapper().readTree(object.toJson());
			String type = "Instnace";
			JASEDataObject jdo = new JASEDataObject(type, node);
			return jdo;
		} catch (IOException e) { 
			throw new RuntimeException(e.getMessage(), e); // mask checked exception
		}
	}

	@Override
	public Collection<String> getSupportedSemanticTypes() {
		return supportedTypes;
	}
}
