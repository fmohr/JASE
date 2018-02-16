package de.upb.crc901.services.core;

import java.io.IOException;
import java.util.Arrays;
import java.util.Objects;

import jaicore.basic.FileUtil;

/**
 * 
 */
public final class EasyClient {
	
	private ServiceHandle innerHandle;
	private HttpBody body;
	private OntologicalTypeMarshallingSystem otms;
	public EasyClient() {
		innerHandle = new ServiceHandle();
		body = new HttpBody();
		otms = new OntologicalTypeMarshallingSystem();
	}

	public EasyClient withHost(String host) {
		Objects.requireNonNull(host);
		innerHandle = innerHandle.withExternalHost(host);
		return this;
	}
	public EasyClient withHost(String host, int port) {
		Objects.requireNonNull(host);
		innerHandle = innerHandle.withExternalHost(host + ":" + port);
		return this;
	}
	public EasyClient withClassPath(String classpath) {
		Objects.requireNonNull(classpath);
		innerHandle = innerHandle.withClassPath(classpath);
		return this;
	}
	public EasyClient withKeywordArgument(String keyword, Object data) {
		Objects.requireNonNull(keyword);
		Objects.requireNonNull(data);
		JASEDataObject parsedArgument = otms.allToSemantic(data, false);
		body.addKeyworkArgument(keyword, parsedArgument);
		return this;
	}
	public EasyClient withPositionalArgument(Object data) {
		Objects.requireNonNull(data);
		JASEDataObject parsedArgument = otms.allToSemantic(data, false);
		body.addPositionalArgument(parsedArgument);
		return this;
	}
	public EasyClient withService(ServiceHandle serviceHandle) {
		innerHandle = Objects.requireNonNull(serviceHandle);
		return this;
	}
	public EasyClient withService(String classpath, String id) {
		innerHandle = innerHandle.withClassPath(classpath);
		innerHandle = innerHandle.withId(id);
		return this;
	}
	public EasyClient withBody(HttpBody body) {
		this.body = Objects.requireNonNull(body);
		return this;
	}
	public EasyClient withCompositionFile(String filePath) throws IOException {
		String composition = FileUtil.readFileAsString(filePath);
		this.body.setComposition(composition);
		return this;
	}
	public EasyClient withComposition(String compostionText) {
		this.body.setComposition(compostionText);
		return this;
	}
	public EasyClient withCurrentIndex(int currentIndex) {
		this.body.setCurrentIndex(currentIndex);
		return this;
	}
	public EasyClient withMaxIndex(int currentIndex) {
		this.body.setMaxIndex(currentIndex);
		return this;
	}
	
	public ServiceHandle createService(String... constructorArgNames)  throws IOException {
		// check if requirements met
		{
			if(!innerHandle.isRemote()) {
				throw new RuntimeException();
			}
			if(!innerHandle.isClassPathSpecified()) {
				throw new RuntimeException();
			}
			if(!checkInputs(constructorArgNames)){
				throw new RuntimeException("Field: " + Arrays.toString(constructorArgNames) + " not available.");
			}
		}
		// create a new composition:
		String comp = "out = " + innerHandle.getHost() + "/" + innerHandle.getClasspath() + "::__construct({";
		comp += getCompositionArgsFromStringInputs(constructorArgNames);
		comp += "})";
		withComposition(comp);
		ServiceCompositionResult result = dispatch();
		if(result.containsKey("out")) {
			JASEDataObject jdo = result.get("out");
			if(jdo.getData() instanceof ServiceHandle) {
				return (ServiceHandle) jdo.getData();
			}
		}
		throw new RuntimeException("Error occurred during creation of service.");
	}
	
	public JASEDataObject invokeOperation(String methodName, String...methodArgNames) throws IOException {
		// check if requirements met
		{
			if(!innerHandle.isRemote()) {
				throw new RuntimeException();
			}
			if(!innerHandle.isSerialized()) {
				throw new RuntimeException();
			}
			if(!checkInputs(methodArgNames)){
				throw new RuntimeException("Field: " + Arrays.toString(methodArgNames) + " not available.");
			}
		}
		// create a new composition:
		String comp = "out = " + innerHandle.getServiceAddress() + "::" + methodName + "({";
		comp += getCompositionArgsFromStringInputs(methodArgNames);
		comp += "})";
		withComposition(comp);
		return dispatch().get("out");
	}
	
	public boolean checkInputs(String...inputNamesToCheck) {
		for(String constructorArg : inputNamesToCheck) {
			if(! body.getState().containsField(constructorArg)) {
				return false;
			}
		}
		return true;
	}
	
	private String getCompositionArgsFromStringInputs(String...argNames) {
		String comp = "";
		int index = 1;
		for(String constructorArg : argNames) {
			comp +=  ",i" + index + "=" + constructorArg;
			index++;
		}
		if(argNames.length == 0) {
			comp += ","; // scs bug
		} else {
			comp = comp.substring(1); // first comma is too much. Look at the loop
		}
		return comp;
	}
	
	
	public ServiceCompositionResult dispatch() throws IOException {
		if(!innerHandle.isRemote()) {
			throw new RuntimeException();
		}
		HttpServiceClient client = new HttpServiceClient(otms);
		return client.sendCompositionRequest(innerHandle.getHost(), body);
	}

}