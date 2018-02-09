package de.upb.crc901.services;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringBufferInputStream;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import de.upb.crc901.services.core.HttpBody;
import de.upb.crc901.services.core.JASEDataObject;
import jaicore.ml.core.SimpleInstanceImpl;
import jaicore.ml.core.SimpleInstancesImpl;
import jaicore.ml.core.SimpleLabeledInstanceImpl;
import jaicore.ml.core.SimpleLabeledInstancesImpl;
import jaicore.ml.interfaces.Instance;
import jaicore.ml.interfaces.Instances;
import jaicore.ml.interfaces.LabeledInstance;
import jaicore.ml.interfaces.LabeledInstances;

public class ExchangeTest {
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
		for(int j = 0; j < 2; j ++) {
			instance = new SimpleInstanceImpl();
			linstance = new SimpleLabeledInstanceImpl();
			for(int i = 0; i < 3; i ++) {
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
	public void basicTest() throws IllegalAccessException, IllegalArgumentException, InvocationTargetException, ClassNotFoundException, InstantiationException, NoSuchMethodException, SecurityException, IOException {
		HttpBody body = new HttpBody();
		body.setComposition("something");
		body.setCurrentIndex(2);
		body.addKeyworkArgument("a", new JASEDataObject("Instances", instances));
		body.addUnparsedKeywordArgument(null, "i1",1);
		body.addPositionalArgument(new JASEDataObject("LabeledInstances", linstances));
		
		body.writeBody(System.out);
		
		
        String bodyString = "{\"currentindex\":2,\"maxindex\":-1,\"inputs\":{\"$arg_list$\":[{\"type\":\"LabeledInstances\",\"data\":{\"instances\":[[0.0,1.0,2.0],[20.0,21.0,22.0]],\"labels\":[\"label0\",\"label1\"]}}],\"a\":{\"type\":\"Instances\",\"data\":[[0.0,1.0,2.0],[20.0,21.0,22.0]]},\"i1\":{\"type\":\"primitive\",\"data\":1}},\"choreography\":\"something\"}";
		InputStream in  = new ByteArrayInputStream(bodyString.getBytes());
        HttpBody body2 = new HttpBody();
		body2.readfromBody(in);
		Assert.assertEquals(body, body2);
	}
}
