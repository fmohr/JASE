package de.upb.crc901.services;

import static org.junit.Assert.*;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.List;
import java.util.Random;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import de.upb.crc901.configurationsetting.operation.SequentialComposition;
import de.upb.crc901.configurationsetting.serialization.SequentialCompositionSerializer;
import de.upb.crc901.services.core.HttpServiceClient;
import de.upb.crc901.services.core.OntologicalTypeMarshallingSystem;
import de.upb.crc901.services.core.ServiceCompositionResult;
import jaicore.basic.FileUtil;
import jaicore.ml.WekaUtil;
import weka.core.Instances;

/**
 * Contains a deployment test case for presentation purposes.
 * 
 * @author aminfaez
 *
 */
public class DeployTests {
	private static HttpServiceClient client;
	private static final OntologicalTypeMarshallingSystem otms = new OntologicalTypeMarshallingSystem();
	private static Instances wekaInstances;
	@BeforeClass
	public static void setUpBeforeClass() throws Exception {
		client = new HttpServiceClient(otms);
		wekaInstances = new Instances(
				new BufferedReader(new FileReader(
						"../CrcTaskBasedConfigurator/testrsc" +
								File.separator + "polychotomous" +
								File.separator + "audiology.arff")));	
		wekaInstances.setClassIndex(wekaInstances.numAttributes() - 1);
		
	}

	@AfterClass
	public static void tearDownAfterClass() throws Exception {
	}

	@Before
	public void setUp() throws Exception {
	}

	@After
	public void tearDown() throws Exception {
	}

	@Test
	public void testPase() throws FileNotFoundException, IOException {
		/* prepare data */
		List<Instances> split = WekaUtil.getStratifiedSplit(wekaInstances, new Random(0), .1f, .8f);
		
		/* load composition */
		List<String> compositionList = FileUtil.readFileAsList("testrsc/deployPase.txt");
		SequentialCompositionSerializer sqs = new SequentialCompositionSerializer();
        SequentialComposition paseComposition = sqs.readComposition(compositionList);
        
        ServiceCompositionResult result = client.invokeServiceComposition(paseComposition, split.get(0), split.get(1), split.get(2));
        System.out.println("Prediction accuracy: " + result.get("Accuracy").asText());
	}

}
