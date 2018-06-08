/**
 * HttpServiceServer.java
 * Copyright (C) 2017 Paderborn University, Germany
 *
 * This class provides (configured) Java functionality over the web
 *
 * @author: Felix Mohr (mail@felixmohr.de)
 */

/*
 *   This program is free software: you can redistribute it and/or modify
 *   it under the terms of the GNU General Public License as published by
 *   the Free Software Foundation, either version 3 of the License, or
 *   (at your option) any later version.
 *
 *   This program is distributed in the hope that it will be useful,
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *   GNU General Public License for more details.
 *
 *   You should have received a copy of the GNU General Public License
 *   along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package de.upb.crc901.services.core;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import de.upb.crc901.configurationsetting.compositiondomain.CompositionDomain;
import de.upb.crc901.configurationsetting.operation.Operation;
import de.upb.crc901.configurationsetting.operation.OperationInvocation;
import de.upb.crc901.configurationsetting.operation.SequentialComposition;
import de.upb.crc901.services.serviceobserver.HttpServiceObserver;
import jaicore.logging.LoggerUtil;
import jaicore.logic.fol.structure.LiteralParam;
import jaicore.logic.fol.structure.VariableParam;

public class HttpServiceServer {

	private static final Logger logger = LoggerFactory.getLogger(HttpServiceServer.class);

	private static final File folder = new File("http");

	private final HttpServer server;
	private final OntologicalTypeMarshallingSystem otms;
	private final ClassesConfiguration classesConfig;

	/**
	 * containsHostPattern is used to check if a operation contains a host address
	 * at the beginning: This pattern matches like: "localhost:10/__",
	 * "10.12.14.16:100/__" or with no port at all: "10.12.14.16/__"
	 */
	private final static String ValidIpAddressRegex = "(([0-9]|[1-9][0-9]|1[0-9]{2}|2[0-4][0-9]|25[0-5])\\.){3}([0-9]|[1-9][0-9]|1[0-9]{2}|2[0-4][0-9]|25[0-5])"; // TODO
																																									// do
																																									// we
																																									// need
																																									// to
																																									// support
																																									// ipv6
	// address
	private final static String ValidHostnameRegex = ".+?";

	private final static Pattern containsHostPattern = Pattern.compile("^(" + ValidHostnameRegex + "|" // host name
			+ ValidIpAddressRegex + ")"// or ipv4 address
			+ "(:\\d+)?/");

	// private final Set<String> supportedOperations = new HashSet<>();
	// private final Map<String, Map<String, String>> resultMaps = new HashMap<>();

	class JavaClassHandler implements HttpHandler {

		@Override
		public void handle(final HttpExchange t) {

			String response = "";
			HttpBody returnBody = null;
			List<Throwable> exceptions = new ArrayList<>();
			try {

				/* determine method to be executed */
				String address = t.getRequestURI().getPath().substring(1);
				logger.info("Received query for {}", address);

				/*
				 * initiate state with the non-constant inputs given in post (non-int and
				 * non-doubles are treated as strings)
				 */
				if ((!"post".equalsIgnoreCase(t.getRequestMethod()))) {
					logger.error("No post request");
					return;
				}
				InputStream input = t.getRequestBody();

				HttpBody body = new HttpBody();
				try {
					body.readfromBody(input);
				} catch (IOException e) {
					LoggerUtil.getExceptionInfo(e);
					exceptions.add(e);
					return;
				}

				try {
					HttpServiceObserver.javaServerRequestNotice(body.getRequestId());
				} catch (IOException e) {
					LoggerUtil.getExceptionInfo(e);
					exceptions.add(e);
					return;
				}

				String[] parts = address.split("/", 3);
				String clazz = parts[0];
				String objectId = null;
				if (parts.length > 1) { // address contains objectId. this request will therefore be handled as a
										// service call.
					objectId = parts[1];
				} else if (clazz.equals("choreography")) { // choreography call:
					if (!body.containsComposition()) {
						response += "objectID and no choreography was given.";
						exceptions.add(new RuntimeException(response));
						logger.error(response);
						return;
					}
					address = body.getOperation(body.getCurrentIndex()).getOperation().getName();
					parts = address.split("/", 3);
					clazz = parts[0];
					if (parts.length > 1) { // address contains objectId. this request will therefore be handled as a
											// service call.
						objectId = parts[1];
					}

				}
				if (body.getComposition() == null && objectId == null) {
					response += "The address: " + address + " can't be handled by this server.";
					exceptions.add(new RuntimeException(response));
					logger.error(response);
					return;
				}

				// Map<String, JASEDataObject> initialState = new
				// HashMap<>(body.getKeyworkArgs());
				// Map<String, JASEDataObject> state = new HashMap<>(initialState);
				EnvironmentState envState = body.getState();
				envState.resetStartingField();
				// logger.info("Input keys are: {}",
				// StreamSupport.stream(envState.startingFieldNames().spliterator(),
				// false).collect(Collectors.joining(", ")));

				/*
				 * analyze choreography in order to see what we actually will execute right
				 * away: 1. move to position of current call; 2. compute all subsequent calls on
				 * same host and on services spawend from here.
				 *
				 */
				SequentialCompositionCollection comp = null;
				SequentialComposition subsequenceComp = new SequentialComposition(new CompositionDomain());
				OperationInvocation invocationToMakeFromHere = null;
				if (body.containsComposition()) {
					comp = body.parseSequentialComposition();
					Iterator<OperationInvocation> it = comp.iterator();
					Collection<String> servicesInExecEnvironment = new HashSet<>();
					for (String field : body.getState().serviceHandleFieldNames()) {
						if (!((ServiceHandle) body.getState().retrieveField(field).getData()).isRemote()) {
							servicesInExecEnvironment.add(field);
						}
					}
					for (int i = 0; it.hasNext(); i++) {
						OperationInvocation opInv = it.next();
						if (body.isBelowExecutionBound(i)) {
							continue; // ignore indexes before the current one
						}
						if (body.isAboveExecutionBound(i)) {
							break; // ignore operations above maxindex.
						}
						invocationToMakeFromHere = opInv;
						String opName = opInv.getOperation().getName();
						if (opName.contains("/")) {
							String host = opName.substring(0, opName.indexOf("/"));
							String myAddress = t.getLocalAddress().toString().substring(1);
							// if (!host.equals(myAddress)) {
							// break;
							// }
							if (!HttpServiceServer.this.canExecute(opInv)) { // if this server can't execute this
																				// operation exit the loop here.
																				// invocationToMakeFromHere will
																				// then contain the address of the next
																				// invocation.
								// throw new RuntimeException("Can't execute this service: " +
								// opInv.toString());
								logger.warn("I cannot execute {}.", opInv);
								break;
							}

							/*
							 * if this is a constructor, also add the created instance to the locally
							 * available services
							 */
							servicesInExecEnvironment.add(opName);
							if (opName.contains("__construct")) {
								servicesInExecEnvironment
										.add(opInv.getOutputMapping().values().iterator().next().getName());
							}
						} else if (!servicesInExecEnvironment.contains(opName.substring(0, opName.indexOf("::")))) {
							break;
						}
						subsequenceComp.addOperationInvocation(opInv);
						invocationToMakeFromHere = null;
					}
				} else {
					OperationInvocation opinv;
					if (objectId.equals("__construct")) {

						/* creating new object */
						opinv = ServiceUtil.getOperationInvocation(
								t.getLocalAddress().toString().substring(1) + "/" + clazz + "::__construct",
								envState.getCurrentMap());

					} else {
						opinv = ServiceUtil.getOperationInvocation(t.getLocalAddress().toString().substring(1) + "/"
								+ clazz + "/" + objectId + "::" + parts[2], envState.getCurrentMap());
					}
					subsequenceComp.addOperationInvocation(opinv);
				}

				/* execute the whole induced composition */

				int currentIndex = body.getCurrentIndex();
				for (OperationInvocation opInv : subsequenceComp) {
					try {
						HttpServiceServer.this.invokeOperation(opInv, envState);
					} catch (Exception e) {
						exceptions.add(e);
						logger.debug("Invocation of operation caused an exception");
						LoggerUtil.logException(e);
						return;
					}
					currentIndex++;
				}
				logger.info("Finished local execution. Now invoking {}", invocationToMakeFromHere);

				/* forward next service */
				if (invocationToMakeFromHere != null) {

					/*
					 * extract vars from state that are in json (ordinary data but not service
					 * references)
					 */
					OperationPieces pieces = new OperationPieces(invocationToMakeFromHere.getOperation().getName());
					ServiceCompositionResult result;

					// create a shallow copy of the state we have:
					EnvironmentState forwardInputs = new EnvironmentState(); // forwarded to the other server
					for (String fieldName : envState.currentFieldNames()) {
						JASEDataObject field = envState.retrieveField(fieldName);
						if (field.isofType("ServiceHandle")) {
							ServiceHandle sh = (ServiceHandle) field.getData();
							if (sh.isRemote()) { // only forward remote services
								forwardInputs.addField(fieldName, field);
							}
						} else {
							// TODO what do we need to forward?
							forwardInputs.addField(fieldName, field);
						}
					}

					HttpBody forwardBody = new HttpBody(forwardInputs, body.getComposition(), currentIndex, -1);

					// use the initial request id
					forwardBody.setRequestId(body.getRequestId());

					try {
						if (pieces.hasHost()) {
							result = new EasyClient().withBody(forwardBody).withHost(pieces.getHost()).dispatch();
						} else if (envState.containsField(pieces.getId())) {

							if (!(envState.retrieveField(pieces.getId()).getData() instanceof ServiceHandle)) {
								throw new RuntimeException("The refered object " + pieces.getId() + " was of type "
										+ envState.retrieveField(pieces.getId()).getType());
							}
							ServiceHandle handler = (ServiceHandle) envState.retrieveField(pieces.getId()).getData();
							result = new EasyClient().withBody(forwardBody).withService(handler).dispatch();
						} else {
							throw new RuntimeException("Can't forward the rest of the message.");
						}
					} catch (Exception e) {
						logger.debug("Forward of piece " + pieces.getId() + " failed with exception.");
						LoggerUtil.logException(e);
						exceptions.add(e);
						return;
					}
					envState.extendBy(result);
					response += result.toString();
					logger.info("Received answer from subsequent service.");
				}

				/* now returning the serializations of all created (non-service) objects */
				logger.info("Returning answer to sender");
				returnBody = new HttpBody();
				for (String key : envState.addedFieldNames()) {
					JASEDataObject answerObject = envState.retrieveField(key);
					if (answerObject == null) {
						logger.info("Ignoring answer object {}", answerObject.getData());
						continue;
					}
					logger.info("Adding {} for key {}", answerObject.getData(), key);
					returnBody.addKeyworkArgument(key, answerObject);
				}
			} finally {

				/* if the returned body has not been created already, create it now */
				if (returnBody == null) {
					returnBody = new HttpBody();
				}

				OutputStream os = null;
				try {
					if (exceptions.isEmpty()) {
						t.sendResponseHeaders(200, 0);
						os = t.getResponseBody();
						returnBody.writeBody(os);
					} else {
						t.sendResponseHeaders(400, 0);
						os = t.getResponseBody();
						StringBuilder sb = new StringBuilder();

						for (Throwable e : exceptions) {
							while (e != null) {
								sb.append((e.getClass().getName() + "\n"));
								sb.append((e.getMessage() + "\n"));
								for (StackTraceElement ee : e.getStackTrace()) {
									sb.append(ee.toString() + "\n");
								}
								if (e.getCause() != null) {
									sb.append("\n\n Caused by: ");
								}
								e = e.getCause();
							}
						}
						os.write(sb.toString().getBytes());
						os.flush();
					}
				} catch (Exception e) {
					logger.debug("Could not return answer due to an exception");
					LoggerUtil.logException(e);
				} finally {
					if (os != null) {
						try {
							os.close();
						} catch (IOException e) {
							logger.debug("Could not propertly close the output stream.");
							LoggerUtil.logException(e);
						}
					}
				}
			}

		}

	}

	private boolean canExecute(final OperationInvocation opInv) {
		String opName = opInv.getOperation().getName();

		if (opName.contains("__construct")) {
			// extract class name
			String clazz = opName.substring(opName.indexOf("/") + 1).split("::")[0];
			return this.classesConfig.classknown(clazz); // returns true if the class is known.
		} else { // lets hope we know how to execute this. TODO see if there are cases where we
					// can't execute an op
					// without '__construct'
			return true;
		}
	}

	/**
	 * Resolves the arguments from a given operation invocation object. This
	 * invocation may have arguments, like: op({"a", 12, field1}). In this the list
	 * will contain 3 JASEDataObject objects: first one is a string the second one
	 * number type and the third will be retrieved from the EnvironmentState
	 * variable.
	 */
	private List<JASEDataObject> resolveArguments(final OperationInvocation operationInvocation,
			final EnvironmentState envState) {

		Operation operation = operationInvocation.getOperation();
		List<VariableParam> inputs = operation.getInputParameters();

		List<JASEDataObject> arguments = new ArrayList<>(inputs.size());

		Map<VariableParam, LiteralParam> inputMapping = operationInvocation.getInputMapping();

		OntologicalTypeMarshallingSystem otms = new OntologicalTypeMarshallingSystem();

		// sort inputs:
		inputs.sort((varPar1, varPar2) -> {
			String name1 = varPar1.getName();
			String name2 = varPar2.getName();
			Integer pos1 = EnvironmentState.indexFromField(name1);
			Integer pos2 = EnvironmentState.indexFromField(name2);
			return pos1.compareTo(pos2);
		});

		// extract inputs from envState:
		for (int j = 0; j < inputs.size(); j++) {
			JASEDataObject argument = null;

			String stringValue = inputMapping.get(inputs.get(j)).getName();
			/*
			 * stringValue is a string encoded value. this value could be a number like: 12
			 * or a boolean value like: true or it could be a string value in which case it
			 * has to be in between quotation marks. like: "abc"
			 */
			if (otms.isPrimitiveNumber(stringValue)) {
				argument = otms.primitiveToSemanticAsString(stringValue);
			} else if (otms.isPrimitiveBoolean(stringValue)) {
				argument = otms.primitiveToSemanticAsString(stringValue);
			} else if (stringValue.startsWith("\"") && stringValue.endsWith("\"")) {
				stringValue = stringValue.substring(1, stringValue.length() - 1); // remove quotation marks
				argument = otms.primitiveToSemantic(stringValue);
			}
			/*
			 * if the value isn't meant to be of primitive type then it is assumed to be a
			 * fieldname.
			 */
			else if (envState.containsField(stringValue)) {
				String fieldName = stringValue;
				argument = envState.retrieveField(fieldName);
			} else {
				throw new IllegalArgumentException(
						"Cannot find value for argument " + stringValue + " in state table.");
			}
			arguments.add(argument);
		}
		return arguments;
	}

	private final class OperationPieces {
		final boolean hasHost;
		final String host; // host contains "/" at the end
		final String context;
		final String invocation;
		final String classpath;
		final String id;

		OperationPieces(final String operationStringValue) {
			String hostContext;
			if (operationStringValue.contains("::")) {
				String[] hostContextOperationSplit = operationStringValue.split("::");
				hostContext = hostContextOperationSplit[0];
				this.invocation = hostContextOperationSplit[1];
			} else {
				hostContext = operationStringValue;
				this.invocation = "__construct";
			}
			if (HttpServiceServer.this.startsWithHost(hostContext)) {
				this.hasHost = true;
				this.host = HttpServiceServer.this.extractHost(hostContext);
			} else {
				this.hasHost = false;
				this.host = "";
			}
			if (this.hasHost) {
				this.context = hostContext.substring(this.host.length());
			} else {
				this.context = hostContext;
			}
			if (this.context.contains("/")) {
				String classPathId[] = this.context.split("/");
				this.classpath = classPathId[0];
				this.id = classPathId[1];
			} else {
				if (this.isConstructorInvocation()) {
					this.classpath = this.context;
					this.id = "";
				} else {
					this.classpath = "";
					this.id = this.context;
				}
			}
		}

		boolean hasHost() {
			return this.hasHost;
		}

		boolean hasClasspathAndId() {
			return !this.id.isEmpty() && !this.classpath.isEmpty();
		}

		String getId() {
			return this.id;
		}

		String getServiceName() {
			return this.context;
		}

		String getHost() {
			return this.host;
		}

		String getClasspath() {
			return this.classpath;
		}

		String getMethodname() {
			return this.invocation;
		}

		boolean isConstructorInvocation() {
			return this.invocation.equalsIgnoreCase("__construct");
		}
	}

	private void invokeOperation(final OperationInvocation operationInvocation, final EnvironmentState envState)
			throws IllegalAccessException, IllegalArgumentException, InvocationTargetException, NoSuchMethodException,
			SecurityException, ClassNotFoundException, InstantiationException, IOException {
		logger.info("Performing invocation {} in state {}", operationInvocation, envState.getCurrentMap());

		List<JASEDataObject> inputList = this.resolveArguments(operationInvocation, envState);

		/*
		 * if this operation is a constructor, create the corresponding service and
		 * return the url
		 */
		String opName = operationInvocation.getOperation().getName();
		Map<VariableParam, VariableParam> outputMapping = operationInvocation.getOutputMapping();
		OperationPieces opPieces = new OperationPieces(opName);
		Map<String, String> resultKeywordMap;
		Object basicResult;
		Object[] inputArgs;
		if (opPieces.isConstructorInvocation()) {
			logger.info("The invocation cr eates a new service instance");
			Class<?> serviceClass = null;
			try {
				serviceClass = Class.forName(opPieces.getClasspath());
			} catch (java.lang.ClassNotFoundException ex) {
				logger.error("CLASS NOT FOUND : " + opPieces.getClasspath());
				return;
			}
			Constructor<?> constructor = this.getConstructor(serviceClass, inputList);
			java.util.Objects.requireNonNull(constructor, "No constructor found for " + serviceClass);
			if (logger.isDebugEnabled()) {
				logger.debug("{}/{}/{}", inputList, constructor.getParameterCount(), constructor);
			}

			Object newService = null;

			boolean wrapped = this.classesConfig.isWrapped(opPieces.getClasspath()); // true if this class is supposed
																						// to be wrapped.
			ServiceWrapper wrapper = null;

			if (wrapped) { // create the wrapper.
				String wrapperClasspath = this.classesConfig.getWrapperClasspath(opPieces.getClasspath());
				Class<?> wrapperClass = Class.forName(wrapperClasspath);
				Constructor<? extends ServiceWrapper> wrapperConstructor = (Constructor<? extends ServiceWrapper>) wrapperClass
						.getConstructor(ServiceWrapper.CONSTRUCTOR_TYPES);
				// create the wrapper by giving it the constructor and the values.
				JASEDataObject[] boxedArgs = inputList.toArray(new JASEDataObject[inputList.size()]);
				if (boxedArgs.length > 0) {
					wrapper = wrapperConstructor.newInstance(constructor, boxedArgs);
				} else {
					wrapper = wrapperConstructor.newInstance(constructor, new JASEDataObject[0]);
				}
				newService = wrapper.getDelegate();
			} else {
				Object[] parsedArgs = this.otms.objectArrayFromSemantic(constructor.getParameterTypes(), inputList);
				// create the service itself;
				newService = constructor.newInstance(parsedArgs);
			}
			// create service handle by identifying the service with an unique identifier.
			String id = UUID.randomUUID().toString();
			ServiceHandle sh;
			if (!wrapped) {
				sh = new ServiceHandle(opPieces.getClasspath(), id, newService);
			} else {
				sh = new ServiceHandle(opPieces.getClasspath(), id, wrapper);
			}
			boolean serializationSuccess = false; // be pessimistic about result. Set to true if it worked.
			// if wrapped and wrappers'delegate can be serialized or it wasn't wrapped and
			// the service itself
			// can be serialized.

			// if (newService instanceof Serializable) {
			// /* serialize result */
			// try {
			// FileUtil.serializeObject(wrapped ? wrapper : newService,
			// getServicePath(opPieces.getClasspath(),
			// id));
			// // no problems occurred.. success
			// serializationSuccess = true;
			// } catch (IOException e) {
			// logger.error(e.getMessage() + "\n" + Arrays.toString(e.getStackTrace()));
			// }
			// }
			ServiceManager.SINGLETON().addService(sh);
			serializationSuccess = true;
			if (!serializationSuccess) {
				// serialization wasn't successful.
				sh = sh.unsuccessedSerialize();
			}
			basicResult = new JASEDataObject(ServiceHandle.class.getSimpleName(), sh);
			inputArgs = new Object[0];
			resultKeywordMap = this.classesConfig.getMethodResultMap(opPieces.getClasspath(), opPieces.getMethodname());
		} else {
			logger.info("Run invocation on an existing service instance");
			ServiceHandle handler = null;
			if (opPieces.hasClasspathAndId()) {
				// load from disk:
				// Object service = FileUtil
				// .unserializeObject(getServicePath(opPieces.getClasspath(),
				// opPieces.getId()));
				// handler = new ServiceHandle(opPieces.getClasspath(), opPieces.getId(),
				// service);
				handler = ServiceManager.SINGLETON().getHandle(opPieces.getClasspath(), opPieces.getId());
			} else {
				if (!envState.containsField(opPieces.getServiceName())) {
					throw new RuntimeException("The handler wasn't found in the state.");
				}
				if (!(envState.retrieveField(opPieces.getServiceName()).getData() instanceof ServiceHandle)) {
					throw new RuntimeException("The refered object " + opPieces.getId() + " was of type "
							+ envState.retrieveField(opPieces.getId()).getType());
				}
				ServiceHandle emptyHandler = (ServiceHandle) envState.retrieveField(opPieces.getId()).getData();
				if (emptyHandler.containService()) {
					handler = emptyHandler;
				} else {
					// Object service =
					// FileUtil.unserializeObject(getServicePath(emptyHandler.getClasspath(),
					// emptyHandler.getId()));
					// handler = emptyHandler.withService(service);
					handler = ServiceManager.SINGLETON().getHandle(emptyHandler.getClasspath(), emptyHandler.getId());
					// replace the servicehandler in state so that next time the service is already
					// unserialized:
					envState.addField(opPieces.getServiceName(), this.otms.objectToSemantic(handler));
				}
			}
			if (!handler.containService()) {
				throw new RuntimeException(
						"Service of class " + handler.getClasspath() + " with id " + handler.getId() + " is null.");
			}
			boolean wrapped = this.classesConfig.isWrapped(handler.getClasspath());
			boolean delegate = false; // if delegate equals true then the wrapper doesn't overwrite the method.
			Method method = null;
			if (wrapped) {
				// This clazz is wrapped.
				String wrapperClazz = this.classesConfig.getWrapperClasspath(handler.getClasspath());
				// Find out if the method is 'overwritten' in the wrapper.
				// If it isn't overwritten use the clazz itself to get the method.
				method = this.getMethod(Class.forName(wrapperClazz), opPieces.getMethodname(), inputList);
				delegate = false;
			}
			if (method == null) { // either this clazz isn't wrapped or the method wasn't overwritten.
				delegate = true;
				method = this.getMethod(Class.forName(handler.getClasspath()), opPieces.getMethodname(), inputList);
			}
			if (method == null) { // The method is still not found.
				throw new UnsupportedOperationException(
						"Cannot invoke " + opPieces.getMethodname() + " for types " + inputList.toString()
								+ ". The method does not exist in class " + opPieces.getClasspath() + ".");
			}
			logger.info("Determined method {} of class {} for execution", method,
					wrapped ? this.classesConfig.getWrapperClasspath(handler.getClasspath()) : handler.getClasspath());

			/* rewrite values according to the choice */
			Class<?>[] requiredTypes = method.getParameterTypes();
			// logger.info("Values that will be used: {}", Arrays.toString(values));
			inputArgs = this.otms.objectArrayFromSemantic(requiredTypes, inputList);
			// invoke method from service.
			// service is the wrapper object itself if the service is set to be wrapped in
			// the config.
			if (wrapped && delegate) {
				// if wrapped and delegate then the method is defined in the delegate object of
				// the wrapper
				basicResult = method.invoke(((ServiceWrapper) handler.getService()).delegate, inputArgs);
			} else {
				// No delegation method can be found in class from the object of service:
				basicResult = method.invoke(handler.getService(), inputArgs);
			}
			// if(handler.isSerialized()) {
			// try {
			// FileUtil.serializeObject(handler.getService(),
			// getServicePath(handler.getClasspath(),
			// handler.getId()));
			// }
			// catch(Exception ex) {
			// logger.error("Can't serialize class: " + handler.getClasspath()+ ".
			// Serialization throws
			// Exception: " + ex.getMessage());
			// }
			// }
			logger.info("Finished invocation of {}. Result is {}", method,
					basicResult != null ? basicResult.toString().replaceAll("\n", "\\n") : null);
			ServiceManager.SINGLETON().addService(handler);
			resultKeywordMap = this.classesConfig.getMethodResultMap(handler.getClasspath(), opPieces.getMethodname());
			if (logger.isDebugEnabled()) {
				// logger.debug("Invocation done. Result is: {}", basicResult);
			}
		}
		/* compute the result of the invocation (resolve call-by-reference outputs) */
		OperationInvocationResult result = new OperationInvocationResult();

		for (String key : resultKeywordMap.keySet()) {
			String val = resultKeywordMap.get(key);
			if (val.equals("return")) {
				result.put(key, basicResult);
			} else if (val.matches("i[\\d]+")) {
				int inputIndex = Integer.parseInt(val.substring(1));
				result.put(key, inputArgs[inputIndex - 1]);
			} else {
				logger.error("Cannot process result map entry {}", val);
				throw new RuntimeException("Cannot process result map entry " + val);
			}
		}

		/* now update state table based on result mapping */
		for (String key : result.keySet()) {
			VariableParam targetParam = outputMapping.get(new VariableParam(key));
			if (targetParam == null) {
				throw new IllegalArgumentException("The parameter " + key + " used in the result mapping of " + opName
						+ " is not a declared output parameter of the operation! " + "Declared output params are: "
						+ operationInvocation.getOperation().getOutputParameters());
			}
			String nameOfStateVariableToStoreResultIn = targetParam.getName();
			Object processedResult = result.get(key);
			JASEDataObject objectToStore = null;
			if (processedResult != null) {
				objectToStore = this.otms.allToSemantic(processedResult, false);
				// TODO do we really need to translate to json again?
			}
			envState.addField(nameOfStateVariableToStoreResultIn, objectToStore);
		}
	}

	// /**
	// * Creates the file path for the given classpath and serviceid.
	// * @param serviceClasspath classpath of the service.
	// * @param serviceId id of the service.
	// * @return file path to the service.
	// */
	// private String getServicePath(String serviceClasspath, String serviceId) {
	// return folder + File.separator + "objects" + File.separator +
	// serviceClasspath + File.separator +
	// serviceId;
	// }

	private Constructor<?> getConstructor(final Class<?> clazz, final List<JASEDataObject> inputs) {
		if (!this.classesConfig.classknown(clazz.getName())) {
			throw new IllegalArgumentException("This server is not configured to create new objects of " + clazz);
		}
		for (Constructor<?> constr : clazz.getDeclaredConstructors()) {
			Class<?> requiredParams[] = constr.getParameterTypes();
			if (this.matchParameters(requiredParams, inputs)) {
				return constr;
			}
		}
		return null;
	}

	private boolean matchParameters(final Class<?>[] requiredTypes, final List<JASEDataObject> providedTypes) {
		if (requiredTypes.length > providedTypes.size()) {
			return false;
		}
		for (int i = 0; i < requiredTypes.length; i++) {
			JASEDataObject providedData = providedTypes.get(i);
			if (!this.otms.isLinkImplemented(providedTypes.get(i).getType(), requiredTypes[i])) {
				logger.debug("The required type is: ",
						requiredTypes[i] + " but the provided one has semantic type of " + requiredTypes[i]);
				return false;
			}
		}
		return true;
	}

	private Method getMethod(final Class<?> clazz, final String methodName, final List<JASEDataObject> providedTypes) {
		if (!this.classesConfig.methodKnown(clazz.getName(), methodName)) {
			logger.warn("The operation " + clazz.getName() + "::" + methodName + " is not supported by this server.");
			return null;
		}
		for (Method method : clazz.getMethods()) {
			if (!method.getName().equals(methodName)) {
				continue;
			}
			Class<?> requiredParams[] = method.getParameterTypes();
			if (this.matchParameters(requiredParams, providedTypes)) {
				return method;
			} else {
				logger.debug("Method {} with params {} matches the required method name but is not satisfied by ",
						methodName, Arrays.toString(requiredParams), providedTypes);
			}
		}
		return null;
	}

	/**
	 * Returns true if the given text starts with a host name. See
	 * 'containsHostPattern'
	 */
	private boolean startsWithHost(final String text) {
		return containsHostPattern.matcher(text).find();
	}

	private String extractHost(final String text) {
		Matcher matcher = containsHostPattern.matcher(text);
		if (matcher.find()) {
			return matcher.group();
		} else {
			return "";
		}
	}

	public HttpServiceServer(final int port) throws IOException {
		this(port, "conf/classifiers.json", "conf/preprocessors.json", "conf/others.json");
	}

	/**
	 * Creates the standard test server.
	 */
	public static HttpServiceServer TEST_SERVER() throws IOException {
		return new HttpServiceServer(8000, "testrsc/conf/classifiers.json", "testrsc/conf/preprocessors.json",
				"testrsc/conf/others.json");
	}

	public HttpServiceServer(final int port, final String... FILE_CONF_CLASSES) throws IOException {
		/*
		 * moved the operation configuration into the classes.json configuration for
		 * more flexibility.
		 */
		this.classesConfig = new ClassesConfiguration(FILE_CONF_CLASSES);
		this.otms = new OntologicalTypeMarshallingSystem();
		new HttpServiceClient(this.otms);
		this.server = HttpServer.create(new InetSocketAddress(port), 100);

		// Set an Executor for the multi-threading
		AtomicInteger counter = new AtomicInteger(1);
		this.server.setExecutor(task -> {
			// a new thread for each request:
			Thread handlerThread = new Thread(task, "HttpServiceServer-worker-" + counter.getAndIncrement());
			handlerThread.start();
		});

		this.server.createContext("/", new JavaClassHandler());
		this.server.start();
		logger.info("Server is up ...");

		HttpServiceObserver.StartServer(port + 1000);
	}

	public void shutdown() {
		this.server.stop(0);
		HttpServiceObserver.CloseServer();
	}

	public static void main(final String[] args) throws Exception {
		// new HttpServiceServer(8000);
		TEST_SERVER();
	}

	public ClassesConfiguration getClassesConfig() {
		return this.classesConfig;
	}
}