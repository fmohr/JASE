package de.upb.crc901.services.mlpipeline;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.security.KeyStore.Entry.Attribute;
import java.util.HashMap;
import java.util.Map;

import de.upb.crc901.configurationsetting.operation.SequentialComposition;
import de.upb.crc901.configurationsetting.serialization.SequentialCompositionSerializer;
import de.upb.crc901.services.core.EasyClient;
import de.upb.crc901.services.core.HttpServiceClient;
import de.upb.crc901.services.core.HttpServiceServer;
import de.upb.crc901.services.core.OntologicalTypeMarshallingSystem;
import de.upb.crc901.services.core.ServiceCompositionResult;
import de.upb.crc901.services.core.ServiceHandle;
import jaicore.basic.FileUtil;
import weka.attributeSelection.ASSearch;
import weka.attributeSelection.AttributeSelection;
import weka.attributeSelection.PrincipalComponents;
import weka.attributeSelection.Ranker;
import weka.classifiers.Classifier;
import weka.core.Capabilities;
import weka.core.Instance;
import weka.core.Instances;

public class MLServicePipeline implements Classifier {

	private final HttpServiceClient client = new HttpServiceClient(new OntologicalTypeMarshallingSystem());
	private final ServiceHandle preprocessor;
	private final ServiceHandle classifier;

	public MLServicePipeline(String hostname, int port, String classifierName) {
		
		super();
		
		ServiceHandle preprocessorTmp = null;
		ServiceHandle classifierTmp = null;
		try {
			
			preprocessorTmp = new EasyClient()
							.withHost(hostname, port)
							.withClassPath("weka.attributeSelection.AttributeSelection")
							.withKeywordArgument("searcher", "weka.attributeSelection.Ranker")
							.withKeywordArgument("eval", "weka.attributeSelection.PrincipalComponents")
							.createService("searcher", "eval");
			
//			preprocessorTmp = ((ServiceHandle) client.callServiceOperation("localhost:800/weka.attributeSelection.AttributeSelection::__construct", "weka.attributeSelection.Ranker", "weka.attributeSelection.PrincipalComponents").get("out").getData());
			
			classifierTmp = new EasyClient()
					.withHost(hostname, port)
					.withClassPath(classifierName)
					.createService();
			
//			classifierTmp = ((ServiceHandle) client.callServiceOperation(classifierName + "::__construct").get("out").getData());
		} catch (IOException e) {
			e.printStackTrace();
		}
		preprocessor = preprocessorTmp;
		classifier = classifierTmp;
		System.out.println(preprocessor.getServiceAddress());
		System.out.println(classifier.getServiceAddress());
	}

	@Override
	public void buildClassifier(Instances data) throws Exception {
		
//		SequentialComposition comp = new SequentialCompositionSerializer().readComposition(FileUtil.readFileAsList("testrsc/pipeline_composition_train.txt"));
//		Map<String,Object> inputs = new HashMap<>();
//		inputs.put("s1", preprocessor);
//		inputs.put("s2", classifier);
//		inputs.put("i1", data);
//		client.invokeServiceComposition(comp, inputs);
		
		new EasyClient()
				.withCompositionFile("testrsc/pipeline_composition_train.txt")
				.withKeywordArgument("s1", preprocessor)
				.withKeywordArgument("s2", classifier)
				.withPositionalArgument(data)
				.withService(preprocessor)
				.dispatch();
				
	}

	@Override
	public double classifyInstance(Instance instance) throws Exception {
		
//		SequentialComposition comp = new SequentialCompositionSerializer().readComposition(FileUtil.readFileAsList("testrsc/pipeline_composition_predict.txt"));
//		Map<String,Object> inputs = new HashMap<>();
//		inputs.put("s1", preprocessor);
//		inputs.put("s2", classifier);
//		inputs.put("i1", instance);
//		ServiceCompositionResult result = client.invokeServiceComposition(comp, inputs);
//		return (Double)result.get("predictions").getData();
		
		ServiceCompositionResult result = new EasyClient()
											.withCompositionFile("testrsc/pipeline_composition_predict.txt")
											.withKeywordArgument("s1", preprocessor)
											.withKeywordArgument("s2", classifier)
											.withPositionalArgument(instance)
											.withService(preprocessor)
											.dispatch();
		
		return new Double((float)result.get("predictions").getData());
	}

	@Override
	public double[] distributionForInstance(Instance instance) throws Exception {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Capabilities getCapabilities() {
		// TODO Auto-generated method stub
		return null;
	}
	
	
	public static void main(String[] args) throws Throwable {
		int port = 8000;
		HttpServiceServer server = new HttpServiceServer(port);
		MLServicePipeline pl = new MLServicePipeline("localhost", port, "weka.classifiers.trees.RandomTree");
		
		Instances wekaInstances = new Instances(
				new BufferedReader(new FileReader(
						"../CrcTaskBasedConfigurator/testrsc" +
								File.separator + "polychotomous" +
								File.separator + "audiology.arff")));
		wekaInstances.setClassIndex(wekaInstances.numAttributes() - 1);
		pl.buildClassifier(wekaInstances);
		System.out.println("build process ready");
		server.shutdown();
	}
}
