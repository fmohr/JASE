package de.upb.crc901.services;

import static org.junit.Assert.*;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import de.upb.crc901.configurationsetting.operation.SequentialComposition;
import de.upb.crc901.configurationsetting.serialization.SequentialCompositionSerializer;
import de.upb.crc901.services.core.HttpServiceClient;
import de.upb.crc901.services.core.HttpServiceServer;
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
	private final static int PORT = 8000;


	private static HttpServiceServer server;

	private static HttpServiceClient client;
	private static final OntologicalTypeMarshallingSystem otms = new OntologicalTypeMarshallingSystem();
	
	private static Instances wekaInstances;
	
	@BeforeClass
	public static void setUpBeforeClass() throws Exception {
		/* start server */
		server = HttpServiceServer.TEST_SERVER();
		
		client = new HttpServiceClient(otms);
		wekaInstances = new Instances(
				new BufferedReader(new FileReader(
						"../CrcTaskBasedConfigurator/testrsc" +
								File.separator + "polychotomous" +
								File.separator + "audiology.arff")));	
//								File.separator + "secom" +
//								File.separator + "test.arff")));

		wekaInstances.setClassIndex(wekaInstances.numAttributes() - 1);
		
	}

	@AfterClass
	public static void tearDownAfterClass() throws Exception {
		server.shutdown();
	}

	@Before
	public void setUp() throws Exception {
	}

	@After
	public void tearDown() throws Exception {
	}

	@Test
	public void test_preproPase_classifyPase() throws FileNotFoundException, IOException {
		List<Instances> split = WekaUtil.getStratifiedSplit(wekaInstances, new Random(0), .1f, .8f);
		ServiceCompositionResult result = executeComposition("testrsc/deployPase.txt", split.get(0), split.get(1), split.get(2));
		System.out.println("Prediction accuracy Scikit RandomForestClassifier: " + result.get("Accuracy").asText());
	}
	
	@Test
	public void test_preproPase_classifyJase() throws FileNotFoundException, IOException {
		List<Instances> split = WekaUtil.getStratifiedSplit(wekaInstances, new Random(0), .1f, .8f);
		ServiceCompositionResult result = executeComposition("testrsc/deployJase.txt", split.get(0), split.get(1), split.get(2));
		System.out.println("Prediction accuracy Weka RandomForest: " + result.get("Accuracy").asText());
	}
	
	@Test
	public void test_nn_tensorflow() throws IOException {
		List<Instances> split = WekaUtil.getStratifiedSplit(wekaInstances, new Random(0), .8f, .14f, 0.05f);
		ServiceCompositionResult result = executeComposition("testrsc/nn_tf.txt", split.get(0), split.get(1), split.get(2));
		System.out.println("Prediction accuracy MLP TF: " + result.get("Accuracy").asText());
		List<Double> predictions = new ObjectMapper().readValue(result.get("Predictions").get("data").traverse(), new TypeReference<ArrayList<String>>(){});
		//System.out.println("Predictions by NN TF: " + predictions);
		
	}
	
	@Test
	public void test_nn_weka() throws IOException {
		List<Instances> split = WekaUtil.getStratifiedSplit(wekaInstances, new Random(0), .8f, .14f, 0.05f);
		ServiceCompositionResult result = executeComposition("testrsc/nn_weka.txt", split.get(0), split.get(1), split.get(2));
		System.out.println("Prediction accuracy MLP Weka: " + result.get("Accuracy").asText());
		List<Double> predictions = new ObjectMapper().readValue(result.get("Predictions").get("data").traverse(), new TypeReference<ArrayList<String>>(){});
		//System.out.println("Predictions by NN TF: " + predictions);
	}
	
	@Test
	public void test_nn_scikit() throws IOException {
		List<Instances> split = WekaUtil.getStratifiedSplit(wekaInstances, new Random(0), .1f, .7f, 0.2f);
		ServiceCompositionResult result = executeComposition("testrsc/nn_sk.txt", split.get(0), split.get(1), split.get(2));
		System.out.println("Prediction accuracy MLP SK: " + result.get("Accuracy").asText());
	}
	
	
	private ServiceCompositionResult executeComposition(String filePath, Object...inputs) throws IOException {
		/* load composition */
		List<String> compositionList = FileUtil.readFileAsList(filePath);
		SequentialCompositionSerializer sqs = new SequentialCompositionSerializer();
        SequentialComposition paseComposition = sqs.readComposition(compositionList);
        /* send composition with data to server */
        ServiceCompositionResult result = client.invokeServiceComposition(paseComposition, inputs);
        /* return the result */
        return result;
	}
	
	

}
