package de.upb.crc901.services.typeserializers;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import org.apache.commons.lang3.NotImplementedException;

import com.fasterxml.jackson.databind.ObjectMapper;

import de.upb.crc901.services.ExchangeTest;
import de.upb.crc901.services.core.IOntologySerializer;
import de.upb.crc901.services.core.JASEDataObject;
import jaicore.ml.WekaUtil;
import jaicore.ml.core.SimpleInstancesImpl;
import jaicore.ml.core.SimpleLabeledInstancesImpl;
import jaicore.ml.core.WekaCompatibleInstancesImpl;
import jaicore.ml.interfaces.LabeledInstances;
import weka.core.Instance;
import weka.core.Instances;

public class InstancesOntologySerializer implements IOntologySerializer<Instances>  {
	
	private static final List<String> supportedTypes = Arrays.asList(new String[] {"Instances", "LabeledInstances"});
	
	public  Instances unserialize(final JASEDataObject jdo) {
		ExchangeTest.STOP_TIME("labeledinstances -> wekainstance started");
		if(jdo.getData() instanceof jaicore.ml.interfaces.Instances) {
			ExchangeTest.STOP_TIME("unserialize done");
			return WekaUtil.fromJAICoreInstances((jaicore.ml.interfaces.Instances)jdo.getData());
		} else if(jdo.getData() instanceof LabeledInstances<?>) {
			throw new NotImplementedException("There is no bridge between labeledinstances and weka instances yet.");
		}
		else {
			throw typeMismatch(jdo);
		}
	}

	public JASEDataObject serialize(final Instances wekaInstances) {
		ExchangeTest.STOP_TIME("wekainstance -> labeledinstances with size: " + wekaInstances.size() + " started");
		if (wekaInstances.classIndex() < 0)
			return new JASEDataObject("Instances", WekaUtil.toJAICoreInstances(wekaInstances));
		else {
			LabeledInstances<String> linstances = new SimpleLabeledInstancesImpl();
			for (Instance inst : wekaInstances) {
				linstances.add(WekaUtil.toJAICoreLabeledInstance(inst));
		    }
			ExchangeTest.STOP_TIME("serialize done");
			return new JASEDataObject("LabeledInstances", linstances);
		}
	}

	@Override
	public Collection<String> getSupportedSemanticTypes() {
		return supportedTypes;
	}
}
