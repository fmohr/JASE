package de.upb.crc901.services.typeserializers;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Enumeration;
import java.util.List;

import org.apache.commons.lang3.NotImplementedException;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Lists;

import de.upb.crc901.services.ExchangeTest;
import de.upb.crc901.services.core.IOntologySerializer;
import de.upb.crc901.services.core.JASEDataObject;
import de.upb.crc901.services.core.TimeLogger;
import jaicore.ml.WekaUtil;
import jaicore.ml.core.SimpleInstancesImpl;
import jaicore.ml.core.SimpleLabeledInstanceImpl;
import jaicore.ml.core.SimpleLabeledInstancesImpl;
import jaicore.ml.core.WekaCompatibleInstancesImpl;
import jaicore.ml.interfaces.LabeledInstances;
import weka.core.Attribute;
import weka.core.DenseInstance;
import weka.core.Instance;
import weka.core.Instances;
import weka.filters.Filter;
import weka.filters.unsupervised.attribute.NominalToBinary;

public class InstancesOntologySerializer implements IOntologySerializer<Instances>  {
	
	private static final List<String> supportedTypes = Arrays.asList(new String[] {"Instances", "LabeledInstances"});
	
	public  Instances unserialize(final JASEDataObject jdo) {
//		TimeLogger.STOP_TIME("labeledinstances -> wekainstance started");
		if(jdo.getData() instanceof jaicore.ml.interfaces.Instances) {
//			TimeLogger.STOP_TIME("unserialize done");
			return WekaUtil.fromJAICoreInstances((jaicore.ml.interfaces.Instances)jdo.getData());
		} else if(jdo.getData() instanceof LabeledInstances<?>) {
			LabeledInstances<String> data = (LabeledInstances<String>) jdo.getData();
			int attributeCount = data.getNumberOfColumns() + 1; // the amount of attributes including the class label.
			/* create basic attribute entries */
			ArrayList<Attribute> attributeList = new ArrayList<>(attributeCount);
			for (int i = 1; i <= attributeCount; i++) {
				attributeList.add(new Attribute("a" + i));
			}
			ArrayList<String> classes = Lists.newArrayList(data.getOccurringLabels());
			Attribute classAttribute = new Attribute("label", classes);
			
			attributeList.add(classAttribute);
			
			weka.core.Instances wekaInstances = new Instances("JAICore-extracted dataset", attributeList,
					data.getNumberOfRows());
			wekaInstances.setClassIndex(attributeCount - 1); // the last item is the class attribute.
			// take every labeled instance and put it into the wekaInstances.
			for (jaicore.ml.interfaces.LabeledInstance<String> labeledInstance : data) {
				double[] values = new double[attributeCount];
				for (int i = 0; i < attributeCount - 1; i++) {
					values[i] = labeledInstance.get(i);
				}
				weka.core.Instance wekaInstance = new DenseInstance(1.0, values);
				// classValue in a weka.core.Instance is the index of the class value.
				Double classIndex = new Double(classAttribute.indexOfValue(labeledInstance.getLabel()));
				wekaInstance.setDataset(wekaInstances);
				wekaInstance.setClassValue(classIndex);
				wekaInstances.add(wekaInstance);
			}
			return wekaInstances;
		}
		else {
			throw typeMismatch(jdo);
		}
	}

	public JASEDataObject serialize(Instances wekaInstances) {
//		TimeLogger.STOP_TIME("wekainstance -> labeledinstances with size: " + wekaInstances.size() + " started");
		if (wekaInstances.classIndex() < 0) {
//			return new JASEDataObject("Instances", WekaUtil.toJAICoreInstances(wekaInstances));
			throw new RuntimeException();
		}
		else {
			LabeledInstances<String> linstances = new SimpleLabeledInstancesImpl();
			weka.filters.unsupervised.attribute.NominalToBinary toBinFilter = new weka.filters.unsupervised.attribute.NominalToBinary();
			
			try {
				toBinFilter.setInputFormat(wekaInstances);
				wekaInstances = Filter.useFilter(wekaInstances, toBinFilter);
			} catch (Exception e) {
				e.printStackTrace();
				throw new RuntimeException(e);
			}
			String[] classValues; // caches the class values.
			{
				Attribute classAttribute = wekaInstances.classAttribute();
				classValues = new String[classAttribute.numValues()];
				Enumeration<Object> classValueEnumerator = classAttribute.enumerateValues();
				int index = 0;
				while(classValueEnumerator.hasMoreElements()) {
					classValues[index] = (String) classValueEnumerator.nextElement();
					index ++;
				}
			}
			
			for (Instance wekaInst : wekaInstances) {
				jaicore.ml.interfaces.LabeledInstance<String> inst = new SimpleLabeledInstanceImpl();
				for (int att = 0; att < wekaInst.numAttributes() && att != wekaInst.classIndex(); att++) {
					inst.add(wekaInst.value(att));
				}
				double classValue = wekaInst.classValue();
				inst.setLabel(classValues[(int) classValue]);
				linstances.add(inst);
		    }
			return new JASEDataObject("LabeledInstances", linstances);
		}
	}

	@Override
	public Collection<String> getSupportedSemanticTypes() {
		return supportedTypes;
	}
}
