package de.upb.crc901.services.typeserializers;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;

import de.upb.crc901.services.core.IOntologySerializer;
import de.upb.crc901.services.core.JASEDataObject;
import jaicore.ml.WekaUtil;
import jaicore.ml.core.WekaCompatibleInstancesImpl;
import weka.core.Instances;

public class InstancesOntologySerializer implements IOntologySerializer<Instances>  {
	
	private static final List<String> supportedTypes = Arrays.asList(new String[] {"Instances", "LabeledInstances"});
	
	public  Instances unserialize(final JASEDataObject jdo) {
		WekaCompatibleInstancesImpl data = new WekaCompatibleInstancesImpl(jdo.getObject());
		return WekaUtil.fromJAICoreInstances(data);
	}

	public JASEDataObject serialize(final Instances instances) {
		try {
			if (instances.classIndex() < 0)
				return new JASEDataObject("Instances", new ObjectMapper().readTree(WekaUtil.toJAICoreInstances(instances).toJson()));
			else
				return new JASEDataObject("LabeledInstances", new ObjectMapper().readTree(WekaUtil.toJAICoreLabeledInstances(instances).toJson()));
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
