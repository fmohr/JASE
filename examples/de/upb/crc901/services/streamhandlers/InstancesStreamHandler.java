package de.upb.crc901.services.streamhandlers;

import java.io.IOException;
import java.util.List;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;

import de.upb.crc901.services.core.StreamHandler;
import jaicore.ml.core.SimpleInstancesImpl;
import jaicore.ml.interfaces.Instance;
import jaicore.ml.interfaces.Instances;

/**
 * Streamhandler implementation for the semantic type: Instances
 * 
 * Example of instances: [[1.0,2.0,3.0],[4.0,5.0,6.0]]
 * 
 * @author aminfaez
 *
 */
public class InstancesStreamHandler implements StreamHandler<Instances>{
	
	InstanceStreamHandler instanceHandlerDelegate = new InstanceStreamHandler();
	
	@Override
	public Instances read(JsonParser jsonIn) throws IOException {
		Instances instances = new SimpleInstancesImpl();
		while(jsonIn.nextToken() != JsonToken.END_ARRAY) {
			Instance instance =  instanceHandlerDelegate.read(jsonIn);
			instances.add(instance);
		}
		return instances;
	}
	

	@Override
	public void write(JsonGenerator jsonOut, Instances data) throws IOException {
		
		writeList(jsonOut, data);
	}
	
	/**
	 * Overloaded method to be used with any list of instance.
	 */
	public void writeList(JsonGenerator jsonOut, List<? extends Instance> data) throws IOException {
		jsonOut.writeStartArray();
		for(Instance instance : data) {
			instanceHandlerDelegate.write(jsonOut, instance,  shouldBeSparsed(data));
		}
		jsonOut.writeEndArray();
	}

	@Override
	public Class<Instances> getSupportedSemanticClass() {
		return Instances.class;
	}
	
	private boolean shouldBeSparsed(List<? extends Instance> data) {
		int testrange = 10;
		if(data.size() < testrange) {
			return false; // dont sparse small data
		}
		
		double zeroRatio = 0;
		double eachZeroRatio = 1. / ((double)(testrange*data.get(0).size())); // how much each 0 entry contribute
		for(int index = 0; index < testrange; index++) {
			Instance instance = data.get(index);
			for(double value : instance) {
				if(value == 0) {
					zeroRatio += eachZeroRatio;
				}
			}
		}
		System.out.println("\n\n\n------------->Zero ratio : "  + zeroRatio +"\n\n\n");
		return (zeroRatio > 0.5);
	}

}
