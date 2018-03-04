package de.upb.crc901.services.core;

import com.fasterxml.jackson.core.JsonEncoding;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;

import de.upb.crc901.configurationsetting.operation.OperationInvocation;
import de.upb.crc901.configurationsetting.operation.SequentialComposition;
import de.upb.crc901.configurationsetting.serialization.SequentialCompositionSerializer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.commons.lang3.reflect.MethodUtils;

/**
 * A data structure class, which encodes or decodes Post body data. This is used by
 * HttpServiceClient, whose data is encoded to the post's body content, and it is also used by
 * HttpServiceServer, who initializes an instance by the encoded post body it receives. (Note: This
 * class was created to take care of logic that was implemented in the client and server class
 * before.)
 *
 * @author aminfaez
 *
 */
public final class HttpBody {

  /**
   * Constant strings used in communication.
   */
  public final static String CHOREOGRAPGY_FIELDNAME = "choreography", CURRENTINDEX_FIELDNAME = "currentindex", MAXINDEX_FIELDNAME = "maxindex", INPUTS_FIELDNAME = "inputs",
      CLIENTID_FIELDNAME = "clientid", REQUESTID_FIELDNAME = "requestid";

  private static final String ARGLIST_FIELDNAME = "$arglist$";

  /**
   * A counter to really ensure unique identifiers even if requests are created within the very same
   * millisecond. Furthermore, this facilitates to track the sequence of requests sent.
   */
  private static final AtomicInteger REQUEST_COUNTER = new AtomicInteger(0);

  private String composition = null;
  private int currentIndex = 0;
  private int maxIndex = -1;
  /**
   * Unique name for identifying the client sending the request, e.g., the current thread.
   */
  private String clientID = "NaN";
  /**
   * Automatically generated request ID.
   */
  private String requestID = System.currentTimeMillis() + "_" + REQUEST_COUNTER.getAndIncrement();

  private EnvironmentState envState = new EnvironmentState();

  private OntologicalTypeMarshallingSystem otms = new OntologicalTypeMarshallingSystem();

  public HttpBody() {

  }

  public HttpBody(final EnvironmentState envState, final String chorepgraphy, final int currentIndex, final int maxindex, final String clientID) {
    this.envState = envState;
    this.composition = chorepgraphy;
    this.currentIndex = currentIndex;
    this.maxIndex = maxindex;
    this.clientID = clientID;
  }

  public boolean containsComposition() {
    return this.composition != null;
  }

  /**
   * @return the composition
   */
  public final String getComposition() {
    if (this.containsComposition()) {
      return this.composition;
    } else {
      return "";
    }
  }

  /**
   * @param composition
   *          the composition to set
   */
  public final void setComposition(final String composition) {
    this.composition = composition;
  }

  /**
   * Adds the given line to the composition text.
   */
  public void addOpToComposition(final String compositionLine) {
    {// error checks
      Objects.requireNonNull(compositionLine);
      if (!compositionLine.endsWith(";")) {
        throw new RuntimeException();
      }
      if (new SequentialCompositionSerializer().readComposition(compositionLine) == null) {
        throw new RuntimeException(); // check if the given composition is parsable
      }
    }
    this.setComposition(this.getComposition() + compositionLine);
  }

  /**
   * @return the currentIndex
   */
  public final int getCurrentIndex() {
    return this.currentIndex;
  }

  /**
   * @param currentIndex
   *          the current index to set
   */
  public final void setCurrentIndex(final int currentIndex) {
    this.currentIndex = currentIndex;
  }

  /**
   * @return the maxIndex
   */
  public final int getMaxIndex() {
    return this.maxIndex;
  }

  /**
   * @param maxIndex
   *          the max index to set
   */
  public final void setMaxIndex(final int maxIndex) {
    this.maxIndex = maxIndex;
  }

  public final String getClientID() {
    return this.clientID;
  }

  public final void setClientID(final String clientID) {
    this.clientID = clientID;
  }

