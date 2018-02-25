package de.upb.crc901.services.mlpipeline;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/**
 * 
 * A class that helps 'plan' a ML-pipeline.
 * 
 * Currently a ML-pipeline only exists of a series of AttributeSelection's (0 or more) followed by a single classifier.
 * 
 * @author aminfaez
 *
 */
public class MLPipelinePlan {
	// list of preprocessors
	private List<AttributeSelectionPipe> atrPipes = new LinkedList<>();
	
	// Points to the end of the pipeline.
	private ClassifierPipe cPipe;

	// contains the host name of the next added pipeline.
	private String nextHost;
	
	public MLPipelinePlan onHost(String hostname) {
		this.nextHost = Objects.requireNonNull(hostname);
		return this;
	}
	
	public MLPipelinePlan onHost(String host, int port) {
		return onHost(host + ":" + port);
	}
	
	public AttributeSelectionPipe addAttributeSelection() {
		Objects.requireNonNull(this.nextHost, "Host needs to be specified before adding pipes to the pipeline.");
		AttributeSelectionPipe asPipe =  new AttributeSelectionPipe(this.nextHost);
		atrPipes.add(asPipe); // add to pipe list before returning.
		return asPipe;
	}
	
	public ClassifierPipe setClassifier(String classifierName) {
		Objects.requireNonNull(this.nextHost, "Host needs to be specified before adding pipes to the pipeline.");
		this.cPipe = new ClassifierPipe(this.nextHost, classifierName); // set cPipe field.
		return cPipe;
	}
	
	/**
	 * Returns True if the plan is 'valid' in the sense that a classifier was set.
	 */
	public boolean isValid() {
		if(cPipe == null) { // if classifier is null return false immediately
			return false;
		}
		for(AttributeSelectionPipe pipe : atrPipes) { 
			if(!pipe.isValid()) {
				return false;
			}
		}
		return true;
	}
	
	public List<AttributeSelectionPipe> getAttrSelections(){
		return atrPipes;
	}
	
	public ClassifierPipe getClassifierPipe() {
		return cPipe;
	}
	
	
	
	// CLASSES for pipe creation.
	abstract class AbstractPipe {
		private final String host;
		
		protected AbstractPipe(String hostname) {
			this.host = Objects.requireNonNull(hostname);
		}
		
		protected String getHost() {
			return this.host;
		}
	}
	
	class ClassifierPipe extends AbstractPipe {
		private final String classifierName;
		private final Set<String> classifierOptions = new TreeSet<>();
		private final List<Object> constructorArgs = new ArrayList<>(); 
		
		protected ClassifierPipe(String hostname, String classifierName) {
			super(hostname);
			this.classifierName = Objects.requireNonNull(classifierName);
		}
		
		public ClassifierPipe addOptions(String...additionalOptions) {
			Objects.requireNonNull(additionalOptions);
			for(String newOption : additionalOptions) {
				classifierOptions.add(newOption);
			}
			return this;
		}
		
		public ClassifierPipe addConstructorArgs(Object... args) {
			Objects.requireNonNull(args);
			for(Object newArg : args) {
				this.constructorArgs.add(newArg);
			}
			return this;
		}
		
		public String getName() {
			return classifierName;
		}
		public ArrayList<String> /*ArrayList was explicitly used*/ getOptions(){
			ArrayList<String> options = new ArrayList<>();
			options.addAll(classifierOptions);
			return options;
		}
		public Object[] getArguments() {
			return constructorArgs.toArray();
		}
		
	}
	
	class AttributeSelectionPipe extends AbstractPipe {
		private String searcherName, evalName; 
		String bla = "weka.attributeSelection.AttributeSelection";
		protected AttributeSelectionPipe(String host) {
			super(host);
		}
		private Set<String> 	searcherOptions = new TreeSet<>(), 
							evalOptions = new TreeSet<>();
		
		public AttributeSelectionPipe withSearcher(String searcherName) {
			this.searcherName = Objects.requireNonNull(searcherName);
			return this;
		} 
		
		public AttributeSelectionPipe withEval(String evaluator) {
			this.evalName = Objects.requireNonNull(evaluator);
			return this;
		}

		public AttributeSelectionPipe addSearchOptions(String... additionalOptions) {
			addToOptionList(searcherOptions, additionalOptions);
			return this;
		}
		
		public AttributeSelectionPipe addOptions(String... additionalOptions) {
			addToOptionList(evalOptions, additionalOptions);
			return this;
		}
		
		private void addToOptionList(Set<String> optionList, String[] additionalOptions) {
			Objects.requireNonNull(additionalOptions);
			for(String newOption : additionalOptions) {
				optionList.add(newOption);
			}
		}
		
		public String getSearcher() {
			return searcherName;
		}
		public String getEval() {
			return evalName;
		}
		
		public ArrayList<String> getSearcherOptions(){
			ArrayList<String> options = new ArrayList<>();
			options.addAll(searcherOptions);
			return options;
		}
		
		public ArrayList<String> getEvalOptions(){
			ArrayList<String> options = new ArrayList<>();
			options.addAll(evalOptions);
			return options;
		}

		protected boolean isValid() {
			if(searcherName == null || evalName == null) {
				return false;
			}
			return true;
		}
	}
}
