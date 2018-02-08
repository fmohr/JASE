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
import jaicore.ml.interfaces.LabeledInstance;
import weka.core.Instance;

public class InstanceOntologySerializer implements IOntologySerializer<Instance> {
	private static final List<String> supportedTypes = Arrays.asList(new String[] {"Instance", "LabeledInstance"});
	
	public Instance unserialize(final JASEDataObject jdo) {
		if(jdo.getData() instanceof Instance) {
			jaicore.ml.interfaces.Instance data = (jaicore.ml.interfaces.Instance) jdo.getData();
			Instance inst =  WekaUtil.fromJAICoreInstance(data);
			assert inst.dataset() != null : "No dataset assigned to instance " + inst;
			return inst;
		} else if (jdo.getData() instanceof LabeledInstance<?>) {
			LabeledInstance<String> data = (LabeledInstance<String>) jdo.getData();
			Instance inst = WekaUtil.fromJAICoreInstance(data);
			assert inst.dataset() != null : "No dataset assigned to instance " + inst;
			return inst;
		}
		else {
			typeMismatch(jdo);
			return null;
		}
		
	}

	public JASEDataObject serialize(final Instance instance) {
		if (instance.classIndex() < 0)
			return new JASEDataObject("Instance", WekaUtil.toJAICoreInstance(instance));
		else
			return new JASEDataObject("LabeledInstance", WekaUtil.toJAICoreLabeledInstance(instance));
	}

	@Override
	public Collection<String> getSupportedSemanticTypes() {
		return supportedTypes;
	}
}
