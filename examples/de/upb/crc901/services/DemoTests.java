package de.upb.crc901.services;

import static org.junit.Assert.*;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import de.upb.crc901.services.core.EasyClient;
import de.upb.crc901.services.core.HttpBody;
import de.upb.crc901.services.core.HttpServiceClient;
import de.upb.crc901.services.core.HttpServiceServer;
import de.upb.crc901.services.core.OntologicalTypeMarshallingSystem;
import de.upb.crc901.services.core.ServiceHandle;
import junit.framework.Assert;
import weka.core.Instances;

public class DemoTests {

	



	  private static Instances wekaInstances;

	  @BeforeClass
	  public static void setUpBeforeClass() throws Exception {
	    /* start server */

	    wekaInstances = new Instances(new BufferedReader(new FileReader("../CrcTaskBasedConfigurator/testrsc" 
	    + File.separator + "mnist" +
	    		File.separator + "test.arff")));
	    

	    wekaInstances.setClassIndex(wekaInstances.numAttributes() - 1);

	  }


	
	
	@Test
	public void testGZipStream() throws Exception {
		HttpBody body = new HttpBody();
		body.addUnparsedKeywordArgument("hallo", wekaInstances);
		ByteArrayOutputStream outStream = new ByteArrayOutputStream();
		body.writeBody(outStream);
		System.out.println("Writing body done");
//		String initString = outStream.toString();
//		System.out.println(initString);
		byte[] initData = outStream.toByteArray();
		System.out.println("Initial Length: " + initData.length);
		
		ByteArrayOutputStream compressedOutStream = new ByteArrayOutputStream();
		GZIPOutputStream zipOut = new GZIPOutputStream(compressedOutStream);
		body.writeBody(zipOut);
		
		zipOut.flush();
		zipOut.close();
		
		
		byte[] compressedData = compressedOutStream.toByteArray();
		
		System.out.println("Compressed Length: " + compressedData.length);
		
		ByteArrayInputStream bais = new ByteArrayInputStream(compressedData);
		GZIPInputStream gzis = new GZIPInputStream(bais);
		InputStreamReader reader = new InputStreamReader(gzis);
		BufferedReader in = new BufferedReader(reader);
		in.readLine();
//		String readed;
//		while ((readed = in.readLine()) != null) {
//		    System.out.println(readed);
//		}
//		org.junit.Assert.assertEquals(initString, readed);
	}

}
