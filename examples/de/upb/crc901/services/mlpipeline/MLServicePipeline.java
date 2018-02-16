package de.upb.crc901.services.mlpipeline;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.List;
import java.util.Random;

import de.upb.crc901.services.core.EasyClient;
import de.upb.crc901.services.core.HttpServiceServer;
import de.upb.crc901.services.core.ServiceCompositionResult;
import de.upb.crc901.services.core.ServiceHandle;
import jaicore.ml.WekaUtil;
import weka.classifiers.Classifier;
import weka.core.Capabilities;
import weka.core.Instance;
import weka.core.Instances;

public class MLServicePipeline implements Classifier {

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
			
			
			classifierTmp = new EasyClient()
					.withHost(hostname, port)
					.withClassPath(classifierName)
					.createService();
			
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
		Instances instances = new Instances(instance.dataset(), 1);
		instances.add(instance);
		return classifyInstances(instances)[0];
	}
	
	public double[] classifyInstances(Instances instances) throws Exception {
		ServiceCompositionResult result = new EasyClient()
				.withCompositionFile("testrsc/pipeline_composition_predict.txt")
				.withKeywordArgument("s1", preprocessor)
				.withKeywordArgument("s2", classifier)
				.withPositionalArgument(instances) // translates to 'i1'
				.withService(preprocessor)
				.dispatch();
		List<String> predictedLabels = (List<String>) result.get("predictions").getData();
		double[] predictedIndices = new double[predictedLabels.size()];
		for(int i = 0, size = predictedIndices.length; i < size; i++) {
			predictedIndices[i] = instances.classAttribute().indexOfValue(predictedLabels.get(i));
		}
		return predictedIndices;
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
		MLServicePipeline pl = new MLServicePipeline("localhost", port, "weka.classifiers.lazy.IBk");
		
		
		
		Instances wekaInstances = new Instances(
				new BufferedReader(new FileReader(
						"../CrcTaskBasedConfigurator/testrsc" +
								File.separator + "polychotomous" +
								File.separator + "audiology.arff")));
		wekaInstances.setClassIndex(wekaInstances.numAttributes() - 1);
		List<Instances> split = WekaUtil.getStratifiedSplit(wekaInstances, new Random(0), .7f);
		
		pl.buildClassifier(split.get(0));
		System.out.println("Building done!");
		
		int mistakes = 0;
		int index = 0;
		double[] predictions = pl.classifyInstances(split.get(1));
		for(Instance instance : split.get(1)) {
			double prediction  = predictions[index];
			if(instance.classValue() != prediction) {
				mistakes++;
			}
			index++;
		}
		System.out.println("Pipeline done. This many mistakes were made:" + mistakes + ". Error rate: " + (mistakes * 1f/split.get(1).size()));
		server.shutdown();
	}
}
