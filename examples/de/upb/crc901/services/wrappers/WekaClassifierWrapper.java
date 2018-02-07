package de.upb.crc901.services.wrappers;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import de.upb.crc901.services.core.ServiceWrapper;
import jaicore.basic.MathExt;
import jaicore.ml.core.SimpleInstancesImpl;
import jaicore.ml.core.SimpleLabeledInstancesImpl;
import jaicore.ml.interfaces.Instance;
import jaicore.ml.interfaces.LabeledInstance;
import jaicore.ml.interfaces.LabeledInstances;
import weka.classifiers.Classifier;
import weka.core.Attribute;
import weka.core.DenseInstance;
import weka.core.Instances;
import weka.core.Utils;

/**
 * Wraps the weka.classifiers.Classifier. 
 * @author aminfaez
 *
 */
public class WekaClassifierWrapper extends ServiceWrapper{

	// Simply use the base constructor if the 'constructor' of the wrapped service shall not change.
	public WekaClassifierWrapper(Constructor<? extends Object> delegateConstructor, Object[] values) {
		super(delegateConstructor, values);
		
		try {
			freshClassifier = weka.classifiers.AbstractClassifier.makeCopy(((Classifier) super.delegate));
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}
	
	/** A fresh copy of super.delegate right after it is created. This is used to reset the classifier in declareClasses function. */
	private final Classifier freshClassifier;

	/** Flag that indicated weather or not declare_classes was invoked. */
	private boolean declaredClasses = false;
	
	/**
	 * 	Increment this version-ID if the class changes in a way that it becomes incompatible with serializations of the previous version. 
	 */
	private static final long serialVersionUID = 1L;
	
	/** When false, this flag indicates that this classifier hasn't been trained before thus the attributeList is unassigned. 
	 * If this flag is set to true, incoming training data will be checked to have the same column size as the first column size. */
	private boolean attributesAssignedFlag = false;
	
	/** A list of Attribute which is only assigned once in collectAttributes.*/
	private ArrayList<Attribute> attributeList;
	
	/** Use TreeSet to get O(log(n)) time for add and contains. */
	private Set<String> classLabelSet = new TreeSet<String>(); 
	

	/**
	 * If not attributesAssignedFlag, this method fills the attributeList based on the amount of attributes. 
	 * Else it will throw an Exception indicating that the data doesn't match with data received before.
	 * @param attributeCount amount of data in each instance
	 */
	private void checkAttributes(int attributeCount) {
		if(!attributesAssignedFlag) {
			/* create basic attribute entries */
		   this.attributeList = new ArrayList<>(attributeCount);
		    for (int i = 1; i <= attributeCount; i++) {
		      this.attributeList.add(new Attribute("a" + i));
		    }
		    // set a dummy attribute as the last item in the list. this will be set again in the create 
		    this.attributeList.add(new Attribute("label"));
		    // flag is now true forever.
		    attributesAssignedFlag = true;
		}
		else {
			/* Check if the data size matches the previous invocations. */
			if(attributeCount != attributeList.size() - 1) { // -1 because attributeList contains a label field.
				throw new RuntimeException("Data column size (=" 
										+ attributeCount + ") doesn't match previous data column size(=" 
										+ (attributeList.size() - 1) + ").");
			}
		} 	
	}
	
	/**
	 * This method receives a LabeledInstances object and expands the classLabelSet and the classAttribute with unknown labels, which are found within labeledInstances.
	 * 
	 * !!! If a unknown label is found the delegate method will be reset. !!!
	 * 
	 * This function can also be invoked before the train starts set class labels.
	 * 
	 * @param labeledInstances List of data mapped onto labels. This function only cares for the labels so the column size may be 0.
	 */
	public void declare_classes(SimpleLabeledInstancesImpl labeledInstances) {
		/* extendedClasses indicates if the labeledInstances contains a label 
		 * which wasn't specified in previous declareClasses invocations.
		 * If true the wekaInstaces object will be extended so is the inner classifier.
		 */
		boolean extendedClasses = false; 
		for(LabeledInstance<String> labeledInstance : labeledInstances) {
			if(!classLabelSet.contains(labeledInstance.getLabel())){
				/* A class that wasn't defined before.*/
				String label = labeledInstance.getLabel();
				// Add it to the tree to mark it known as of now.
				classLabelSet.add(label);
				extendedClasses = true;
			}
		}
		// build classes map and list. 
		ArrayList<String> classes_List = new ArrayList<String>(); // this list is used to create the label Attribute 
		// Map<String, Double> classToIndex_Map = new HashedMap<>(); // this map is used when creating weka.core.Instance objects from LabeledInstance by looking up which label corresponds to which index.
		//int index = 0;
		for(String class_ : this.classLabelSet) {
			classes_List.add(class_);
			//classToIndex_Map.put(class_, (double) (index++));
		}
		// create label attribute and add it to a new list of attributes.
		Attribute classAttribute = new Attribute("label", classes_List);
	    this.attributeList.set(attributeList.size()-1, classAttribute); // reset the label attribute with the new filles one
	    
	    if(extendedClasses) {
	    		// Reset the classifier because new labels are introduced.
	    		try {
				super.delegate = weka.classifiers.AbstractClassifier.makeCopy(freshClassifier);
			} catch (Exception e) {
				throw new RuntimeException(e);
			}
	    		
	    }
	    declaredClasses = true;
	}
	
	
	/**
	 * Train method for the classfier based on WekaClassifier. 
	 * Uses buildClassifier method on the delegate object to 'train' it.
	 * @param labeledInstances training data
	 */
	public void train(SimpleLabeledInstancesImpl trainingData) {
		if(trainingData.getNumberOfRows() < 1) { // no data. do nothing.
			return; // :(
		}
		// does the data match in column size?
		checkAttributes(trainingData.getNumberOfColumns());
		// store the classes defined in the given data if it didn't happen before.
		if(!declaredClasses) {
			declare_classes(trainingData);
		}
		
		// Now create a weka.core.Instances object and fill our data to it.
		Instances trainingInstances = createWekaInstances(trainingData);
		
		try {
			((Classifier)super.delegate).buildClassifier(trainingInstances);
		} catch (Exception e) {
			// Mask this excpetion
			throw new RuntimeException(e);
		}
	}
	
	/**
	 * Contains logic to do prediction based on an iterator of Instance. 
	 * 
	 * @param data Iterable of subtype of Instance. Each Instance is converted to a Weka Instance whose it's class is set to be missing.
	 * @param rows indicates how many times data can iterate. Used to preallocate the predictions list.
	 * @param columns indicates how many numerical entries each Instance from iterable has.
	 * @return List of predictions made by the inner classifier referenced by super.delegate. 
	 */
	private List<String> predictInstanceIterable(Iterable<? extends Instance> data, int rows, int columns) {
		List<Double> predicitons = new ArrayList<>(rows);
		Classifier innerClassfier = (Classifier) super.delegate;
		// translate to weka instances
		weka.core.Instances wekaInstances = createWekaInstances(data, rows, columns);
		// iterate over weka instances and feed it to the train algorithm.
		for(weka.core.Instance wekaInstance : wekaInstances) {
			try {
				// try to do a prediction:
				Double prediction = innerClassfier.classifyInstance(wekaInstance);
				predicitons.add(prediction);
			}catch(Exception ex) {
				// Else put a missing value if the prediciton failed.
				predicitons.add(Utils.missingValue());
			}
		}
		List<String> labeledPredictions = new ArrayList<>(rows);
		Attribute classAttribute = attributeList.get(attributeList.size()-1);
		for(double predictionIndex : predicitons) {
			int index = (int) predictionIndex;
			String label = classAttribute.value(index);
			labeledPredictions.add(label);
		}
		return labeledPredictions;
	}
	
	/**
	 * Takes Instances and returns a list of predictions made by the inner classifier.
	 */
	public List<String> predict(SimpleInstancesImpl instances) {
		return predictInstanceIterable(
				instances, 
				instances.getNumberOfRows(), 
				instances.getNumberOfColumns());
	}
	

	/**
	 * Takes LabeledInstances and predicts ignoring the labels. 
	 */
	public List<String> predict(SimpleLabeledInstancesImpl labeledinstances)  {
		return predictInstanceIterable(
				labeledinstances, 
				labeledinstances.getNumberOfRows(), 
				labeledinstances.getNumberOfColumns());
	}
	
	/**
	 * First predicts the input objects using the predict method.
	 *  Then calculated the accuracy of the model and return it.
	 *  
	 *  normalize: If ``False``, return the number of correctly classified samples.
        Otherwise, return the fraction of correctly classified samples.
	 */
	public double predict_and_score(SimpleLabeledInstancesImpl labeledinstances)  {
		List<String> predictions = predict(labeledinstances);
		boolean normalize = true;
		int index = 0;
		int score = 0;
		
		// compare predictions result with labels in the given set.
		for(String predictedLabel : predictions) {
			boolean matched = labeledinstances.get(index).getLabel().equals(predictedLabel);
			if(matched) {
				score++;
			}
			index++;
		}
		if(normalize) {
			double normalizedScore = ((double)score) / ((double)labeledinstances.size());
			double roundedNormalizedScore =  MathExt.round(normalizedScore, 2);
			return roundedNormalizedScore;
		}
		else {
			return score;
		}
	}
	
	
	/* -----------------
	 * UTILITY FUNCTIONS
	 * -----------------*/

	/**
	 * Creates a weka.core.Instances object containing the given training data ready to be used to train the delegate using buildClassifier from weka's Classifier class.
	 */
	private weka.core.Instances createWekaInstances(LabeledInstances<String> data) {
		int attributeCount = data.getNumberOfColumns() + 1; // the amount of attributes including the class label.
		weka.core.Instances wekaInstances = new Instances("JAICore-extracted dataset", this.attributeList, data.getNumberOfRows());
		wekaInstances.setClassIndex(attributeCount - 1); // the last item is the class attribute.
		Attribute classAttribute = this.attributeList.get(attributeCount-1); // the attribute containing all the classes.
		
		// take every labeled instance and put it into the wekaInstance.
		for (jaicore.ml.interfaces.LabeledInstance<String> labeledInstance : data) {
		  weka.core.Instance wekaInstance = new DenseInstance(attributeCount);
	      wekaInstance.setDataset(wekaInstances);
	      int att = 0;
	      for (Double val : labeledInstance) {
	        wekaInstance.setValue(att++, val);
	      }
	      // classValue in a weka.core.Instance is the index of the class value.
	      Double classIndex = new Double(classAttribute.indexOfValue(labeledInstance.getLabel()));
	      wekaInstance.setClassValue(classIndex);
	      wekaInstances.add(wekaInstance);
	    }
		return wekaInstances;
	}
	
	/**
	 * From the Instances object given this function creates a weka.core.Instances object ready to be used by classifyInstance function from Classifier. 
	 * The class value is set to missing.
	 */
	private weka.core.Instances createWekaInstances(Iterable<? extends Instance> data, int rows, int columns) {
		int attributeCount = columns + 1; // the amount of attributes including the class label.
		weka.core.Instances wekaInstances = new Instances("JAICore-extracted dataset", this.attributeList, rows);
		wekaInstances.setClassIndex(attributeCount - 1); // the last item is the class attribute.
		for(Instance instance : data) {
		    weka.core.Instance wekaInstance = new DenseInstance(attributeCount);
		    wekaInstance.setDataset(wekaInstances);
		    int att = 0;
		    for (Double val : instance) {
		      wekaInstance.setValue(att++, val);
		    }
		    wekaInstance.setClassMissing();
		    wekaInstances.add(wekaInstance);
		}
	    return wekaInstances;
	}

}
