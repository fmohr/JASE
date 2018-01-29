package de.upb.crc901.services.typeserializers;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;

import de.upb.crc901.services.core.IOntologySerializer;
import de.upb.crc901.services.core.JASEDataObject;
import jaicore.ml.WekaUtil;
import jaicore.ml.core.SimpleLabeledInstanceImpl;
import weka.core.Instance;

public class InstanceOntologySerializer implements IOntologySerializer<Instance> {
	private static final List<String> supportedTypes = Arrays.asList(new String[] {"Instance", "LabeledInstance"});
	
	public Instance unserialize(final JASEDataObject json) {
		SimpleLabeledInstanceImpl data = new SimpleLabeledInstanceImpl(json.getObject());
		Instance inst = WekaUtil.fromJAICoreInstance(data);
		assert inst.dataset() != null : "No dataset assigned to instance " + inst;
		return inst;
	}

	public JASEDataObject serialize(final Instance instance) {
		try {
			if (instance.classIndex() < 0)
				return new JASEDataObject("Instance", new ObjectMapper().readTree(WekaUtil.toJAICoreInstance(instance).toJson()));
			else
				return new JASEDataObject("LabeledInstance", new ObjectMapper().readTree(WekaUtil.toJAICoreLabeledInstance(instance).toJson()));
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
