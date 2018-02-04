package de.upb.crc901.services.typeserializers;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import Catalano.Imaging.FastBitmap;
import de.upb.crc901.services.CatalanoWrapper;
import de.upb.crc901.services.core.IOntologySerializer;
import de.upb.crc901.services.core.JASEDataObject;
import jaicore.ml.core.SimpleInstanceImpl;

public class FastBitmapOntologySerializer implements IOntologySerializer<FastBitmap> {
	
	private static final List<String> supportedTypes = Arrays.asList(new String[] {"Instance"});
	
	public FastBitmap unserialize(final JASEDataObject jdo) {
		return new FastBitmap(CatalanoWrapper.instance2FastBitmap(new SimpleInstanceImpl(jdo.getObject())));
	}

	public JASEDataObject serialize(final FastBitmap fb) {
		try {
			JsonNode object = (new ObjectMapper().readTree(CatalanoWrapper.fastBitmap2Instance(fb).toJson()));
			String type = "Instance";
			JASEDataObject jdo = new JASEDataObject(type, object);
			return jdo;
		} catch (IOException e) {
			e.printStackTrace();
		}
		return null;
	}

	@Override
	public Collection<String> getSupportedSemanticTypes() {
		return supportedTypes;
	}
}
