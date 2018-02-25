package de.upb.crc901.services.mlpipeline;

import de.upb.crc901.services.core.EasyClient;
import de.upb.crc901.services.core.EnvironmentState;
import de.upb.crc901.services.core.HttpServiceServer;
import de.upb.crc901.services.core.OntologicalTypeMarshallingSystem;
import de.upb.crc901.services.core.ServiceCompositionResult;
import de.upb.crc901.services.core.ServiceHandle;
import de.upb.crc901.services.mlpipeline.MLPipelinePlan.AttributeSelectionPipe;
import jaicore.ml.WekaUtil;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Random;

import weka.classifiers.Classifier;
import weka.core.Capabilities;
import weka.core.Instance;
import weka.core.Instances;

public class MLServicePipeline implements Classifier {

	private final EnvironmentState servicesContainer = new EnvironmentState();
	
	private final OntologicalTypeMarshallingSystem otms = new OntologicalTypeMarshallingSystem();
	
	private final List<String> PPFieldNames = new LinkedList<>();
	
	private final String classifierFieldName;

	public MLServicePipeline(final MLPipelinePlan plan) {
		super();
		if(!plan.isValid()) {
			throw new RuntimeException("The given plan is not valid..");
		}

		int varIDCounter = 1;
		EasyClient constructorEC = new EasyClient().withOTMS(otms);
		// build composition
		for (AttributeSelectionPipe attrPipe : plan.getAttrSelections()) {
			String ppFieldName = "service" + varIDCounter;
			PPFieldNames.add(ppFieldName);

			String searcherFieldName = "searcher" + varIDCounter;
			String searchOptions = "searcherOptions" + varIDCounter;
			
			String evalFieldName = "eval" + varIDCounter;
			String evalOptions = "evalOptions" + varIDCounter;
			
			// set host of the attribute selection
			constructorEC.withHost(attrPipe.getHost());
			
			// add inputs from attribute selection
			constructorEC	.withKeywordArgument(searcherFieldName, attrPipe.getSearcher())
							.withKeywordArgument(evalFieldName, attrPipe.getEval())
							
							.withKeywordArgument(searchOptions, attrPipe.getSearcherOptions())
							.withKeywordArgument(evalOptions, attrPipe.getEvalOptions());

			// add a construction line to the composition
			constructorEC.withAddedConstructOperation(ppFieldName, // output field name of the created servicehandle
					"weka.attributeSelection.AttributeSelection", // classpath of the preprocessor
					searcherFieldName, searchOptions, evalFieldName, evalOptions);

			varIDCounter++;
		}
		this.classifierFieldName = "service" + varIDCounter;
		String classifierOptions = "classifierOptions";
		
		//set host for classifier
		constructorEC.withHost(plan.getClassifierPipe().getHost());
		
		// add a stringlist for classifier
		constructorEC.withKeywordArgument(classifierOptions, plan.getClassifierPipe().getOptions());
		
		constructorEC.withAddedConstructOperation(classifierFieldName, // output field name of the created servicehandle
				 plan.getClassifierPipe().getName(), // classpath of the classifier
				 classifierOptions); // no args for the classifier construct
		
		try {
			// send server request:
			System.out.println("Sending the following construct composition:\n" + constructorEC.getCurrentCompositionText());
			ServiceCompositionResult result = constructorEC.dispatch(); 
			servicesContainer.extendBy(result); // add the services to out state
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		for (String fieldname : servicesContainer.serviceHandleFieldNames()) {
			ServiceHandle sh = (ServiceHandle) servicesContainer
					.retrieveField(fieldname).getData();
			System.out.println("\t" + fieldname + ":=" + sh.getServiceAddress());
		}
		// check if all our preprocessors and the classifier is created:
		for(String ppFieldName : PPFieldNames) {
			if( !servicesContainer.containsField(ppFieldName) || // if it isn't returned by the server
				!(servicesContainer.retrieveField(ppFieldName).getData() instanceof ServiceHandle) ||  // or if it isn't a servicehandle
				!((ServiceHandle)servicesContainer.retrieveField(ppFieldName).getData()).isSerialized()) // of if it doesn't contain an id.
				{
				throw new RuntimeException(ppFieldName);
			}
		}
		// same check for the classifier:
		if( !servicesContainer.containsField(classifierFieldName) || // if it isn't returned by the server
			!(servicesContainer.retrieveField(classifierFieldName).getData() instanceof ServiceHandle) ||  // or if it isn't a servicehandle
			!((ServiceHandle)servicesContainer.retrieveField(classifierFieldName).getData()).isSerialized()) // of if it doesn't contain an id.
			{
			throw new RuntimeException(classifierFieldName);
		}
	}

	@Override
	public void buildClassifier(final Instances data) throws Exception {
		int invocationNumber = 1;
		String dataInFieldName = "i1";
		EasyClient trainEC = new EasyClient()
				.withOTMS(otms)
				.withInputs(servicesContainer)
				.withPositionalArgument(data);
		// create train composition
		for(String ppFieldname : PPFieldNames) {
			String dataOutFieldName = "data"+invocationNumber;
			trainEC.withAddedMethodOperation("empty", ppFieldname, "SelectAttributes", dataInFieldName);
			trainEC.withAddedMethodOperation(dataOutFieldName, ppFieldname, "reduceDimensionality", dataInFieldName);
			// out put of this pipe it the input of the next one:
			dataInFieldName = dataOutFieldName;
			// create output name for the next data
			invocationNumber++;
		}
		trainEC.withAddedMethodOperation("empty", classifierFieldName, "train", dataInFieldName);
		
		try {
			System.out.println("Sending the following train composition:\n " + trainEC.getCurrentCompositionText());
			// send train request:
			trainEC.dispatch();
		} catch(IOException ex) {
			ex.printStackTrace();
		}
	}

	@Override
	public double classifyInstance(final Instance instance) throws Exception {
		Instances instances = new Instances(instance.dataset(), 1);
		instances.add(instance);
		return this.classifyInstances(instances)[0];
	}

	public double[] classifyInstances(final Instances instances)
			throws Exception {
		
		int invocationNumber = 1;
		String dataInFieldName = "i1";
		
		EasyClient predictEC = new EasyClient()
				.withOTMS(otms)
				.withInputs(servicesContainer)
				.withPositionalArgument(instances); // translates to i1
		
		// create train composition
		for(String ppFieldname : PPFieldNames) {
			String dataOutFieldName = "data"+invocationNumber;
			predictEC.withAddedMethodOperation(dataOutFieldName, ppFieldname, "reduceDimensionality", dataInFieldName);
			// out put of this pipe it the input of the next one:
			dataInFieldName = dataOutFieldName;
			// create output name for the next data
			invocationNumber++;
		}
		predictEC.withAddedMethodOperation("predictions", classifierFieldName, "predict", dataInFieldName);
		
		
		ServiceCompositionResult result;
		try {
			System.out.println("Sending the following predict composition:\n " + predictEC.getCurrentCompositionText());
			// send predict request:
			result = predictEC.dispatch();
		} catch(IOException ex) {
			ex.printStackTrace();
			throw new RuntimeException(ex);
		}
		
		@SuppressWarnings("unchecked")
		List<String> predictedLabels = (List<String>) result.get("predictions").getData();
		double[] predictedIndices = new double[predictedLabels.size()];
		for (int i = 0, size = predictedIndices.length; i < size; i++) {
			predictedIndices[i] = instances.classAttribute()
					.indexOfValue(predictedLabels.get(i));
		}
		return predictedIndices;
	}

	@Override
	public double[] distributionForInstance(final Instance instance)
			throws Exception {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Capabilities getCapabilities() {
		// TODO Auto-generated method stub
		return null;
	}
	
	
	public static void main(final String[] args) throws Throwable {
		HttpServiceServer server = null;
		try {
			int jasePort = 8000;
			server = HttpServiceServer.TEST_SERVER();
			// create a ml pp plan:
			MLPipelinePlan plan = new MLPipelinePlan();
			// add attribute selections:
			plan.onHost("localhost", jasePort);
			plan.addAttributeSelection() .withSearcher("weka.attributeSelection.Ranker")
										.withEval("weka.attributeSelection.PrincipalComponents");

			plan.addAttributeSelection() .withSearcher("weka.attributeSelection.Ranker")
										.withEval("weka.attributeSelection.PrincipalComponents");

			plan.addAttributeSelection() .withSearcher("weka.attributeSelection.Ranker")
										.withEval("weka.attributeSelection.PrincipalComponents");
			

			plan.onHost("localhost", jasePort);
			
			plan.setClassifier("weka.classifiers.rules.DecisionTable");
			

			System.out.println("Create MLServicePipeline with classifier and "
					+ plan.getAttrSelections().size() + " many preprocessors.");
			
			MLServicePipeline pl = new MLServicePipeline(plan);

			Instances wekaInstances = new Instances(new BufferedReader(
					new FileReader("../CrcTaskBasedConfigurator/testrsc"
							+ File.separator + "polychotomous" + File.separator
							+ "audiology.arff")));
			wekaInstances.setClassIndex(wekaInstances.numAttributes() - 1);
			List<Instances> split = WekaUtil.getStratifiedSplit(wekaInstances,
					new Random(0), .7f);

			pl.buildClassifier(split.get(0));
			System.out.println("Building done!");

			int mistakes = 0;
			int index = 0;
			double[] predictions = pl.classifyInstances(split.get(1));
			for (Instance instance : split.get(1)) {
				double prediction = predictions[index];
				if (instance.classValue() != prediction) {
					mistakes++;
				}
				index++;
			}
			System.out.println("Pipeline done. This many mistakes were made:"
					+ mistakes + ". Error rate: "
					+ (mistakes * 1f / split.get(1).size()));
		} finally {
			if(server != null) {
				server.shutdown();
			}
		}
		
	}

}
