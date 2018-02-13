package de.upb.crc901.services.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.upb.crc901.services.ExchangeTest;

public class TimeLogger {
	private static final Logger log = LoggerFactory.getLogger(TimeLogger.class);
	
	

	private static long timestart = System.currentTimeMillis();
	
	public static void STOP_TIME(String label) {
		long difference = System.currentTimeMillis() - TimeLogger.timestart;
//		log.debug(label + ": " + difference + " ms");
		System.out.println(label + ": " + difference + " ms");
		TimeLogger.timestart = System.currentTimeMillis();
	}
	
}