  /**
   * @return The unique identifier for this request.
   */
  public String getRequestID() {
    return this.requestID;
  }

  /**
   * @param requestID
   *          A unique identifier for this request.
   */
  public void setRequestID(final String requestID) {
    this.requestID = requestID;
  }

  public void addKeyworkArgument(final String name, final JASEDataObject data) {
    this.envState.addField(name, data);
  }

  public void addPositionalArgument(final JASEDataObject data) {
    this.envState.appendField(data);
  }

  public void addUnparsedKeywordArgument(final String name, final Object o) {
    this.addKeyworkArgument(name, this.otms.allToSemantic(o, false));
  }

  public void addUnparsedPositionalArgument(final Object o) {

    this.addPositionalArgument(this.otms.allToSemantic(o, false));
  }

  /**
   * Parses the contained composition to SequentialComposition.
   *
   * @return a SequentialComposition object.
   */
  public SequentialCompositionCollection parseSequentialComposition() {
    SequentialCompositionSerializer scs = new SequentialCompositionSerializer();
    // TODO workaround because bug in scs. remove the replacement after the bug is
    // fixed:
    String composition = this.getComposition();
    composition = composition.replaceAll("\\(\\{\\}\\)", "({,})"); // add comma to empty inputs
    // end of workaround
    SequentialComposition sc = scs.readComposition(composition);
    SequentialCompositionCollection scc = new SequentialCompositionCollection(sc);
    return scc;
  }

  /**
   * Returns the operation in position index from parsedCompositionField.
   *
   * @param index
   *          position of operation in composition.
   * @return the operation
   */
  public OperationInvocation getOperation(final int index) {
    return this.parseSequentialComposition().get(index);
  }

  /**
   * Returns true if the index is below currentindexField.
   */
  public boolean isBelowExecutionBound(final int index) {
    return index < this.getCurrentIndex();
  }

  /**
   * Returns true if the given index is above or equal to maxindexField. If maxindexField is set to
   * -1, it will be treated as infinity.
   */
  public boolean isAboveExecutionBound(final int index) {
    if (this.getMaxIndex() == -1) {
      return false; // always in bound if maxIndex is -1 (infinity).
    } else {
      return index >= this.getMaxIndex();
    }
  }

  /**
   * Returns the environment map
   */
  public EnvironmentState getState() {
    return this.envState;
  }

  /**
   * Writes this instance as a json body to the stream using the jackson library.
   *
   * @param outStream
   *          the output stream
   * @param otms
   *          The marshaling system used to parse the arguments objects to semantic objects
   * @throws IOException
   */
  private void writeBodyAsJson(final OutputStream outStream) throws IOException {
    JsonFactory jfactory = new JsonFactory();
    JsonGenerator jsonOut = jfactory.createGenerator(outStream, JsonEncoding.UTF8);
    jsonOut.writeStartObject(); // {
    // Write composition:
    if (this.containsComposition()) {
      jsonOut.writeStringField(HttpBody.CHOREOGRAPGY_FIELDNAME, this.getComposition());
    }
    // write client id
    jsonOut.writeStringField(HttpBody.CLIENTID_FIELDNAME, this.getClientID());
    // write request id
    jsonOut.writeStringField(HttpBody.REQUESTID_FIELDNAME, this.getRequestID());

    // Write current and max index:
    jsonOut.writeNumberField(HttpBody.CURRENTINDEX_FIELDNAME, this.getCurrentIndex());
    jsonOut.writeNumberField(HttpBody.MAXINDEX_FIELDNAME, this.getMaxIndex());
    // Write Arguments:
    jsonOut.writeFieldName(HttpBody.INPUTS_FIELDNAME);
    jsonOut.writeStartObject();

    // positional arguments
    jsonOut.writeFieldName(HttpBody.ARGLIST_FIELDNAME);
    jsonOut.writeStartArray();
    for (String fieldName : this.getState().positionalFieldNames()) {
      this.writeObject(jsonOut, this.getState().retrieveField(fieldName));
    }
    jsonOut.writeEndArray();

    // keyword arguments
    for (String keyword : this.getState().keywordFieldNames()) {
      JASEDataObject data = this.getState().retrieveField(keyword);
      jsonOut.writeFieldName(keyword);
      this.writeObject(jsonOut, data);
    }

    // end of arguments
    jsonOut.writeEndObject();
    // end of body
    jsonOut.writeEndObject(); // }
    jsonOut.flush();
  }

