/**
 * ExampleTester.java
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
package de.upb.crc901.services;

import Catalano.Imaging.FastBitmap;

import de.upb.crc901.configurationsetting.operation.SequentialComposition;
import de.upb.crc901.configurationsetting.serialization.SequentialCompositionSerializer;
import de.upb.crc901.services.core.HttpServiceClient;
import de.upb.crc901.services.core.HttpServiceServer;
import de.upb.crc901.services.core.OntologicalTypeMarshallingSystem;
import de.upb.crc901.services.core.ServiceCompositionResult;
import de.upb.crc901.services.core.ServiceHandle;

import jaicore.basic.FileUtil;
import jaicore.basic.MathExt;
import jaicore.ml.WekaUtil;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.List;
import java.util.Random;

import javax.swing.JOptionPane;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import weka.classifiers.trees.RandomTree;
import weka.core.Instance;
import weka.core.Instances;

public class ExampleTester {

  private final static int PORT = 8000;

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
    this.composition = this.sqs.readComposition(FileUtil.readFileAsList("testrsc/composition.txt"));

    this.client = new HttpServiceClient(this.otms);
  }

  @Test
  public void testClassifier() throws Exception {

    /* read instances */
    Instances wekaInstances = new Instances(
        new BufferedReader(new FileReader("../CrcTaskBasedConfigurator/testrsc" + File.separator + "polychotomous" + File.separator + "audiology.arff")));
    wekaInstances.setClassIndex(wekaInstances.numAttributes() - 1);
    List<Instances> split = WekaUtil.getStratifiedSplit(wekaInstances, new Random(0), .9f);

    /* create and train classifier service */
    String className = RandomTree.class.getName();

    ServiceHandle service = ((ServiceHandle) this.client.callServiceOperation("localhost:" + PORT + "/" + className + "::__construct").get("out").getData());
    String serviceAddress = service.getServiceAddress();
    this.client.callServiceOperation(serviceAddress + "::train", split.get(0));

    /* eval instances on service */
    int mistakes = 0;
    for (Instance i : split.get(1)) {
      ServiceCompositionResult resource = this.client.callServiceOperation(serviceAddress + "::classifyInstance", i);

      float prediction = (float) (resource.get("out").getData());
      if (prediction != i.classValue()) {
        mistakes++;
      }
    }

    ServiceCompositionResult result = this.client.callServiceOperation(serviceAddress + "::predict", split.get(1));
    List<String> predictions = (List<String>) result.get("out").getData();

    for (String predictedLabel : predictions) {
      int index = wekaInstances.classAttribute().indexOfValue(predictedLabel);
      Assert.assertNotSame(-1, index);
    }

    result = this.client.callServiceOperation(serviceAddress + "::predict_and_score", split.get(1));
    Double score = (Double) result.get("out").getData();

    /* report score */
    System.out.println(mistakes + "/" + split.get(1).size());
    double clientside_score = MathExt.round(1 - mistakes * 1f / split.get(1).size(), 2);
    score = MathExt.round(score, 2);

    System.out.println("Accuracy calculated by client: " + clientside_score);
    System.out.println("Accuracy by predict_and_score method: " + score);

    Assert.assertEquals(clientside_score, score, 0.01);

  }

  // @Test
  public void testImageProcessor() throws Exception {

    /* create new classifier */
    System.out.println("Now running the following composition: ");
    System.out.println(this.sqs.serializeComposition(this.composition));
    File imageFile = new File("testrsc/FelixMohr.jpg");
    FastBitmap fb = new FastBitmap(imageFile.getAbsolutePath());
    JOptionPane.showMessageDialog(null, fb.toIcon(), "Result", JOptionPane.PLAIN_MESSAGE);
    ServiceCompositionResult resource = this.client.invokeServiceComposition(this.composition, fb);
    FastBitmap result = this.otms.objectFromSemantic(resource.get("fb3"), FastBitmap.class);
    JOptionPane.showMessageDialog(null, result.toIcon(), "Result", JOptionPane.PLAIN_MESSAGE);
  }

  // @Test
  public void testSequentialCompositionSerializer() throws Exception {
    SequentialCompositionSerializer scs = new SequentialCompositionSerializer();
    SequentialComposition sc = scs.readComposition("a = foo::bar({})");
  }

  @After
  public void shutdown() {
    System.out.println("Shutting down ...");
    this.server.shutdown();
  }
}
