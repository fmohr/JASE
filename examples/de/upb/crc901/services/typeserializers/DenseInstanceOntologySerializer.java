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
import jaicore.ml.interfaces.LabeledInstance;
import weka.core.DenseInstance;

public class DenseInstanceOntologySerializer implements IOntologySerializer<DenseInstance> {

	private static final List<String> supportedTypes = Arrays.asList(new String[] {"Instance", "LabeledInstance"});
	
	public DenseInstance unserialize(final JASEDataObject jdo) {
		if(jdo.getData() instanceof LabeledInstance<?>) {
			// Parse the inner JsonNode into LabeledInstance
			LabeledInstance<String> data = (SimpleLabeledInstanceImpl) jdo.getData();
			DenseInstance inst = new DenseInstance(WekaUtil.fromJAICoreInstance(data));
			assert inst.dataset() != null : "No dataset assigned to instance " + inst;
			return inst;
		}
		else {
			typeMismatch(jdo);
			return null;
		}
	}

	public JASEDataObject serialize(final DenseInstance instance) {
		Object object;
		String type;
		if (instance.classIndex() < 0) {
			object = WekaUtil.toJAICoreInstance(instance);
			type = "Instance";
		}
		else {
			object = WekaUtil.toJAICoreLabeledInstance(instance);
			type = "LabeledInstance";
		}
		JASEDataObject jdo = new JASEDataObject(type, object);
		return jdo;
	}

	@Override
	public Collection<String> getSupportedSemanticTypes() {
		return supportedTypes;
	}
}
