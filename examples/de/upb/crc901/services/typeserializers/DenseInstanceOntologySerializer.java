package de.upb.crc901.services.typeserializers;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import de.upb.crc901.services.core.IOntologySerializer;
import de.upb.crc901.services.core.JASEDataObject;
import jaicore.ml.WekaUtil;
import jaicore.ml.core.SimpleLabeledInstanceImpl;
import weka.core.DenseInstance;

public class DenseInstanceOntologySerializer implements IOntologySerializer<DenseInstance> {

	private static final List<String> supportedTypes = Arrays.asList(new String[] {"Instance", "LabeledInstance"});
	
	public DenseInstance unserialize(final JASEDataObject jdo) {
		// Parse the inner JsonNode into LabeledInstance
		SimpleLabeledInstanceImpl data = new SimpleLabeledInstanceImpl(jdo.getObject());
		DenseInstance inst = new DenseInstance(WekaUtil.fromJAICoreInstance(data));
		assert inst.dataset() != null : "No dataset assigned to instance " + inst;
		return inst;
	}

	public JASEDataObject serialize(final DenseInstance instance) {
		try {
			JsonNode object;
			String type;
			if (instance.classIndex() < 0) {
				object = new ObjectMapper().readTree(WekaUtil.toJAICoreInstance(instance).toJson());
				type = "Instance";
			}
			else {
				object = new ObjectMapper().readTree(WekaUtil.toJAICoreLabeledInstance(instance).toJson());
				type = "LabeledInstance";
			}
			JASEDataObject jdo = new JASEDataObject(type, object);
			return jdo;
		} catch (IOException e) {
			e.printStackTrace();
		}
		return null;
	}

	@Override
	public Collection<String> getSupportedSemanticTypes() {
		return supportedTypes;
	}
}
