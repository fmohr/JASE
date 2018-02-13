package de.upb.crc901.services.core;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.upb.crc901.configurationsetting.logic.VariableParam;
import de.upb.crc901.configurationsetting.operation.OperationInvocation;

/**
 * Handles the logic behind executing operations.
 * @author aminfaez
 *
 */
public class Executioner {
	
	/** logger for execution. */
	private static final Logger logger = LoggerFactory.getLogger(Executioner.class);
	
	private OntologicalTypeMarshallingSystem otms;
	private EnvironmentState envState;
	
	public Executioner(){
		otms = new OntologicalTypeMarshallingSystem();
		envState = new EnvironmentState();
	}
	
	
	
	public JASEDataObject execute(OperationInvocation operation) {
		logger.info("Performing invocation {}", operation);
		// prepare inputs:
		List<VariableParam> inputs = operation.getOperation().getInputParameters();
//		JASEDataObject inputs = new JASEDataObject[]
		return null;
		 
	}
	
}
