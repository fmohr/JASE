package de.upb.crc901.services.wrappers;

import java.lang.reflect.Constructor;

import de.upb.crc901.services.core.JASEDataObject;
import de.upb.crc901.services.core.ServiceWrapper;
import weka.attributeSelection.ASEvaluation;
import weka.attributeSelection.ASSearch;
import weka.attributeSelection.AttributeSelection;

public class WekaAttributeSelectionWrapper extends ServiceWrapper {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	public WekaAttributeSelectionWrapper(Constructor<? extends Object> delegateConstructor, Object[] values) {
		super(delegateConstructor, values);
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
