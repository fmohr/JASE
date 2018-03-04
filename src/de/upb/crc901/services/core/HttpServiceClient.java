/**
 * HttpServiceClient.java
 * Copyright (C) 2017 Paderborn University, Germany
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

import de.upb.crc901.configurationsetting.compositiondomain.CompositionDomain;
import de.upb.crc901.configurationsetting.operation.OperationInvocation;
import de.upb.crc901.configurationsetting.operation.SequentialComposition;
import de.upb.crc901.configurationsetting.serialization.SequentialCompositionSerializer;

import jaicore.logic.fol.structure.LiteralParam;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.SocketException;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.math.NumberUtils;

public class HttpServiceClient {

  private final OntologicalTypeMarshallingSystem otms;

  public HttpServiceClient(final OntologicalTypeMarshallingSystem otms) {
    super();
    this.otms = otms;

  }

  public ServiceCompositionResult sendCompositionRequest(final String host, final HttpBody body, final int timeoutInSeconds) throws IOException, InterruptedException {
    // use choreography specific url
    return this.sendRequest(host, "choreography", body, timeoutInSeconds);
  }

  public ServiceCompositionResult sendRequest(final String host, final String operation, final HttpBody body, final int timeoutInSeconds) throws IOException, InterruptedException {
    if (Thread.interrupted()) {
      throw new InterruptedException();
    }
    URL url = new URL("http://" + host + "/" + operation);
    this.translateServiceHandlers(body.getState(), host, "local");
    HttpURLConnection con = (HttpURLConnection) url.openConnection();
    con.setConnectTimeout(2000);
    con.setReadTimeout(1000 * timeoutInSeconds);
    con.setChunkedStreamingMode(1 << 20); // 1 MByte buffer
    con.setRequestProperty("Content-Type", "application/json");
    con.setRequestMethod("POST");
    con.setDoOutput(true);
    TimeLogger.STOP_TIME("Sending data started");
    /* send data */
    OutputStream out = con.getOutputStream();
    body.writeBody(out);
    TimeLogger.STOP_TIME("Sending data concluded");

    /* read and return answer */

    System.out.println("Waiting for response");
    Semaphore s = new Semaphore(0);
    final AtomicInteger responseCode = new AtomicInteger(0);

    new Thread(new Runnable() {

      @Override
      public void run() {
        try {
          responseCode.set(con.getResponseCode());
          s.release();
        } catch (SocketException e) {
          if (e.getMessage().equals("Socket Closed")) {
            System.out.println("Socket has been closed. Finishing thread that waits for response code.");
          } else {
            e.printStackTrace();
          }
        } catch (IOException e) {
          e.printStackTrace();
        }

      }
    }).start();

    /* launch thread that communicates that we are still listening */
    // AtomicBoolean alive = new AtomicBoolean(true);
    // new Thread(new Runnable() {
    //
    // @Override
    // public void run() {
    // try {
    // while (alive.get()) {
    // synchronized (con) {
    // if (!alive.get())
    // return;
    // out.write("alive".getBytes());
    // }
    // Thread.sleep(1 * 1000);
    // }
    // } catch (IOException e) {
    // e.printStackTrace();
    // } catch (InterruptedException e) {
    // e.printStackTrace();
    // }
    // }
    // }).start();

    /*
     * wait for response of server. In case of timeout, close connection (and thereby notify server that
     * the process is canceled)
     */
    try {
      s.acquire();
    } catch (Throwable e) {
      synchronized (con) {
        // alive.set(false);
        System.out.println("DISCONNECTING");
        con.disconnect();
      }
      throw e;
    }

    /* now process result */
    // alive.set(false);
    System.out.println("Received response: " + responseCode + ".");
    HttpBody returnedBody = new HttpBody();
    if (responseCode.get() == 200) {
      try (InputStream in = con.getInputStream()) {
        returnedBody.readfromBody(in);
      } catch (IOException ex) {
        ex.printStackTrace();
      } catch (Exception ex) {
        ex.printStackTrace();
      }
      ServiceCompositionResult result = new ServiceCompositionResult();
      this.translateServiceHandlers(returnedBody.getState(), "local", host);
      result.addBody(returnedBody);
      return result;
    } else {
      try (InputStream in = con.getErrorStream()) {
        String theString = IOUtils.toString(in, Charset.defaultCharset());
        throw new RuntimeException(theString.replaceAll("\n", "\n\t\t"));
      } catch (IOException ex) {
        ex.printStackTrace();
      }
      return null; // TODO correct error handling
    }
  }

  /**
   * Changes the host attribute of all servicehandlers in the given envirenment state map.
   */
  public void translateServiceHandlers(final EnvironmentState envState, final String from, final String to) {
    for (String field : envState.serviceHandleFieldNames()) {

      ServiceHandle serviceHandle = (ServiceHandle) envState.retrieveField(field).getData();
      if (!serviceHandle.getHost().equals(from)) {
        continue;
      }
      serviceHandle = serviceHandle.withExternalHost(to);
      envState.addField(field, new JASEDataObject(ServiceHandle.class.getSimpleName(), serviceHandle));

    }
  }

  public ServiceCompositionResult callServiceOperation(final String serviceCall, final Object... inputs) throws IOException {
    return this.callServiceOperation(ServiceUtil.getOperationInvocation(serviceCall, inputs), new SequentialComposition(new CompositionDomain()), inputs);
  }

  public ServiceCompositionResult callServiceOperation(final OperationInvocation call, final SequentialComposition coreography) throws IOException {
    return this.callServiceOperation(call, coreography, new HashMap<>());
  }

  public ServiceCompositionResult callServiceOperation(final OperationInvocation call, final SequentialComposition coreography, final Object... additionalInputs)
      throws IOException {

    return this.callServiceOperation(call, coreography, ServiceUtil.getObjectInputMap(additionalInputs));
  }

  public ServiceCompositionResult callServiceOperation(final OperationInvocation call, final SequentialComposition coreography, final Map<String, Object> additionalInputs,
      final int timeoutInSeconds) throws IOException, InterruptedException {

    /* prepare data */
    // TODO coreography should have a indexof method
    int index = 0;
    for (OperationInvocation opInv : coreography) {
      if (opInv.equals(call)) {
        break;
      }
      index++;
    }
    // get the coreography string
    String serializedCoreography = null;
    if (coreography.iterator().hasNext()) { // if coreography is not empty // TODO coreography::isEmpty method
      serializedCoreography = new SequentialCompositionSerializer().serializeComposition(coreography);
    }
    // create body, encode it and write it to the outputstream.
    HttpBody body = new HttpBody(); // new HttpBody(additionalInputs, serializedCoreography, index, -1);
    body.setComposition(serializedCoreography);
    body.setCurrentIndex(index);
    for (String keyword : additionalInputs.keySet()) {
      Object input = additionalInputs.get(keyword);
      JASEDataObject parsedSemanticInput = null;
      if (!(input instanceof JASEDataObject)) {
        // first parse object
        parsedSemanticInput = this.otms.allToSemantic(input, false);
      } else {
        parsedSemanticInput = (JASEDataObject) input; // the given input is already semantic complient.
      }
      body.addKeyworkArgument(keyword, parsedSemanticInput);
    }

    /* setup connection */

    /* separate service and operation from name */
    String opFQName = call.getOperation().getName();
    String[] hostservice_OpTupel = opFQName.split("::", 2);
    // split the opFQName into service (classpath or objectname) and operation name (function name).

    String host;
    URL url;
    if (serializedCoreography != null) {
      String serviceKey = hostservice_OpTupel[0];
      if (serviceKey.contains("/")) {
        String[] host_serviceTupel = hostservice_OpTupel[0].split("/", 2);
        host = host_serviceTupel[0]; // hostname e.g.: 'localhost:5000'
      } else if (!additionalInputs.containsKey(serviceKey)) {
        throw new IllegalArgumentException("Want to execute composition with first service being " + serviceKey + ", but no service handle is given in the additional inputs.");
      } else {
        ServiceHandle handle = (ServiceHandle) additionalInputs.get(serviceKey);
        host = handle.getHost();
      }
      return this.sendCompositionRequest(host, body, timeoutInSeconds);
    } else {
      String[] host_serviceTupel = hostservice_OpTupel[0].split("/", 2);
      host = host_serviceTupel[0]; // hostname e.g.: 'localhost:5000'
      String service = host_serviceTupel[1]; // service name e.g.: 'packagePath.Constructor'
      // If no '::' is given, assume its a '__construct' call.
      String opName = hostservice_OpTupel.length > 1 ? hostservice_OpTupel[1] : "__construct";
      return this.sendRequest(host, service + "/" + opName, body, timeoutInSeconds);
    }
  }

  public ServiceCompositionResult invokeServiceComposition(final SequentialComposition composition, final Map<String, Object> inputs) throws IOException {

    /* check first that all inputs are given */
    Collection<String> availableInputs = new HashSet<>(inputs.keySet());
    for (OperationInvocation opInv : composition) {
      for (LiteralParam l : opInv.getInputMapping().values()) {
        boolean isNumber = NumberUtils.isNumber(l.getName());
        boolean isString = !isNumber && l.getName().startsWith("\"") && l.getName().endsWith("\"");
        if (!availableInputs.contains(l.getName()) && !isNumber && !isString) {
          throw new IllegalArgumentException("Parameter " + l.getName() + " required for " + opInv + " is missing in the invocation.");
        }
      }
      availableInputs.addAll(opInv.getOutputMapping().values().stream().map(p -> p.getName()).collect(Collectors.toList()));
    }

    /* get fist service call and invoke it with the rest of the composition as a choreography */
    OperationInvocation first = composition.iterator().next();
    return this.callServiceOperation(first, composition, inputs);
  }

  public ServiceCompositionResult invokeServiceComposition(final SequentialComposition composition, final Object... additionalInputs) throws IOException {
    return this.invokeServiceComposition(composition, ServiceUtil.getObjectInputMap(additionalInputs));
  }
}
