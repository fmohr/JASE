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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import de.upb.crc901.configurationsetting.compositiondomain.CompositionDomain;
import de.upb.crc901.configurationsetting.logic.LiteralParam;
import de.upb.crc901.configurationsetting.logic.VariableParam;
import de.upb.crc901.configurationsetting.operation.OperationInvocation;
import de.upb.crc901.configurationsetting.operation.SequentialComposition;

import jaicore.basic.FileUtil;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.Serializable;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.apache.commons.lang3.math.NumberUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HttpServiceServer {

  public static final Logger logger = LoggerFactory.getLogger(HttpServiceServer.class);

  private static File folder = new File("http");

  private final HttpServer server;
  private final HttpServiceClient clientForSubSequentCalls;
  private final OntologicalTypeMarshallingSystem otms;
  private final ClassesConfiguration classesConfig;
  // private final Set<String> supportedOperations = new HashSet<>();
  // private final Map<String, Map<String, String>> resultMaps = new HashMap<>();

  class ServiceHandle {
    private final String id;
    private final Object service;

    /**
     * Standard constructor
     * 
     * @param id
     *          Id which the service can be accessed through
     * @param service
     *          inner service
     */
    ServiceHandle(final String id, final Object service) {
      super();
      this.id = id;
      this.service = service;
    }

    /**
     * Constructor which is used to indicate that the serialization has failed.
     * 
     * @param service
     *          inner service
     */
    ServiceHandle(final Object service) {
      super();
      this.id = null;
      this.service = service;
    }

    /**
     * If id is set to null, the service couldn't be serialized (see the invokeOperation method) This
     * the server shouldn't return this servicehandle to the client. (see
     * 
     * @return True if the inner service was serialized to disk.
     */
    public boolean wasSerialized() {
      return this.id != null;
    }

    /**
     * Returns the id of the service. Throws Runtime-Exception if wasSerialized() returns false.
     */
    public String getId() {
      if (this.wasSerialized()) {
        return this.id;
      } else {
        // this service wasn't serialized. so the id shouldn't be accessed.
        throw new RuntimeException("The service wasn't serialized. Can't access the id.");
      }
    }
  }

  class JavaClassHandler implements HttpHandler {

    @Override
    public void handle(final HttpExchange t) throws IOException {

      String response = "";
      try {

        /* determine method to be executed */
        String address = t.getRequestURI().getPath().substring(1);
        logger.info("Received query for {}", address);

        /*
         * initiate state with the non-constant inputs given in post (non-int and non-doubles are treated as
         * strings)
         */
        HttpBody body = HttpBody.decode(t, HttpServiceServer.this.otms);

        String[] parts = address.split("/", 3);
        String clazz = parts[0];
        String objectId = null;
        if (parts.length > 1) { // address contains objectId. this request will therefore be handled as a service call.
          objectId = parts[1];
        } else if (clazz.equals("choreography")) { // choreography call:
          if (!body.containsCoreography()) {
            response += "objectID and no choreography was given.";
            throw new RuntimeException(response);
          }
          address = body.getOperation(body.getCurrentIndex()).getOperation().getName();
          parts = address.split("/", 3);
          clazz = parts[0];
          if (parts.length > 1) { // address contains objectId. this request will therefore be handled as a service call.
            objectId = parts[1];
          }

        }
        if (objectId == null) {
          response += "The address: " + address + " can't be handled by this server.";
          throw new RuntimeException(response);
        }

        Map<String, Object> initialState = body.getInputs();
        Map<String, Object> state = new HashMap<>(initialState);
        logger.info("Input keys are: {}", initialState.keySet());

        /*
         * analyze choreography in order to see what we actually will execute right away: 1. move to
         * position of current call; 2. compute all subsequent calls on same host and on services spawend
         * from here
         **/
        SequentialComposition comp = null;
        SequentialComposition subsequenceComp = new SequentialComposition(new CompositionDomain());
        OperationInvocation invocationToMakeFromHere = null;
        if (body.containsCoreography()) {
          comp = body.getComposition();
          Iterator<OperationInvocation> it = comp.iterator();
          Collection<String> servicesInExecEnvironment = new HashSet<>();
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
              // String host = opName.substring(0, opName.indexOf("/"));
              // String myAddress = t.getLocalAddress().toString().substring(1);
              // if (!host.equals(myAddress))
              // break;
              if (!HttpServiceServer.this.canExecute(opInv)) { // if this server can't execute this operation exit the loop here. invocationToMakeFromHere will
                                                               // then contain the address of the next invocation.
                break;
              }
              /* if this is a constructor, also add the created instance to the locally available services */
              servicesInExecEnvironment.add(opName);
              if (opName.contains("__construct")) {
                servicesInExecEnvironment.add(opInv.getOutputMapping().values().iterator().next().getName());
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
            opinv = ServiceUtil.getOperationInvocation(t.getLocalAddress().toString().substring(1) + "/" + clazz + "::__construct", state);

          } else {
            opinv = ServiceUtil.getOperationInvocation(t.getLocalAddress().toString().substring(1) + "/" + clazz + "/" + objectId + "::" + parts[2], state);
          }
          subsequenceComp.addOperationInvocation(opinv);
        }

        /* execute the whole induced composition */
        for (OperationInvocation opInv : subsequenceComp) {
          HttpServiceServer.this.invokeOperation(opInv, state);
        }
        logger.info("Finished local execution. Now invoking {}", invocationToMakeFromHere);

        /* call next service */
        if (invocationToMakeFromHere != null) {

          /* extract vars from state that are in json (ordinary data but not service references) */
          Map<String, Object> inputsToForward = new HashMap<>();
          for (String key : state.keySet()) {
            Object val = state.get(key);
            if (val instanceof JsonNode) {
              inputsToForward.put(key, val);
            }
          }
          ServiceCompositionResult result = HttpServiceServer.this.clientForSubSequentCalls.callServiceOperation(invocationToMakeFromHere, comp, inputsToForward);
          response += result.toString();
          logger.info("Received answer from subsequent service.");
        }

        /* now returning the serializations of all created (non-service) objects */
        logger.info("Returning answer to sender");
        ObjectNode objectsToReturn = new ObjectMapper().createObjectNode();
        for (String key : state.keySet()) {
          Object answerObject = state.get(key);
          if (!initialState.containsKey(key)) {
            if (answerObject == null) {
              objectsToReturn.putNull(key);
            } else if ((answerObject instanceof ObjectNode)) {
              objectsToReturn.set(key, (JsonNode) answerObject);
            } else if (answerObject instanceof String) {
              objectsToReturn.put(key, (String) answerObject);
            } else if (answerObject instanceof Double) {
              objectsToReturn.put(key, (Double) answerObject);
            } else if (answerObject instanceof Integer) {
              objectsToReturn.put(key, (Integer) answerObject);
            } else if (answerObject instanceof ServiceHandle) {
              ServiceHandle handle = (ServiceHandle) answerObject;
              if (handle.wasSerialized()) {
                // Return the handle only if the service was serialized and can be accessed.
                objectsToReturn.put(key, t.getLocalAddress().toString().substring(1) + "/" + clazz + "/" + handle.getId());
              }
            } else {
              throw new IllegalArgumentException("Do not know how to treat object " + answerObject + " as it is not serialized to some json thing");
            }
          }
        }
        response += objectsToReturn.toString();
      } catch (InvocationTargetException e) {
        e.getTargetException().printStackTrace();
      } catch (Throwable e) {
        e.printStackTrace();
      } finally {
        t.sendResponseHeaders(200, response.length());
        OutputStream os = t.getResponseBody();
        os.write(response.getBytes());
        os.close();
      }

    }

  }

  private boolean canExecute(final OperationInvocation opInv) {
    String opName = opInv.getOperation().getName();

    if (opName.contains("__construct")) {
      // extract class name
      String clazz = opName.substring(opName.indexOf("/") + 1).split("::")[0];
      return this.classesConfig.classknown(clazz); // returns true if the class is known.
    } else { // lets hope we know how to execute this. TODO see if there are cases where we can't execute an op
             // without '__construct'
      return true;
    }
  }

  private void invokeOperation(final OperationInvocation operationInvocation, final Map<String, Object> state)
      throws IllegalAccessException, IllegalArgumentException, InvocationTargetException, NoSuchMethodException, SecurityException, ClassNotFoundException, InstantiationException {
    logger.info("Performing invocation {} in state {}", operationInvocation, state.keySet());
    List<VariableParam> inputs = operationInvocation.getOperation().getInputParameters();
    String[] types = new String[inputs.size()];
    Object[] values = new Object[inputs.size()];
    Map<VariableParam, LiteralParam> inputMapping = operationInvocation.getInputMapping();
    for (int j = 0; j < inputs.size(); j++) {
      String val = inputMapping.get(inputs.get(j)).getName();

      /* first try native types */
      if (NumberUtils.isNumber(val)) {
        if (val.contains(".")) {
          values[j] = Double.valueOf(val);
        } else {
          types[j] = Integer.TYPE.getName();
          values[j] = Integer.valueOf(val);
        }
      } else if (val.startsWith("\"") && val.endsWith("\"")) {
        types[j] = String.class.getName();
        values[j] = val.substring(1, val.length() - 1); // remove quotation marks
      }

      /* if the value is a variable in our current state, use this */
      else if (state.containsKey(val)) {
        JsonNode var = (JsonNode) state.get(val);
        if (var.isNumber()) {
          if (var.asText().contains(".")) {
            values[j] = var.asDouble();
          } else {
            types[j] = Integer.TYPE.getName();
            values[j] = var.asInt();
          }
        } else if (var.isTextual()) {
          types[j] = String.class.getName();
          values[j] = var.asText();
        } else {
          types[j] = var.get("type").asText();
          values[j] = var;
        }
      } else {
        throw new IllegalArgumentException("Cannot find value for argument " + val + " in state table.");
      }
    }

    /* if this operation is a constructor, create the corresponding service and return the url */
    String opName = operationInvocation.getOperation().getName();
    Map<VariableParam, VariableParam> outputMapping = operationInvocation.getOutputMapping();
    String clazz = "";
    String methodName = "";
    String fqOpName = "";
    Object basicResult = null;
    if (opName.contains("/")) { // if this is a service called via an address

      logger.info("Run invocation on a service that has not been created previously");

      fqOpName = opName;
      String[] parts = opName.substring(opName.indexOf("/") + 1).split("::");
      clazz = parts[0];
      methodName = parts[1];
      if (methodName.equals("__construct")) {
        logger.info("The invocation creates a new service instance");
        Class serviceClass = null;
        try {
          serviceClass = Class.forName(clazz);
          ;
        } catch (java.lang.ClassNotFoundException ex) {
          logger.error("CLASS NOT FOUND : " + clazz);
          return;
        }
        Constructor<?> constructor = this.getConstructor(serviceClass, types);
        if (logger.isDebugEnabled()) {
          logger.debug("{}/{}/{}", types.length, constructor.getParameterCount(), constructor);
        }

        Object newService = null;

        boolean wrapped = this.classesConfig.isWrapped(clazz); // true if this class is supposed to be wrapped.
        ServiceWrapper wrapper = null;
        if (wrapped) { // create the wrapper.
          String wrapperClasspath = this.classesConfig.getWrapperClasspath(clazz);
          Class<?> wrapperClass = Class.forName(wrapperClasspath);
          Constructor<? extends ServiceWrapper> wrapperConstructor = (Constructor<? extends ServiceWrapper>) wrapperClass.getConstructor(ServiceWrapper.CONSTRUCTOR_TYPES);
          // create the wrapper by giving it the constructor and the values.
          wrapper = wrapperConstructor.newInstance(constructor, values);
          newService = wrapper.getDelegate();
        } else {
          // create the service itself;
          newService = constructor.newInstance(values);
        }
        // create service handle by identifying the service with an unique identifier.
        String id = UUID.randomUUID().toString();
        ServiceHandle sh;
        if (!wrapped) {
          sh = new ServiceHandle(id, newService);
        } else {
          sh = new ServiceHandle(id, wrapper);
        }
        boolean serializationSuccess = false; // be pessimistic about result. Set to true if it worked.
        // if wrapped and wrappers'delegate can be serialized or it wasn't wrapped and the service itself
        // can be serialized.
        if (newService instanceof Serializable) {
          /* serialize result */
          try {
            FileUtil.serializeObject(wrapped ? wrapper : newService, this.getServicePath(clazz, id));
            // no problems occurred.. success
            serializationSuccess = true;
          } catch (IOException e) {
            logger.error(e.getMessage() + "\n" + Arrays.toString(e.getStackTrace()));
          }
        }
        if (!serializationSuccess) {
          // serialization wasn't successful.
          /*
           * reset the ServiceHandle with id = null to indicate that this service wasn't serialized
           * successfully.
           */
          sh = new ServiceHandle(sh.service);
        }
        // Put the handler into the state.
        state.put(outputMapping.values().iterator().next().getName(), sh);
        return;
      }

      /* otherwise execute the operation on the given service */
      else {

        logger.info("Run invocation on an existing service instance");
        clazz = parts[0].substring(0, parts[0].lastIndexOf("/"));
        String objectId = parts[0].substring(clazz.length() + 1);
        boolean wrapped = this.classesConfig.isWrapped(clazz);
        boolean delegate = false; // if delegate equals true then the wrapper doesn't overwrite the method.
        try {
          Method method = null;
          if (wrapped) {
            // This clazz is wrapped.
            String wrapperClazz = this.classesConfig.getWrapperClasspath(clazz);
            // Find out if the method is 'overwritten' in the wrapper.
            // If it isn't overwritten use the clazz itself to get the method.
            method = this.getMethod(Class.forName(wrapperClazz), methodName, types);
            delegate = false;
          }
          if (method == null) { // either this clazz isn't wrapped or the method wasn't overwritten.
            delegate = true;
            method = this.getMethod(Class.forName(clazz), methodName, types);
          }
          if (method == null) { // The method is still not found.
            throw new UnsupportedOperationException("Cannot invoke " + methodName + " for types " + Arrays.toString(types) + ". The method does not exist in class " + clazz + ".");
          }

          Object service = FileUtil.unserializeObject(this.getServicePath(clazz, objectId));

          /* rewrite values according to the choice */
          Class<?>[] requiredTypes = method.getParameterTypes();
          // logger.info("Values that will be used: {}", Arrays.toString(values));
          for (int i = 0; i < requiredTypes.length; i++) {
            if (!requiredTypes[i].isPrimitive() && !requiredTypes[i].getName().equals("String")) {
              logger.debug("map {}-th input to {}", i, requiredTypes[i].getName());
              if (values[i] instanceof ObjectNode) {
                // System.out.print(values[i] + " -> ");
                // System.out.println(otms.jsonToObject((JsonNode) values[i], requiredTypes[i]));
              }
              values[i] = this.otms.jsonToObject((JsonNode) values[i], requiredTypes[i]);
            }
          }
          try {
            // invoke method from service.
            // service is the wrapper object itself if the service is set to be wrapped in the config.
            if (wrapped && delegate) {
              // if wrapped and delegate then the method is defined in the delegate object of the wrapper
              basicResult = method.invoke(((ServiceWrapper) service).delegate, values);
            } else {
              // No delegation method can be found in class from the object of service:
              basicResult = method.invoke(service, values);
            }
          } catch (Exception e) {
            logger.error(operationInvocation + " error: " + e.getMessage());
          }
          if (logger.isDebugEnabled()) {
            logger.debug("Invocation ready. Result is: {}", basicResult);
          }
          FileUtil.serializeObject(service, this.getServicePath(clazz, objectId));

        } catch (IOException e) {
          e.printStackTrace();
        }
      }
    } else { // if this is a call on a created services (this cannot be a constructor)
      String[] parts = opName.split("::");
      String objectId = parts[0];
      Object object = state.get(objectId);
      methodName = parts[1];

      if (object instanceof ServiceHandle) {
        object = ((ServiceHandle) object).service;
      }
      clazz = object.getClass().getName();

      Method method = this.getMethod(object.getClass(), methodName, types);
      if (method == null) {
        // method wasn't found.
        // maybe this service is wrapped.
        if (object instanceof ServiceWrapper) {
          object = ((ServiceWrapper) object).delegate;
          method = this.getMethod(object.getClass(), methodName, types);
        }
      }
      if (!(object instanceof ServiceWrapper)) {
        fqOpName = object.getClass().getName() + "::" + methodName;
      } else {
        // its a wrapper
        fqOpName = ((ServiceWrapper) object).delegate.getClass().getName() + "::" + methodName;
      }
      /* parse semantic types to the required types */
      Class<?>[] requiredTypes = method.getParameterTypes();
      for (int i = 0; i < requiredTypes.length; i++) {
        if (!requiredTypes[i].isPrimitive() && !requiredTypes[i].getName().equals("String")) {
          values[i] = this.otms.jsonToObject((JsonNode) values[i], requiredTypes[i]);
        }
      }

      /* check argument count and then execute the method */
      assert (method.getParameterCount() == values.length) : "Required number of parameters: " + method.getParameterCount() + " but " + values.length + " are given.";
      basicResult = method.invoke(object, values);
    }

    /* compute the result of the invocation (resolve call-by-reference outputs) */
    OperationInvocationResult result = new OperationInvocationResult();

    Map<String, String> map = this.classesConfig.getMethodResultMap(clazz, methodName);
    for (String key : map.keySet()) {
      String val = map.get(key);
      if (val.equals("return")) {
        result.put(key, basicResult);
      } else if (val.matches("i[\\d]+")) {
        int inputIndex = Integer.parseInt(val.substring(1));
        result.put(key, values[inputIndex - 1]);
      } else {
        logger.error("Cannot process result map entry {}", val);
      }
    }

    /* now update state table based on result mapping */
    for (String key : result.keySet()) {
      VariableParam targetParam = outputMapping.get(new VariableParam(key));
      if (targetParam == null) {
        throw new IllegalArgumentException("The parameter " + key + " used in the result mapping of " + fqOpName + " is not a declared output parameter of the operation! "
            + "Declared output params are: " + operationInvocation.getOperation().getOutputParameters());
      }
      String nameOfStateVariableToStoreResultIn = targetParam.getName();
      Object processedResult = result.get(key);
      Object objectToStore = null;
      if (processedResult != null) {
        if (processedResult instanceof Number || processedResult instanceof String) {
          objectToStore = processedResult;
        } else {
          objectToStore = this.otms.objectToJson(processedResult); // TODO do we really need to translate to json again?
        }
      }
      state.put(nameOfStateVariableToStoreResultIn, objectToStore);
    }
  }

  /**
   * Creates the file path for the given classpath and serviceid.
   * 
   * @param serviceClasspath
   *          classpath of the service.
   * @param serviceId
   *          id of the service.
   * @return file path to the service.
   */
  private String getServicePath(final String serviceClasspath, final String serviceId) {
    return folder + File.separator + "objects" + File.separator + serviceClasspath + File.separator + serviceId;
  }

  private Constructor<?> getConstructor(final Class<?> clazz, final String[] types) {
    if (!this.classesConfig.classknown(clazz.getName())) {
      throw new IllegalArgumentException("This server is not configured to create new objects of " + clazz);
    }
    for (Constructor<?> constr : clazz.getDeclaredConstructors()) {
      Class<?> requiredParams[] = constr.getParameterTypes();
      if (this.matchParameters(requiredParams, types)) {
        return constr;
      }
    }
    return null;
  }

  private boolean matchParameters(final Class<?>[] requiredTypes, final String[] providedTypes) {
    if (requiredTypes.length > providedTypes.length) {
      return false;
    }
    for (int i = 0; i < requiredTypes.length; i++) {
      if (requiredTypes[i].isPrimitive()) {
        if (!requiredTypes[i].getName().equals(providedTypes[i])) {
          return false;
        }
      } else {
        if (!this.otms.isLinkImplemented(providedTypes[i], requiredTypes[i])) {
          logger.debug("The required type is: ", requiredTypes[i] + " but the provided one has semantic type of " + requiredTypes[i]);
          return false;
        }
      }
    }
    return true;
  }

  private Method getMethod(final Class<?> clazz, final String methodName, final String[] types) {
    if (!this.classesConfig.methodKnown(clazz.getName(), methodName)) {
      logger.info("The operation " + clazz.getName() + "::" + methodName + " is not supported by this server.");
      return null;
    }
    for (Method method : clazz.getMethods()) {
      if (!method.getName().equals(methodName)) {
        continue;
      }
      Class<?> requiredParams[] = method.getParameterTypes();
      if (this.matchParameters(requiredParams, types)) {
        return method;
      } else {
        logger.debug("Method {} with params {} matches the required method name but is not satisfied by ", methodName, Arrays.toString(requiredParams), Arrays.toString(types));
      }
    }
    return null;
  }

  public HttpServiceServer(final int port) throws IOException {
    this(port, "conf/classes.json");
  }

  /**
   * Creates the standard test server.
   */
  public static HttpServiceServer TEST_SERVER() throws IOException {
    return new HttpServiceServer(8000, "testrsc/conf/classes.json");
  }

  public HttpServiceServer(final int port, final String FILE_CONF_CLASSES) throws IOException {
    /* moved the operation configuration into the classes.json configuration for more flexibility. */
    // for (String op : FileUtil.readFileAsList(FILE_CONF_OPS)) {
    // if (op.trim().startsWith("#") || op.trim().isEmpty())
    // continue;
    // String[] split = op.split("\t");
    // String opName = split[0].trim();
    // supportedOperations.add(opName);
    // // TODO
    // /* if an output mapping is given, add it */
    // if (split.length > 1) {
    // String map = split[split.length - 1].trim();
    // if (!map.startsWith("{") || !map.endsWith("}")) {
    // logger.warn("Result mapping {} for operator is malformed", map, split[0]);
    // }
    // resultMaps.put(opName, new HashMap<>());
    // Map<String, String> resultMap = resultMaps.get(opName);
    // for (String mapEntry : map.substring(1, map.length() - 1).split(",")) {
    // String[] kv = mapEntry.split("=");
    // if (kv.length != 2) {
    // logger.error("Entry {} in result mapping {} is malformed", mapEntry, map);
    // } else {
    // resultMap.put(kv[0].trim(), kv[1].trim());
    // }
    // }
    // }
    // }
    this.classesConfig = new ClassesConfiguration(FILE_CONF_CLASSES);
    this.otms = new OntologicalTypeMarshallingSystem();
    this.clientForSubSequentCalls = new HttpServiceClient(this.otms);
    this.server = HttpServer.create(new InetSocketAddress(port), 0);
    this.server.createContext("/", new JavaClassHandler());
    this.server.start();
    logger.info("Server is up ...");
  }

  public void shutdown() {
    this.server.stop(0);
  }

  public static void main(final String[] args) throws Exception {
    new HttpServiceServer(8000);
  }

  public ClassesConfiguration getClassesConfig() {
    return this.classesConfig;
  }
}