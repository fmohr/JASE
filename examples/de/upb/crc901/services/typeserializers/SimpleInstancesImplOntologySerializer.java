package de.upb.crc901.services.typeserializers;

import java.io.IOException;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import de.upb.crc901.services.core.IOntologySerializer;
import jaicore.ml.core.SimpleInstancesImpl;

public class SimpleInstancesImplOntologySerializer implements IOntologySerializer<SimpleInstancesImpl>  {
	public  SimpleInstancesImpl unserialize(final JsonNode json) {
		return new SimpleInstancesImpl(json);
	}

	@Override
	public JsonNode serialize(SimpleInstancesImpl object) {
		try {
			return new ObjectMapper().readTree(object.toJson());
		} catch (IOException e) { 
			throw new RuntimeException(e.getMessage(), e); // mask checked exception
		}
	}
}