  private void writeObject(final JsonGenerator jsonOut, final JASEDataObject jdo) throws IOException {
    jsonOut.writeStartObject();
    jsonOut.writeStringField("type", jdo.getType());
    jsonOut.writeFieldName("data");
    if (this.otms.isPrimitive(jdo)) {
      // primitive types can be written as is.
      jsonOut.writeString(jdo.getData().toString());
    } else {
      // non-primitive types need to be transmitted using a streamhandler
      this.streamObject(jsonOut, jdo);
    }
    jsonOut.writeEndObject();
  }

  private void streamObject(final JsonGenerator jsonOut, final JASEDataObject jdo) throws IOException {
    String streamHandlerClassName = "de.upb.crc901.services.streamhandlers." + jdo.getType() + "StreamHandler";
    Class<?> streamHandlerClass;
    try {
      streamHandlerClass = Class.forName(streamHandlerClassName);
    } catch (ClassNotFoundException e) {
      throw new RuntimeException("Can't find streamhandler for semantic type " + jdo.getType());
    }
    StreamHandler<?> handler;
    try {
      handler = (StreamHandler<?>) streamHandlerClass.getConstructor().newInstance();
    } catch (InstantiationException | IllegalAccessException | IllegalArgumentException | InvocationTargetException | NoSuchMethodException | SecurityException e) {
      e.printStackTrace();
      throw new RuntimeException(e); // mask exception
    }

    Method write = MethodUtils.getMatchingAccessibleMethod(streamHandlerClass, "write", JsonGenerator.class, handler.getSupportedSemanticClass());
    assert write != null : "Could not find method \"write(" + JsonGenerator.class + ", " + handler.getSupportedSemanticClass() + ")\" in streamhandler class "
        + streamHandlerClassName;
    try {
      write.invoke(handler, jsonOut, jdo.getData());
    } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
      e.printStackTrace();
      throw new RuntimeException(e);
    }
  }

  /**
   * Encodes this instance and writes it chunk wise through the outStream.
   *
   * @throws IOException
   */
  public void writeBody(final OutputStream outStream) throws IOException {
    this.writeBodyAsJson(outStream);
  }

  public void readfromJsonBody(final InputStream input) throws IOException {
    JsonFactory jfactory = new JsonFactory();
    // jfactory.setCodec(new ObjectMapper());
    JsonParser jsonIn = jfactory.createParser(input);
    while (jsonIn.nextToken() != JsonToken.END_OBJECT) {
      if (jsonIn.currentToken() == null) {
        // end stream:
        break;
      }
      String fieldname = jsonIn.getCurrentName();
      if (HttpBody.CHOREOGRAPGY_FIELDNAME.equals(fieldname)) {
        jsonIn.nextToken();
        String composition = jsonIn.getValueAsString();
        // composition.replaceAll("\\\"", "\"");
        this.setComposition(composition);
      } else if (HttpBody.CURRENTINDEX_FIELDNAME.equals(fieldname)) {
        jsonIn.nextToken();
        this.setCurrentIndex(jsonIn.getIntValue());
      } else if (HttpBody.CLIENTID_FIELDNAME.equals(fieldname)) {
        jsonIn.nextToken();
        this.setClientID(jsonIn.getValueAsString());
      } else if (HttpBody.REQUESTID_FIELDNAME.equals(fieldname)) {
        jsonIn.nextToken();
        this.setRequestID(jsonIn.getValueAsString());
      } else if (HttpBody.MAXINDEX_FIELDNAME.equals(fieldname)) {
        jsonIn.nextToken();
        this.setMaxIndex(jsonIn.getIntValue());
      } else if (HttpBody.INPUTS_FIELDNAME.equals(fieldname)) {
        jsonIn.nextToken();
        // inputs are wrapped in a object:
        while (jsonIn.nextToken() != JsonToken.END_OBJECT) {
          fieldname = jsonIn.getCurrentName();
          if (HttpBody.ARGLIST_FIELDNAME.equals(fieldname)) {
            jsonIn.nextToken();
            while (jsonIn.nextToken() != JsonToken.END_ARRAY) {
              JASEDataObject jdo;
              if (jsonIn.currentToken() == JsonToken.VALUE_NULL) {
                jdo = null; // dont read null values in
              } else {
                jdo = this.readObject(jsonIn);
              }
              this.addPositionalArgument(jdo);
            }
          } else {
            String keywrod = fieldname;
            jsonIn.nextToken();
            if (jsonIn.currentToken() == JsonToken.VALUE_NULL) {
              continue; // dont read null values in
            }
            JASEDataObject jdo = this.readObject(jsonIn);
            this.addKeyworkArgument(keywrod, jdo);
          }
        }
      }

    }
  }

  private JASEDataObject readObject(final JsonParser jsonIn) throws IOException {
    String type = null;
    Object data = null;
    while (jsonIn.nextToken() != JsonToken.END_OBJECT) {
      String fieldname = jsonIn.getCurrentName();
      if ("type".equals(fieldname)) {
        jsonIn.nextToken();
        type = jsonIn.getText();
      }
      if ("data".equals(fieldname)) {
        if (type != null) {
          jsonIn.nextToken();
          data = this.parseData(jsonIn, type);
        } else {
          throw new RuntimeException("The incoming json string doesn't specify type before the data.");
        }
      }
    }
    JASEDataObject jdo = new JASEDataObject(type, data);
    return jdo;
  }

  private Object parseData(final JsonParser jsonIn, final String type) throws IOException {
    if (this.otms.isPrimitiveType(type)) {
      return this.otms.primitiveToSemanticAsString(jsonIn.getValueAsString()).getData();
    } else {
      try {
        String streamHandlerClassName = "de.upb.crc901.services.streamhandlers." + type + "StreamHandler";
        Class<?> streamHandlerClass = Class.forName(streamHandlerClassName);
        StreamHandler<?> handler = (StreamHandler<?>) streamHandlerClass.getConstructor().newInstance();

        Method read = MethodUtils.getMatchingAccessibleMethod(streamHandlerClass, "read", JsonParser.class);
        assert read != null : "Could not find method \"write(" + JsonParser.class + ")\" in streamhandler class " + streamHandlerClassName;

        return read.invoke(handler, jsonIn);
      } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
        e.printStackTrace();
      } catch (ClassNotFoundException e) {
        e.printStackTrace();
      } catch (InstantiationException | NoSuchMethodException | SecurityException e) {
        e.printStackTrace();
      }
      return null;
    }
  }

  public void readfromBody(final InputStream input) throws IOException {
    this.readfromJsonBody(input);
  }

  @Override
  public boolean equals(final Object object) {
    if (object instanceof HttpBody) {
      return this.equals((HttpBody) object);
    }
    return false;
  }

  public boolean equals(final HttpBody otherBody) {
    if (!this.getComposition().equals(otherBody.getComposition())) {
      return false;
    }
    if (this.getCurrentIndex() != otherBody.getCurrentIndex()) {
      return false;
    }
    if (this.getMaxIndex() != otherBody.getMaxIndex()) {
      return false;
    }
    if (!this.getState().equals(otherBody.getState())) {
      return false;
    }
    return true;
  }

}
