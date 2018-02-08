package de.upb.crc901.services;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import com.fasterxml.jackson.core.JsonEncoding;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import de.upb.crc901.services.streamhandlers.InstanceStreamHandler;
import de.upb.crc901.services.streamhandlers.InstancesStreamHandler;
import de.upb.crc901.services.streamhandlers.LabeledInstanceStreamHandler;
import de.upb.crc901.services.streamhandlers.LabeledInstancesStreamHandler;
import de.upb.crc901.services.streamhandlers.StringListStreamHandler;
import jaicore.ml.core.SimpleInstanceImpl;
import jaicore.ml.core.SimpleInstancesImpl;
import jaicore.ml.core.SimpleLabeledInstanceImpl;
import jaicore.ml.core.SimpleLabeledInstancesImpl;
import jaicore.ml.interfaces.Instance;
import jaicore.ml.interfaces.Instances;
import jaicore.ml.interfaces.LabeledInstance;
import jaicore.ml.interfaces.LabeledInstances;

public class StreamhandlerTests {
	Instance instance = null;
	Instances instances = null;
	LabeledInstance<String> linstance = null;
	LabeledInstances<String> linstances = null;
	List<String> stringList = null;
	
	@Before
	public void setup() {
		// Create some arbitrary test data:
		
		instances = new SimpleInstancesImpl();
		stringList = new ArrayList<>();
		linstances = new  SimpleLabeledInstancesImpl();
		for(int j = 0; j < 10; j ++) {
			instance = new SimpleInstanceImpl();
			linstance = new SimpleLabeledInstanceImpl();
			for(int i = 0; i < 20; i ++) {
				instance.add((double) i + j * 20);
				linstance.add((double) i + j * 20);
			}
			linstance.setLabel("label" + j);
			stringList.add("label" + j);
			instances.add(instance);
			linstances.add(linstance);
		}
	}
	@Test
	public void testWrite() throws IOException {
		JsonFactory jfactory = new JsonFactory();
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		JsonGenerator jGenerator = jfactory
		  .createGenerator(out, JsonEncoding.UTF8);
		// instance test
		InstanceStreamHandler handler = new InstanceStreamHandler();
		handler.write(jGenerator, instance );
		jGenerator.flush();
		
		String sentJson = out.toString();
		System.out.println(sentJson);
		SimpleInstanceImpl instance2 = new SimpleInstanceImpl(sentJson);
		Assert.assertEquals(instance, instance2);
		
		out.reset();
		// instances test
		InstancesStreamHandler handler2 = new InstancesStreamHandler();
		handler2.write(jGenerator, instances);
		jGenerator.flush();
		
		sentJson = out.toString();
		System.out.println(sentJson);
		SimpleInstancesImpl instances2 = new SimpleInstancesImpl(sentJson);
		Assert.assertEquals(instances, instances2);
		
		out.reset();
		// labeled instance test
		LabeledInstanceStreamHandler handler3 = new LabeledInstanceStreamHandler();
		handler3.write(jGenerator, linstance);
		jGenerator.flush();
		
		sentJson = out.toString();
		System.out.println(sentJson);
		SimpleLabeledInstanceImpl linstance2 = new SimpleLabeledInstanceImpl(sentJson);
		Assert.assertEquals(instance, linstance2);

		out.reset();
		// labeled instances test
		LabeledInstancesStreamHandler handler4 = new LabeledInstancesStreamHandler();
		handler4.write(jGenerator, linstances);
		jGenerator.flush();

		sentJson = out.toString();
		System.out.println(sentJson);
		SimpleLabeledInstancesImpl linstances2 = new SimpleLabeledInstancesImpl(sentJson);
		Assert.assertEquals(instances, linstances2);
		
		out.reset();
		// string list test
		StringListStreamHandler handler5 = new StringListStreamHandler();
		handler5.write(jGenerator, stringList);
		jGenerator.flush();

		sentJson = out.toString();
		System.out.println(sentJson);
		List<String> stringlist2 = new ObjectMapper().
				readValue(sentJson, 
				new TypeReference<ArrayList<String>>(){});
		
		
		Assert.assertEquals(stringList, stringlist2);
	}
	
	@Test
	public void testRead() throws JsonParseException, IOException {
		
		JsonFactory jfactory = new JsonFactory();
		// test instance
		JsonParser jParser = jfactory.createParser(instance.toJson());
		InstanceStreamHandler handler1 = new InstanceStreamHandler();
		Instance instance2 = handler1.read(jParser);
		Assert.assertEquals(instance, instance2);
		// ..  TODO write tests for all the other readers
	}
	
	
	
	
}
