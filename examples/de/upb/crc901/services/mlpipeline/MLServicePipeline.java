package de.upb.crc901.services.mlpipeline;

import de.upb.crc901.services.core.EasyClient;
import de.upb.crc901.services.core.HttpServiceServer;
import de.upb.crc901.services.core.ServiceCompositionResult;
import de.upb.crc901.services.core.ServiceHandle;

import jaicore.ml.WekaUtil;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
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

  private final List<ServiceHandle> preprocessor;
  private final ServiceHandle classifier;

  Map<ServiceHandle, String> serviceHandleToVarName = new HashMap<>();
  Map<String, ServiceHandle> varNameToServiceHandle = new HashMap<>();

  public MLServicePipeline(final String hostname, final int port, final String classifierName) {
    super();

    int varIDCounter = 1;
    ServiceHandle preprocessorTmp = null;
    ServiceHandle classifierTmp = null;
    try {
      preprocessorTmp = new EasyClient().withHost(hostname, port).withClassPath("weka.attributeSelection.AttributeSelection")
          .withKeywordArgument("searcher", "weka.attributeSelection.Ranker").withKeywordArgument("eval", "weka.attributeSelection.PrincipalComponents")
          .createService("searcher", "eval");
      preprocessorTmp.setVariableBinding("s" + varIDCounter++);

      classifierTmp = new EasyClient().withHost(hostname, port).withClassPath(classifierName).createService();
      classifierTmp.setVariableBinding("s" + varIDCounter++);
    } catch (IOException e) {
      e.printStackTrace();
    }

    this.preprocessor = new LinkedList<>();
    this.preprocessor.add(preprocessorTmp);

    this.classifier = classifierTmp;
    for (ServiceHandle preprocessor : this.preprocessor) {
      System.out.println(preprocessor.getVariableBinding() + ":=" + preprocessor.getServiceAddress());
    }
    System.out.println(this.classifier.getVariableBinding() + ":=" + this.classifier.getServiceAddress());
  }

  @Override
  public void buildClassifier(final Instances data) throws Exception {

    StringBuffer sb = new StringBuffer();
    int invocationNumber = 1;
    String dataVariable = "i1";
    for (ServiceHandle preprocessor : this.preprocessor) {
      sb.append("empty" + (invocationNumber++) + " = " + preprocessor.getVariableBinding() + "::" + "SelectAttributes" + "({i1=" + dataVariable + "});\n");
      String newDataVariable = "data" + (invocationNumber);
      sb.append(newDataVariable + " = " + preprocessor.getVariableBinding() + "::" + "reduceDimensionality" + "({i1 = " + dataVariable + "});\n");
      dataVariable = newDataVariable;
    }
    sb.append("empty" + (invocationNumber++) + " = " + this.classifier.getVariableBinding() + "::train({i1=" + dataVariable + "});\n");

    EasyClient ec = new EasyClient().withComposition(sb.toString());
    for (ServiceHandle preprocessor : this.preprocessor) {
      ec.withKeywordArgument(preprocessor.getVariableBinding(), preprocessor);
    }
    ec.withKeywordArgument(this.classifier.getVariableBinding(), this.classifier);
    ec.withPositionalArgument(data).withService(this.preprocessor.get(0)).dispatch();
  }

  @Override
  public double classifyInstance(final Instance instance) throws Exception {
    Instances instances = new Instances(instance.dataset(), 1);
    instances.add(instance);
    return this.classifyInstances(instances)[0];
  }

  public double[] classifyInstances(final Instances instances) throws Exception {

    ServiceCompositionResult result = new EasyClient().withCompositionFile("testrsc/pipeline_composition_predict.txt").withKeywordArgument("s1", this.preprocessor.get(0))
        .withKeywordArgument("s2", this.classifier).withPositionalArgument(instances) // translates to 'i1'
        .withService(this.preprocessor.get(0)).dispatch();
    List<String> predictedLabels = (List<String>) result.get("predictions").getData();
    double[] predictedIndices = new double[predictedLabels.size()];
    for (int i = 0, size = predictedIndices.length; i < size; i++) {
      predictedIndices[i] = instances.classAttribute().indexOfValue(predictedLabels.get(i));
    }
    return predictedIndices;
  }

  @Override
  public double[] distributionForInstance(final Instance instance) throws Exception {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public Capabilities getCapabilities() {
    // TODO Auto-generated method stub
    return null;
  }

  public static void main(final String[] args) throws Throwable {
    int port = 8000;
    HttpServiceServer server = new HttpServiceServer(port);
    MLServicePipeline pl = new MLServicePipeline("localhost", port, "weka.classifiers.lazy.IBk");

    Instances wekaInstances = new Instances(
        new BufferedReader(new FileReader("../CrcTaskBasedConfigurator/testrsc" + File.separator + "polychotomous" + File.separator + "audiology.arff")));
    wekaInstances.setClassIndex(wekaInstances.numAttributes() - 1);
    List<Instances> split = WekaUtil.getStratifiedSplit(wekaInstances, new Random(0), .7f);

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
    System.out.println("Pipeline done. This many mistakes were made:" + mistakes + ". Error rate: " + (mistakes * 1f / split.get(1).size()));
    server.shutdown();
  }
}
