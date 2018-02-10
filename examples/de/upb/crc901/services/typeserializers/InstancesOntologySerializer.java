package de.upb.crc901.services.typeserializers;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import org.apache.commons.lang3.NotImplementedException;

import com.fasterxml.jackson.databind.ObjectMapper;

import de.upb.crc901.services.core.IOntologySerializer;
import de.upb.crc901.services.core.JASEDataObject;
import jaicore.ml.WekaUtil;
import jaicore.ml.core.WekaCompatibleInstancesImpl;
import jaicore.ml.interfaces.LabeledInstances;
import weka.core.Instances;

public class InstancesOntologySerializer implements IOntologySerializer<Instances>  {
	
	private static final List<String> supportedTypes = Arrays.asList(new String[] {"Instances", "LabeledInstances"});
	
	public  Instances unserialize(final JASEDataObject jdo) {
		if(jdo.getData() instanceof jaicore.ml.interfaces.Instances) {
			return WekaUtil.fromJAICoreInstances((jaicore.ml.interfaces.Instances)jdo.getData());
		} else if(jdo.getData() instanceof LabeledInstances<?>) {
			throw new NotImplementedException("There is no bridge between labeledinstances and weka instances yet.");
		}
		else {
			throw typeMismatch(jdo);
		}
	}

	public JASEDataObject serialize(final Instances instances) {
		if (instances.classIndex() < 0)
			return new JASEDataObject("Instances", WekaUtil.toJAICoreInstances(instances));
		else
			return new JASEDataObject("LabeledInstances", WekaUtil.toJAICoreLabeledInstances(instances));
	}

	@Override
	public Collection<String> getSupportedSemanticTypes() {
		return supportedTypes;
	}
}
