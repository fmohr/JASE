package de.upb.crc901.services.typeserializers;

import de.upb.crc901.services.core.IOntologySerializer;
import de.upb.crc901.services.core.JASEDataObject;

import jaicore.ml.WekaUtil;
import jaicore.ml.interfaces.LabeledInstance;
import jaicore.ml.interfaces.LabeledInstances;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;

import weka.core.Instance;
import weka.core.Instances;
import weka.filters.Filter;

public class InstancesOntologySerializer implements IOntologySerializer<Instances> {

  private static final List<String> supportedTypes = Arrays.asList(new String[] { "Instances", "LabeledInstances" });

  @Override
  public Instances unserialize(final JASEDataObject jdo) {
    // TimeLogger.STOP_TIME("labeledinstances -> wekainstance started");
    if (jdo.getData() instanceof jaicore.ml.interfaces.Instances) {
      // TimeLogger.STOP_TIME("unserialize done");
      return WekaUtil.fromJAICoreInstances((jaicore.ml.interfaces.Instances) jdo.getData());
    } else if (jdo.getData() instanceof LabeledInstances<?>) {
      return WekaUtil.fromJAICoreInstances((LabeledInstances<String>) jdo.getData());
    } else {
      throw this.typeMismatch(jdo);
    }
  }

  @Override
  public JASEDataObject serialize(Instances wekaInstances) {
    // TimeLogger.STOP_TIME("wekainstance -> labeledinstances with size: " + wekaInstances.size() + "
    // started");
    if (wekaInstances.classIndex() < 0) {
      return new JASEDataObject("Instances", WekaUtil.toJAICoreInstances(wekaInstances));
    } else {
      if (WekaUtil.needsBinarization(wekaInstances, true)) {
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

  class ShallowLabeledInstances implements LabeledInstances<String> {
    private final Instances wekaInstances;
    private final InstanceOntologySerializer instanceSerialiser = new InstanceOntologySerializer();

    public ShallowLabeledInstances(final Instances wekaInstances) {
      this.wekaInstances = wekaInstances;
    }

    @Override
    public int size() {
      return this.wekaInstances.size();
    }

    @Override
    public boolean isEmpty() {
      return this.wekaInstances.isEmpty();
    }

    @Override
    public boolean contains(final Object o) {
      return this.wekaInstances.contains(o);
    }

    @Override
    public Iterator<LabeledInstance<String>> iterator() {

      final Iterator<Instance> wekaIterator = this.wekaInstances.iterator();

      return new Iterator<LabeledInstance<String>>() {
        @Override
        public boolean hasNext() {
          return wekaIterator.hasNext();
        }

        @SuppressWarnings("unchecked")
        @Override
        public LabeledInstance<String> next() {
          return (LabeledInstance<String>) ShallowLabeledInstances.this.instanceSerialiser.serialize(wekaIterator.next()).getData();
        }

      };
    }

    @Override
    public Object[] toArray() {
      return this.wekaInstances.toArray();
    }

    @Override
    public <T> T[] toArray(final T[] a) {
      // TODO Auto-generated method stub
      return null;
    }

    @Override
    public boolean add(final LabeledInstance<String> e) {
      // TODO Auto-generated method stub
      return false;
    }

    @Override
    public boolean remove(final Object o) {
      // TODO Auto-generated method stub
      return false;
    }

    @Override
    public boolean containsAll(final Collection<?> c) {
      // TODO Auto-generated method stub
      return false;
    }

    @Override
    public boolean addAll(final Collection<? extends LabeledInstance<String>> c) {
      // TODO Auto-generated method stub
      return false;
    }

    @Override
    public boolean addAll(final int index, final Collection<? extends LabeledInstance<String>> c) {
      // TODO Auto-generated method stub
      return false;
    }

    @Override
    public boolean removeAll(final Collection<?> c) {
      // TODO Auto-generated method stub
      return false;
    }

    @Override
    public boolean retainAll(final Collection<?> c) {
      // TODO Auto-generated method stub
      return false;
    }

    @Override
    public void clear() {
      // TODO Auto-generated method stub

    }

    @Override
    public LabeledInstance<String> get(final int index) {
      return (LabeledInstance<String>) this.instanceSerialiser.serialize(this.wekaInstances.get(index)).getData();
    }

    @Override
    public LabeledInstance<String> set(final int index, final LabeledInstance<String> element) {
      // TODO Auto-generated method stub
      return null;
    }

    @Override
    public void add(final int index, final LabeledInstance<String> element) {
      // TODO Auto-generated method stub

    }

    @Override
    public LabeledInstance<String> remove(final int index) {
      // TODO Auto-generated method stub
      return null;
    }

    @Override
    public int indexOf(final Object o) {
      // TODO Auto-generated method stub
      return 0;
    }

    @Override
    public int lastIndexOf(final Object o) {
      // TODO Auto-generated method stub
      return 0;
    }

    @Override
    public ListIterator<LabeledInstance<String>> listIterator() {
      // TODO Auto-generated method stub
      return null;
    }

    @Override
    public ListIterator<LabeledInstance<String>> listIterator(final int index) {
      // TODO Auto-generated method stub
      return null;
    }

    @Override
    public List<LabeledInstance<String>> subList(final int fromIndex, final int toIndex) {
      // TODO Auto-generated method stub
      return null;
    }

    @Override
    public int getNumberOfRows() {
      return this.wekaInstances.size();
    }

    @Override
    public int getNumberOfColumns() {
      return this.wekaInstances.numAttributes() - 1;
    }

    @Override
    public String toJson() {
      // TODO Auto-generated method stub
      return null;
    }

    @Override
    public void addAllFromJson(final String jsonString) throws IOException {
      // TODO Auto-generated method stub

    }

    @Override
    public void addAllFromJson(final File jsonFile) throws IOException {
      // TODO Auto-generated method stub

    }

    @Override
    public ArrayList<String> getOccurringLabels() {
      // TODO Auto-generated method stub
      return null;
    }

  }
}
