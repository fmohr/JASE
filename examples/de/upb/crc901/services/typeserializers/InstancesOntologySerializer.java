package de.upb.crc901.services.typeserializers;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;

import org.apache.commons.collections4.ListUtils;
import org.apache.commons.lang3.NotImplementedException;

import com.fasterxml.jackson.databind.ObjectMapper;

import de.upb.crc901.services.ExchangeTest;
import de.upb.crc901.services.core.IOntologySerializer;
import de.upb.crc901.services.core.JASEDataObject;
import de.upb.crc901.services.core.TimeLogger;
import jaicore.ml.WekaUtil;
import jaicore.ml.core.SimpleInstancesImpl;
import jaicore.ml.core.SimpleLabeledInstanceImpl;
import jaicore.ml.core.SimpleLabeledInstancesImpl;
import jaicore.ml.core.WekaCompatibleInstancesImpl;
import jaicore.ml.interfaces.LabeledInstance;
import jaicore.ml.interfaces.LabeledInstances;
import weka.core.Attribute;
import weka.core.DenseInstance;
import weka.core.Instance;
import weka.core.Instances;
import weka.filters.Filter;
import weka.filters.unsupervised.attribute.NominalToBinary;

public class InstancesOntologySerializer implements IOntologySerializer<Instances>  {
	
	private static final List<String> supportedTypes = Arrays.asList(new String[] {"Instances", "LabeledInstances"});
	
	public  Instances unserialize(final JASEDataObject jdo) {
//		TimeLogger.STOP_TIME("labeledinstances -> wekainstance started");
		if(jdo.getData() instanceof jaicore.ml.interfaces.Instances) {
//			TimeLogger.STOP_TIME("unserialize done");
			return WekaUtil.fromJAICoreInstances((jaicore.ml.interfaces.Instances)jdo.getData());
		} else if(jdo.getData() instanceof LabeledInstances<?>) {
			return WekaUtil.fromJAICoreInstances((LabeledInstances<String>)jdo.getData());
		}
		else {
			throw typeMismatch(jdo);
		}
	}

	public JASEDataObject serialize(Instances wekaInstances) {
//		TimeLogger.STOP_TIME("wekainstance -> labeledinstances with size: " + wekaInstances.size() + " started");
		if (wekaInstances.classIndex() < 0) {
			return new JASEDataObject("Instances", WekaUtil.toJAICoreInstances(wekaInstances));
		}
		else{
			if(WekaUtil.needsBinarization(wekaInstances, true)) {
				weka.filters.unsupervised.attribute.NominalToBinary toBinFilter = new weka.filters.unsupervised.attribute.NominalToBinary();
				try {
					toBinFilter.setInputFormat(wekaInstances);
					wekaInstances = Filter.useFilter(wekaInstances, toBinFilter);
				} catch (Exception e) {
					e.printStackTrace();
					throw new RuntimeException(e);
				}
			}
			return new JASEDataObject("LabeledInstances", new ShallowLabeledInstances(wekaInstances));
		}
	}
	
	

	@Override
	public Collection<String> getSupportedSemanticTypes() {
		return supportedTypes;
	}
	
	
	
	class ShallowLabeledInstances implements LabeledInstances<String>{
		private final Instances wekaInstances;
		private final InstanceOntologySerializer instanceSerialiser = new InstanceOntologySerializer();
		
		public ShallowLabeledInstances(Instances wekaInstances) {
			this.wekaInstances = wekaInstances;
		}

		@Override
		public int size() {
			return wekaInstances.size();
		}

		@Override
		public boolean isEmpty() {
			return wekaInstances.isEmpty();
		}

		@Override
		public boolean contains(Object o) {
			return wekaInstances.contains(o);
		}

		@Override
		public Iterator<LabeledInstance<String>> iterator() {

			final Iterator<Instance> wekaIterator = wekaInstances.iterator();
			
			return new Iterator<LabeledInstance<String>>(){
				@Override
				public boolean hasNext() {
					return wekaIterator.hasNext();
				}
				@SuppressWarnings("unchecked")
				@Override
				public LabeledInstance<String> next() {
					return (LabeledInstance<String>) instanceSerialiser.serialize(wekaIterator.next()).getData();
				}
				
			};
		}

		@Override
		public Object[] toArray() {
			return wekaInstances.toArray();
		}

		@Override
		public <T> T[] toArray(T[] a) {
			// TODO Auto-generated method stub
			return null;
		}

		@Override
		public boolean add(LabeledInstance<String> e) {
			// TODO Auto-generated method stub
			return false;
		}

		@Override
		public boolean remove(Object o) {
			// TODO Auto-generated method stub
			return false;
		}

		@Override
		public boolean containsAll(Collection<?> c) {
			// TODO Auto-generated method stub
			return false;
		}

		@Override
		public boolean addAll(Collection<? extends LabeledInstance<String>> c) {
			// TODO Auto-generated method stub
			return false;
		}

		@Override
		public boolean addAll(int index,
				Collection<? extends LabeledInstance<String>> c) {
			// TODO Auto-generated method stub
			return false;
		}

		@Override
		public boolean removeAll(Collection<?> c) {
			// TODO Auto-generated method stub
			return false;
		}

		@Override
		public boolean retainAll(Collection<?> c) {
			// TODO Auto-generated method stub
			return false;
		}

		@Override
		public void clear() {
			// TODO Auto-generated method stub
			
		}

		@Override
		public LabeledInstance<String> get(int index) {
			return (LabeledInstance<String>) instanceSerialiser.serialize(wekaInstances.get(index)).getData();
		}

		@Override
		public LabeledInstance<String> set(int index,
				LabeledInstance<String> element) {
			// TODO Auto-generated method stub
			return null;
		}

		@Override
		public void add(int index, LabeledInstance<String> element) {
			// TODO Auto-generated method stub
			
		}

		@Override
		public LabeledInstance<String> remove(int index) {
			// TODO Auto-generated method stub
			return null;
		}

		@Override
		public int indexOf(Object o) {
			// TODO Auto-generated method stub
			return 0;
		}

		@Override
		public int lastIndexOf(Object o) {
			// TODO Auto-generated method stub
			return 0;
		}

		@Override
		public ListIterator<LabeledInstance<String>> listIterator() {
			// TODO Auto-generated method stub
			return null;
		}

		@Override
		public ListIterator<LabeledInstance<String>> listIterator(int index) {
			// TODO Auto-generated method stub
			return null;
		}

		@Override
		public List<LabeledInstance<String>> subList(int fromIndex,
				int toIndex) {
			// TODO Auto-generated method stub
			return null;
		}

		@Override
		public int getNumberOfRows() {
			return wekaInstances.size();
		}

		@Override
		public int getNumberOfColumns() {
			return wekaInstances.numAttributes();
		}

		@Override
		public String toJson() {
			// TODO Auto-generated method stub
			return null;
		}

		@Override
		public void addAllFromJson(String jsonString) throws IOException {
			// TODO Auto-generated method stub
			
		}

		@Override
		public void addAllFromJson(File jsonFile) throws IOException {
			// TODO Auto-generated method stub
			
		}

		@Override
		public ArrayList<String> getOccurringLabels() {
			// TODO Auto-generated method stub
			return null;
		}
		
	}
}
