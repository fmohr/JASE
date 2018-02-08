package de.upb.crc901.services.streamhandlers;

import java.io.IOException;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;

import de.upb.crc901.services.core.StreamHandler;
import jaicore.ml.core.SimpleInstanceImpl;
import jaicore.ml.interfaces.Instance;

/**
 * Streamhandler implementation for the semantic type: Instance
 * 
 * Example of instance: [1.0,2.0,3.0]
 * 
 * @author aminfaez
 *
 */
public class InstanceStreamHandler implements StreamHandler<Instance> {
	
	
	@Override
	public Instance read(JsonParser jsonIn) throws IOException {
		Instance instance = new SimpleInstanceImpl();
		read(jsonIn, instance);
		return instance;
	}
	
	/**
	 * Overloaded read method to be used with LabeledInstance instead of only with instance.
	 */
	public void read(JsonParser jsonIn, Instance instance) throws IOException {
		assert (jsonIn.currentToken() == JsonToken.START_ARRAY);
		
		while(jsonIn.nextToken() != JsonToken.END_ARRAY) {
			JsonToken token = jsonIn.currentToken();
			if(token.isNumeric()) {
				instance.add(jsonIn.getNumberValue().doubleValue());
			}
			else {
				throw new IOException("Type mismatch: " + token.asString() + " isn't numeric.");
			}
		}
	}

	@Override
	public void write(JsonGenerator jsonOut, Instance data) throws IOException {
		Instance instance = (Instance) data;
		jsonOut.writeStartArray();
		for(Double value : instance) {
			jsonOut.writeNumber(value);
		}
		jsonOut.writeEndArray();
	}

	@Override
	public Class<Instance> getSupportedSemanticClass() {
		return Instance.class;
	}


}
