package de.upb.crc901.services.core;

import jaicore.basic.FileUtil;

import java.io.IOException;
import java.util.Arrays;
import java.util.Objects;

/**
 *
 */
public final class EasyClient {

  private ServiceHandle innerHandle;
  private HttpBody body;
  private OntologicalTypeMarshallingSystem otms;

  public EasyClient() {
    this.innerHandle = new ServiceHandle();
    this.body = new HttpBody();
    this.otms = new OntologicalTypeMarshallingSystem();
  }

  public EasyClient withHost(final String host) {
    Objects.requireNonNull(host);
    this.innerHandle = this.innerHandle.withExternalHost(host);
    return this;
  }

  public EasyClient withHost(final String host, final int port) {
    Objects.requireNonNull(host);
    this.innerHandle = this.innerHandle.withExternalHost(host + ":" + port);
    return this;
  }

  public EasyClient withClassPath(final String classpath) {
    Objects.requireNonNull(classpath);
    this.innerHandle = this.innerHandle.withClassPath(classpath);
    return this;
  }

  public EasyClient withKeywordArgument(final String keyword, final Object data) {
    Objects.requireNonNull(keyword);
    Objects.requireNonNull(data);
    JASEDataObject parsedArgument = this.otms.allToSemantic(data, false);
    this.body.addKeyworkArgument(keyword, parsedArgument);
    return this;
  }

  public EasyClient withPositionalArgument(final Object data) {
    Objects.requireNonNull(data);
    JASEDataObject parsedArgument = this.otms.allToSemantic(data, false);
    this.body.addPositionalArgument(parsedArgument);
    return this;
  }

  public EasyClient withService(final ServiceHandle serviceHandle) {
    this.innerHandle = Objects.requireNonNull(serviceHandle);
    return this;
  }

  public EasyClient withService(final String classpath, final String id) {
    this.innerHandle = this.innerHandle.withClassPath(classpath);
    this.innerHandle = this.innerHandle.withId(id);
    return this;
  }

  public EasyClient withBody(final HttpBody body) {
    this.body = Objects.requireNonNull(body);
    return this;
  }

  public EasyClient withCompositionFile(final String filePath) throws IOException {
    String composition = FileUtil.readFileAsString(filePath);
    this.body.setComposition(composition);
    return this;
  }

  public EasyClient withComposition(final String compostionText) {
    this.body.setComposition(compostionText);
    return this;
  }

  public EasyClient withCurrentIndex(final int currentIndex) {
    this.body.setCurrentIndex(currentIndex);
    return this;
  }

  public EasyClient withMaxIndex(final int currentIndex) {
    this.body.setMaxIndex(currentIndex);
    return this;
  }

  public EasyClient withVariableBinding(final String varName) {
    this.innerHandle.setVariableBinding(varName);
    return this;
  }

  public ServiceHandle createService(final String... constructorArgNames) throws IOException {
    // check if requirements met
    {
      if (!this.innerHandle.isRemote()) {
        throw new RuntimeException();
      }
      if (!this.innerHandle.isClassPathSpecified()) {
        throw new RuntimeException();
      }
      if (!this.checkInputs(constructorArgNames)) {
        throw new RuntimeException("Field: " + Arrays.toString(constructorArgNames) + " not available.");
      }
    }
    // create a new composition:
    String comp = "out = " + this.innerHandle.getHost() + "/" + this.innerHandle.getClasspath() + "::__construct({";
    comp += this.getCompositionArgsFromStringInputs(constructorArgNames);
    comp += "})";
    this.withComposition(comp);
    ServiceCompositionResult result = this.dispatch();
    if (result.containsKey("out")) {
      JASEDataObject jdo = result.get("out");
      if (jdo.getData() instanceof ServiceHandle) {
        return (ServiceHandle) jdo.getData();
      }
    }
    throw new RuntimeException("Error occurred during creation of service.");
  }

  public JASEDataObject invokeOperation(final String methodName, final String... methodArgNames) throws IOException {
    // check if requirements met
    {
      if (!this.innerHandle.isRemote()) {
        throw new RuntimeException();
      }
      if (!this.innerHandle.isSerialized()) {
        throw new RuntimeException();
      }
      if (!this.checkInputs(methodArgNames)) {
        throw new RuntimeException("Field: " + Arrays.toString(methodArgNames) + " not available.");
      }
    }
    // create a new composition:
    String comp = "out = " + this.innerHandle.getServiceAddress() + "::" + methodName + "({";
    comp += this.getCompositionArgsFromStringInputs(methodArgNames);
    comp += "})";
    this.withComposition(comp);
    return this.dispatch().get("out");
  }

  public boolean checkInputs(final String... inputNamesToCheck) {
    for (String constructorArg : inputNamesToCheck) {
      if (!this.body.getState().containsField(constructorArg)) {
        return false;
      }
    }
    return true;
  }

  private String getCompositionArgsFromStringInputs(final String... argNames) {
    String comp = "";
    int index = 1;
    for (String constructorArg : argNames) {
      comp += ",i" + index + "=" + constructorArg;
      index++;
    }
    if (argNames.length == 0) {
      comp += ","; // scs bug
    } else {
      comp = comp.substring(1); // first comma is too much. Look at the loop
    }
    return comp;
  }

  public ServiceCompositionResult dispatch() throws IOException {
    if (!this.innerHandle.isRemote()) {
      throw new RuntimeException();
    }
    HttpServiceClient client = new HttpServiceClient(this.otms);
    return client.sendCompositionRequest(this.innerHandle.getHost(), this.body);
  }

}