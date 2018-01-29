package de.upb.crc901.services.typeserializers;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;

import de.upb.crc901.services.core.IOntologySerializer;
import de.upb.crc901.services.core.JASEDataObject;
import jaicore.ml.core.SimpleInstanceImpl;

public class SimpleInstanceImplOntologySerializer implements IOntologySerializer<SimpleInstanceImpl>  {
	private static final List<String> supportedTypes = Arrays.asList(new String[] {"Instance"});
	
	public  SimpleInstanceImpl unserialize(final JASEDataObject jdo) {
		return new SimpleInstanceImpl(jdo.getObject());
	}

	@Override
	public JASEDataObject serialize(SimpleInstanceImpl object) {
		try {
			return new JASEDataObject("Instance", new ObjectMapper().readTree(object.toJson()));
		} catch (IOException e) { 
			throw new RuntimeException(e.getMessage(), e); // mask checked exception
		}
	}

	@Override
	public Collection<String> getSupportedSemanticTypes() {
		return supportedTypes;
	}
}
