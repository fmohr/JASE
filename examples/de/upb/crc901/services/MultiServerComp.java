package de.upb.crc901.services;

import Catalano.Imaging.FastBitmap;

import de.upb.crc901.configurationsetting.operation.SequentialComposition;
import de.upb.crc901.configurationsetting.serialization.SequentialCompositionSerializer;
import de.upb.crc901.services.core.HttpServiceClient;
import de.upb.crc901.services.core.HttpServiceServer;
import de.upb.crc901.services.core.OntologicalTypeMarshallingSystem;
import de.upb.crc901.services.core.ServiceCompositionResult;

import jaicore.basic.FileUtil;

import java.io.File;
import java.io.IOException;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class MultiServerComp {
  private final static int JASEPORT = 8000;

  private HttpServiceServer server;
  private SequentialComposition composition;
  private SequentialCompositionSerializer sqs;
  private HttpServiceClient client;
  private final OntologicalTypeMarshallingSystem otms = new OntologicalTypeMarshallingSystem();

  @Before
  public void init() throws Exception {

    /* start server */
    this.server = HttpServiceServer.TEST_SERVER();

    /* read in composition */
    this.sqs = new SequentialCompositionSerializer();

    this.client = new HttpServiceClient(this.otms);
  }

  @After
  public void shutdown() {
    System.out.println("Shutting down ...");
    this.server.shutdown();
  }

  @Test
  public void multi1() throws IOException {
    this.composition = this.sqs.readComposition(FileUtil.readFileAsList("testrsc/multicomposition1.txt"));
    System.out.println(this.sqs.serializeComposition(this.composition));
    File imageFile = new File("testrsc/2k.jpg");
    FastBitmap fb = new FastBitmap(imageFile.getAbsolutePath());
    ServiceCompositionResult resource = this.client.invokeServiceComposition(this.composition, fb);
    FastBitmap result = this.otms.jsonToObject(resource.get("fb3"), FastBitmap.class);
    // JOptionPane.showMessageDialog(null, fb.toIcon(), "Before", JOptionPane.PLAIN_MESSAGE);
    // JOptionPane.showMessageDialog(null, result.toIcon(), "Result", JOptionPane.PLAIN_MESSAGE);

  }

  @Test
  public void multiArff() throws IOException {

  }
}
