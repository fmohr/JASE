package de.upb.crc901.services.typeserializers;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import de.upb.crc901.services.core.IOntologySerializer;
import de.upb.crc901.services.core.JASEDataObject;
import jaicore.ml.core.SimpleLabeledInstancesImpl;

public class SimpleLabeledInstancesImplOntologySerializer implements IOntologySerializer<SimpleLabeledInstancesImpl>  {

	private static final List<String> supportedTypes = Arrays.asList(new String[] {"Instances"});
	
	public SimpleLabeledInstancesImpl unserialize(final JASEDataObject json) {
		SimpleLabeledInstancesImpl data = new SimpleLabeledInstancesImpl(json.getObject());
		return data;
	}

	public JASEDataObject serialize(final SimpleLabeledInstancesImpl instances) {
		try {
			JsonNode object = (new ObjectMapper().readTree(instances.toJson()));
			String type = "Instances";
			JASEDataObject jdo = new JASEDataObject(type, object);
			return jdo;
		} catch (IOException e) {
			e.printStackTrace();
		}
		return null;
	}
	
	public Collection<String> getSupportedSemanticTypes(){
		return supportedTypes;
	}
}
