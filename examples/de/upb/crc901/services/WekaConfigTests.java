package de.upb.crc901.services;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

import de.upb.crc901.services.core.HttpServiceClient;
import de.upb.crc901.services.core.HttpServiceServer;
import de.upb.crc901.services.core.OntologicalTypeMarshallingSystem;
import de.upb.crc901.services.core.ServiceCompositionResult;
import jaicore.ml.WekaUtil;
import weka.core.Instances;

/**
 * 
 * Tests the configuration of jase server regarding weka classifiers and preprocessors.
 * 
 * @author aminfaez
 *
 */
public class WekaConfigTests {
	private final static int PORT = 8000;


	private static HttpServiceServer server;

	private static HttpServiceClient client;
	private static final OntologicalTypeMarshallingSystem otms = new OntologicalTypeMarshallingSystem();
	
	private static Instances wekaInstances;
	
	// names of base classes in the configuration
	private static String baseClassifierConfigName = "$base_weka_classifier_config$";
	private static String basePreprocessorConfigName = "$base_weka_preprocessor_config$";
	
	
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

		wekaInstances.setClassIndex(wekaInstances.numAttributes() - 1);
		
		// print all classifiers and preprocessors that are enumerated in the classes configuration.
		System.out.println("WEKA CLASSFIERS:");
		for(String wekaClassifier : server.getClassesConfig().allSubconfigs(baseClassifierConfigName)) {
			System.out.println("\t" + wekaClassifier);
		}
		System.out.println("WEKA PREPROCESSORS:");
		for(String wekaClassifier : server.getClassesConfig().allSubconfigs(basePreprocessorConfigName)) {
			System.out.println("\t" + wekaClassifier);
		}
	}

	@AfterClass
	public static void tearDownAfterClass() throws Exception {
		server.shutdown();
	}
	
	@Test
	/**
	 * Iterates over all classifiers in the config and tries to train and evaluate them.
	 */
	public void testAllClassifiers() throws IOException {
		// prepare the data
		List<Instances> split = WekaUtil.getStratifiedSplit(wekaInstances, new Random(0), .9f);
		Set<String> errorSet = new HashSet<>();
		for(String wekaClassifierClasspath : server.getClassesConfig().allSubconfigs(baseClassifierConfigName)) {
			try {
				// Create classifier service
				String serviceId = client.callServiceOperation("localhost:" + PORT + "/" + wekaClassifierClasspath + "::__construct").get("out").asText();
				Assert.assertNotNull(serviceId);
				// train the classifier
				client.callServiceOperation(serviceId + "::train", split.get(0));
				// evaluate the classifier
				ServiceCompositionResult result =  client.callServiceOperation(serviceId + "::predict_and_score", split.get(1));
				Double score = new ObjectMapper().readValue(result.get("out").traverse(), Double.class);
				
				System.out.println("Accuracy of " + wekaClassifierClasspath + ": " + score);
			} catch (Exception ex) {
				errorSet.add(wekaClassifierClasspath);
			}
		}
		System.out.println("Error occurred with these classifiers:\n" + errorSet);
	}
}
