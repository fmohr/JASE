package de.upb.crc901.services.core;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * EnvironmentState maps environment field names during an execution to their values. For example:
 * "a" : <Object of semantic type of Instances>, ...
 * 
 * @author aminfaez
 *
 */
public final class EnvironmentState {
	/**
	 * Contains field name that were part of the env state from the very beginning. 
	 * In a composition call startingFields is filled with the names of the inputs.
	 */
	private final Set<String> startingFields;
	
	/**
	 * The state of the environment.
	 * Variable names are mapped to their semantical objects.
	 */
	private final Map<String, JASEDataObject> envState;
	
	/**
	 * An empty env state.
	 */
	public EnvironmentState() {
		startingFields = new HashSet<>();
		envState = new HashMap<>();
	}
	
	/**
	 * An env state filled with the objects from starting state.
	 * @param startingState
	 */
	public EnvironmentState(Map<String, JASEDataObject> startingState) {
		startingFields = new HashSet<>(startingState.keySet()); // copy starting field names into a new set.
		envState = new HashMap<>(startingState); // copy the given map into a new map.
	}
	
	/**
	 * Returns true if the given fieldname was included in the starting field.
	 */
	public boolean containedAtStart(String fieldName) {
		return startingFields.contains(fieldName);
	}
	
	/**
	 * Returns true if the given fieldname maps to a variable in this env state.
	 */
	public boolean containsField(String fieldName) {
		return envState.containsKey(fieldName);
	}
	
	/**
	 * Returns an iterable object of all field names that are among the starting fields.
	 */
	public Iterable<String> startingFieldNames() {
		return startingFields;
	}
	
	/**
	 * Returns an iterable object of all currently contained field names that map to a object of semantic type.
	 */
	public Iterable<String> currentFieldNames() {
		return envState.keySet();
	}
	
	
	/**
	 * Returns an iterable object of all field name that were added to this state after it's creation.
	 * @return
	 */
	public Iterable<String> addedFieldNames(){
		// return a anonymous sub type of Iterable that returns FileteredIterator in its 'iterator()' method.
		return () -> { 
			return new FilteredIterator<String>(
					currentFieldNames().iterator(),  	// iterator of all current fieldnames
					(s -> !containedAtStart(s))); 		// filter out every fieldname that was contained at start.
		};
	}
	
	/**
	 * Adds the given JASEDataObject to the state with the given field name.
	 */
	public void addField(String newFieldName, JASEDataObject newVar) {
		envState.put(newFieldName, newVar);
	}
	
	
	
	
}
