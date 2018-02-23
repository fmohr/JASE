package de.upb.crc901.services.wrappers;

import java.lang.reflect.Constructor;

import de.upb.crc901.services.core.JASEDataObject;
import de.upb.crc901.services.core.ServiceWrapper;
import weka.attributeSelection.ASEvaluation;
import weka.attributeSelection.ASSearch;
import weka.attributeSelection.AttributeSelection;
import weka.core.Attribute;
import weka.core.Instance;
import weka.core.Instances;

public class WekaAttributeSelectionWrapper extends ServiceWrapper {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	

	private Attribute cachedClassAttribute;
	private Instances cachedInstances;
	
	public WekaAttributeSelectionWrapper(Constructor<? extends Object> delegateConstructor, Object[] values) {
		super(delegateConstructor, values);
	}
	
	
	public void SelectAttributes(Instances instances) throws Exception {
		if(cachedClassAttribute==null) {
			cachedClassAttribute = instances.classAttribute();
			cachedInstances = new Instances(instances, 0);
		}
		((AttributeSelection)delegate).SelectAttributes(instances);
	}
	
	public Instance reduceDimensionality(Instance instance) throws Exception {
		if(cachedInstances==null) {
			throw new RuntimeException("First call SelectAttribute");
		}
		String label = instance.dataset().classAttribute().value(0);
		instance.setDataset(cachedInstances);
		instance.setClassValue(label);
		return ((AttributeSelection)delegate).reduceDimensionality(instance);
	}
	
	public Instances reduceDimensionality(Instances instances) throws Exception {
		if(cachedInstances==null) {
			throw new RuntimeException("First call SelectAttribute");
		}
		for(Instance instance : instances) {
			instance.setDataset(cachedInstances);
		}
		return ((AttributeSelection)delegate).reduceDimensionality(instances);
	}
	
	protected void buildDelegate() {
		if(constructorValues.length != 2) {
			throw new RuntimeException("Given length is: " +  constructorValues.length);
		}
		super.buildDelegate();
		AttributeSelection selection = (AttributeSelection) getDelegate();
		String asSearcherClasspath = ((JASEDataObject) constructorValues[0]).getData().toString();
		String asEvaluatorClasspath = ((JASEDataObject) constructorValues[1]).getData().toString(); 
		try {
			ASSearch asSearch = ASSearch.forName(asSearcherClasspath, new String[0]);
			selection.setSearch(asSearch);
		} catch (Exception e) {
			e.printStackTrace();
			throw new RuntimeException(e);
		}
		try {
			ASEvaluation asEvaluator = ASEvaluation.forName(asEvaluatorClasspath, new String[0]);
			selection.setEvaluator(asEvaluator);
		} catch (Exception e) {
			e.printStackTrace();
			throw new RuntimeException(e);
		}
	}

}
