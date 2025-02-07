package org.eclipse.bpmn2.modeler.suggestion.algorithm;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.StringReader;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

import org.cheetahplatform.common.logging.AuditTrailEntry;
import org.cheetahplatform.common.logging.Process;
import org.cheetahplatform.common.logging.ProcessInstance;
import org.cheetahplatform.common.logging.PromLogger;
import org.eclipse.bpmn2.modeler.suggestion.Activator;
import org.eclipse.bpmn2.modeler.ui.Bpmn2DiagramEditorInput;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IWorkspace;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.FileLocator;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.Status;
import org.eclipse.emf.common.util.URI;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.IFileEditorInput;
import org.eclipse.ui.PlatformUI;
import org.osgi.framework.Bundle;
import org.semanticweb.owlapi.io.OWLObjectRenderer;
import org.semanticweb.owlapi.io.OWLXMLOntologyFormat;
import org.semanticweb.owlapi.model.AxiomType;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLDataProperty;
import org.semanticweb.owlapi.model.OWLNamedIndividual;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyCreationException;
import org.semanticweb.owlapi.model.OWLOntologyStorageException;
import org.semanticweb.owlapi.model.SWRLRule;
import org.semanticweb.owlapi.reasoner.OWLReasoner;
import org.semanticweb.owlapi.reasoner.OWLReasonerConfiguration;
import org.semanticweb.owlapi.reasoner.OWLReasonerFactory;
import org.semanticweb.owlapi.reasoner.SimpleConfiguration;
import org.semanticweb.owlapi.reasoner.structural.StructuralReasonerFactory;

import ugent.mis.cmoeplus.CMOEplusProperties;
import ugent.mis.cmoeplus.Recommendation;
import ugent.mis.cmoeplus.RecommendationServices;
import uk.ac.manchester.cs.owlapi.dlsyntax.DLSyntaxObjectRenderer;

import com.aliasi.spell.JaroWinklerDistance;

import edu.smu.tspell.wordnet.Synset;
import edu.smu.tspell.wordnet.WordNetDatabase;
import edu.stanford.nlp.ling.HasWord;
import edu.stanford.nlp.ling.TaggedWord;
import edu.stanford.nlp.process.DocumentPreprocessor;
import edu.stanford.nlp.tagger.maxent.MaxentTagger;


public class BPMNSuggestionEngine3 extends RecommendationServices {

	private PromLogger recommendationLogger;

	private URL wordnet_url;
	private File wordnet;

	//LexicalizedParser lp;
	//ShiftReduceParser model;
	MaxentTagger tagger;
	final String task = "http://www.mis.ugent.be/ontologies/bpmn.owl#Task";
	final String activity = "http://www.mis.ugent.be/ontologies/bpmn.owl#Activity";
	final String event = "http://www.mis.ugent.be/ontologies/bpmn.owl#Event";
	final String pool = "http://www.mis.ugent.be/ontologies/bpmn.owl#Pool";
	final String message = "http://www.mis.ugent.be/ontologies/bpmn.owl#Message";
	final String gateway = "http://www.mis.ugent.be/ontologies/bpmn.owl#Gateway";
	final String qualityUniversal = "http://www.mis.ugent.be/ontologies/ufo.owl#Quality_Universal";

	//Set parameters for jaro-winkler distance:
	double JWDweightThreshold=0.4;
	int JWDnumChars=4;


	public BPMNSuggestionEngine3(){
		super();
		//recommendationLogger = new PromLogger();

		Properties systemProperties = System.getProperties();

		//String modelPath = "edu/stanford/nlp/models/srparser/englishSR.ser.gz";
		// String taggerPath = "models/english-left3words-distsim.tagger";
		//lp = LexicalizedParser.loadModel("edu/stanford/nlp/models/lexparser/englishPCFG.ser.gz");
		//	tagger = new MaxentTagger(taggerPath);
		//model = ShiftReduceParser.loadModel(modelPath);
		//System.out.println(source);

		modelName ="model";
		
		String source ="local";

		if(source.equals("cheetah")) {
			try {
				Bundle bundle = Platform.getBundle("org.cheetahplatform.modeler");
				//Bundle bundle = Platform.getBundle("org.eclipse.bpmn2.modeler.suggestion");
				System.out.println(bundle.getLocation());
				System.out.println(System.getProperty("user.dir"));

				wordnet = new File(FileLocator.resolve(bundle.getEntry("resource/ugent/ontology/dict")).toURI());

				manager = new CheetahOntologyManager("org.cheetahplatform.modeler","");
			} catch (Exception e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
			}

		}
		else if(source.equals("bizagi")){
			try{

				Bundle bundle = Platform.getBundle("org.eclipse.bpmn2.modeler.suggestion");

				System.out.println("BUNDLE: " + bundle.getLocation());
				System.out.println("USER.DIR: "+ System.getProperty("user.dir"));

				wordnet = new File("resource/ugent/ontology/dict");

				//wordnet = new File(FileLocator.resolve(bundle.getEntry("resource/ugent/ontology/dict")).toURI());


				IPreferenceStore preferenceStore = Activator.getDefault().getPreferenceStore();
				manager = new BizagiOntologyManager(preferenceStore);
			} catch (Exception e1) {
				System.out.println(e1.getMessage());
				e1.printStackTrace();
			}
		}

		else if(source.equals("local")){

			try {
				URL propertiesURL;
				String platformURL = "platform:/plugin/org.eclipse.bpmn2.modeler.suggestion";
				if(systemProperties.containsKey("Experiment") && systemProperties.getProperty("Experiment").equals("true")){

					propertiesURL = new URL("platform:/plugin/org.eclipse.bpmn2.modeler.suggestion/experiment.properties");
				}
				else if(systemProperties.containsKey("Tutorial") && systemProperties.getProperty("Tutorial").equals("true")){
					propertiesURL = new URL("platform:/plugin/org.eclipse.bpmn2.modeler.suggestion/tutorial.properties");
				}
				else {
					propertiesURL = new URL("platform:/plugin/org.eclipse.bpmn2.modeler.suggestion/suggestionalgorithm.properties");
				}

				FileInputStream fileInput = (FileInputStream) propertiesURL.openConnection().getInputStream();
				properties = new CMOEplusProperties(fileInput);

				URL wordnet_url = new URL("platform:/plugin/org.eclipse.bpmn2.modeler.suggestion/dict");
				wordnet = new File(FileLocator.resolve(wordnet_url).toURI());

				manager = new LocalOntologyManager(platformURL, properties);

			} catch (Exception e1) {
				System.out.println(e1.getMessage());
				e1.printStackTrace();
			}

		}
		else if(source.equals("deploy")){
			try {
				File file; 

				if(systemProperties.containsKey("Experiment") && systemProperties.getProperty("Experiment").equals("true")){
					file = new File("experiment.properties");
				}
				else if(systemProperties.containsKey("Tutorial") && systemProperties.getProperty("Tutorial").equals("true")){
					file = new File("tutorial.properties");
				}
				else {
					file = new File("suggestionalgorithm.properties");
				}
				FileInputStream fileInput = new FileInputStream(file);
				properties = new CMOEplusProperties(fileInput);

				System.out.println(System.getProperty("user.dir"));

				wordnet = new File("dict");

				/*CoO = manager.loadOntologyFromOntologyDocument(new File("ontology/" + CoO_filename));

					System.out.println("Loaded ontology: " + CoO);

					MLO = manager.loadOntologyFromOntologyDocument(new File("ontology/" + MLO_filename));

					System.out.println("Loaded ontology: " + MLO);

					CoO_MLO = manager.loadOntologyFromOntologyDocument(new File("ontology/" + CoO_MLO_filename ));

					System.out.println("Loaded ontology: " + CoO_MLO);

					ESO = manager.loadOntologyFromOntologyDocument(new File("ontology/" + ESO_filename));

					System.out.println("Loaded ontology: " + ESO);


					semAnnO = manager.loadOntologyFromOntologyDocument(new File("ontology/" + semAnnO_filename));

					System.out.println("Loaded ontology: " + semAnnO);


					rulesO =manager.loadOntologyFromOntologyDocument(new File("ontology/" + rulesO_filename));

					System.out.println("Loaded ontology: " + rulesO);
					listSWRLRules(rulesO);

					System.out.println("Done Loading Ontologies");*/

			} catch (Exception e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
			}

		}
		else {
			System.out.println("Ontologies could not be loaded");
		}


	if(systemProperties.containsKey("Experiment") && systemProperties.getProperty("Experiment").equals("true")){
		manager.makeModelOntology("default.owl");
	}
	else if(systemProperties.containsKey("Tutorial") && systemProperties.getProperty("Tutorial").equals("true")){
		manager.makeModelOntology("default.owl");
	}
	else {
		IEditorInput input = PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage().getActiveEditor().getEditorInput();
		IProject project;
		String filename; 
		File file;
		if (input instanceof Bpmn2DiagramEditorInput) {
			Bpmn2DiagramEditorInput bpmnInput = (Bpmn2DiagramEditorInput) input;
			URI inputUri = bpmnInput.getModelUri();
			String inputString = inputUri.path();
			String model = inputString.split("/")[2];
			IWorkspace ws = ResourcesPlugin.getWorkspace();
			project = ws.getRoot().getProject(model);
			filename = bpmnInput.getName();
			String projectDir = project.getLocationURI().getPath();
			file = new File(projectDir + "/" + filename + ".owl");
		} else {
			project = ((IFileEditorInput)input).getFile().getProject();
			filename = ((IFileEditorInput)input).getFile().getName();
			String projectDir = project.getLocationURI().getPath();
			file = new File(projectDir + "/" + filename.substring(0, filename.length()-5)  + ".owl");
		}

		if(file.exists()){
			System.out.println("EXISTS:" + filename.toString());
			manager.loadModelOntology(file);
		}
		else{
			System.out.println("DOES NOT EXISTS:" + filename + ".owl");
			manager.makeModelOntology(filename + ".owl");
		}
	}

	manager.mergeOntology("http://www.mis.ugent.be/ontologies/mymerge");

	logNewProcessInstance();

	initializeSuggestionList();


}


public void log(AuditTrailEntry entry) {
	IStatus status = recommendationLogger.append(entry);
	if (status.getSeverity() == IStatus.WARNING) {
		MessageDialog.openWarning(PlatformUI.getWorkbench().getActiveWorkbenchWindow().getShell(), "Logger Changed",
				status.getMessage());
	}

}

public void logNewProcessInstance() {

	Process process = new Process("Create Business Process with suggestions");
	ProcessInstance instance = new ProcessInstance();
	instance.setId(modelName);
	PromLogger.addHost(instance);

	try {
		recommendationLogger.append(process, instance);
	} catch (Exception e) {
		IStatus status = new Status(IStatus.ERROR, Activator.PLUGIN_ID, "Could not initialize the log file.", e);
		Activator.getDefault().getLog().log(status);
	}
}







public OWLReasoner getReasoner(OWLOntology ontology){
	OWLReasonerFactory reasonerFactory = new StructuralReasonerFactory();

	OWLReasonerConfiguration config = new SimpleConfiguration();
	return reasonerFactory.createReasoner(ontology, config);

}

public void testConsistency(OWLOntology ontology){

	// Ask the reasoner to do all the necessary work now
	OWLReasoner reasoner = getReasoner(ontology);
	reasoner.precomputeInferences();
	// We can determine if the ontology is actually consistent (in this
	// case, it should be).
	boolean consistent = reasoner.isConsistent();
	System.out.println("Consistent: " + consistent);
}




public static void listSWRLRules(OWLOntology ontology) { 
	OWLObjectRenderer renderer = new DLSyntaxObjectRenderer(); 
	for (SWRLRule rule : ontology.getAxioms(AxiomType.SWRL_RULE)) { 
		System.out.println(renderer.render(rule)); 
	} 
}




public Set<String>  getSynonyms(String label){
	System.setProperty("wordnet.database.dir", wordnet.getAbsolutePath());
	String[] wordForms = null;
	WordNetDatabase database = WordNetDatabase.getFileInstance();
	Synset[] synsets = database.getSynsets(label);
	Set<String> result = new HashSet();
	for (int i = 0; i < synsets.length; i++)
	{

		Synset synset = synsets[i];
		//System.out.println("SYN: " + synset.getDefinition());
		wordForms = synset.getWordForms();
		for (int j = 0; j < wordForms.length; j++)
		{
			String word = wordForms[j];
			result.add(word);
		}
	}
	return result;

}








public double getJaroWinklerDistance(String string1, String string2, double jWDweightThreshold, int jWDnumChars){
	JaroWinklerDistance jwd = new JaroWinklerDistance(jWDweightThreshold,jWDnumChars);
	return 1- jwd.distance((CharSequence)string1.toLowerCase(), string2.toLowerCase());
}





public void printSugList(SortedSet<Recommendation> sugList){
	System.out.println();
	for(Recommendation sug: sugList)
		System.out.println(sug.getIri() + "   Weight=" + sug.getScore());
	System.out.println();

}



public Set<OWLNamedIndividual> filterIndividuals(Set<OWLNamedIndividual> entities){
	Set<OWLNamedIndividual> filteredEntities = new TreeSet<OWLNamedIndividual>();
	for (OWLNamedIndividual entity : entities) {
		if(manager.getESO().containsIndividualInSignature(entity.getIRI()))
			filteredEntities.add(entity);
	}
	return filteredEntities;
}

public Set<OWLDataProperty> filterDataProperties(Set<OWLDataProperty> entities){
	Set<OWLDataProperty> filteredEntities = new TreeSet<OWLDataProperty>();
	for (OWLDataProperty entity : entities) {
		if(manager.getESO().containsDataPropertyInSignature(entity.getIRI()))
			filteredEntities.add(entity);
	}
	return filteredEntities;
}

public List<String> parseSentence2(String input, String Tag){

	//List<HasWord> sentence = Sentence.toWordList(input + ".");
	//List<Word> sentence2 = Sentence.toUntaggedList(input);
	DocumentPreprocessor tokenizer = new DocumentPreprocessor(new StringReader(input));
	List<TaggedWord> tagged = null;
	for (List<HasWord> sentence : tokenizer)
		tagged = tagger.tagSentence(sentence);

	List<String> taggedWords = new ArrayList<String>(); 
	for (TaggedWord tw : tagged) {
		if (tw.tag().startsWith(Tag)) {
			taggedWords.add(tw.word());
			//System.out.printf("%s/%s%n", tw.word(), tw.tag());
		}
	}
	//parse.pennPrint();
	//System.out.println();
	return taggedWords;
}


public void saveModelOntology() throws OWLOntologyStorageException, OWLOntologyCreationException, IOException {

	//System.out.println(PromLogger.getHost());
	//recommendationLogger.close();
	// Now save a local copy of the ontology. (Specify a path appropriate to
	// your setup)
	//Bundle bundle = Platform.getBundle("org.eclipse.bpmn2.modeler.suggestion");
	Properties systemProperties = System.getProperties();
	IProject project;
	String filename; 
	File file;
	String projectDir;
	//		 if(systemProperties.containsKey("Experiment") && systemProperties.getProperty("Experiment").equals("true")){
	//			 	IWorkspace ws = ResourcesPlugin.getWorkspace();
	//				project = ws.getRoot().getProject("default.bpmn");
	//				projectDir = project.getLocationURI().getPath();
	//				file = new File(projectDir + "/" + "default.owl");
	//		 }
	//		 else if(systemProperties.containsKey("Tutorial") && systemProperties.getProperty("Tutorial").equals("true")){
	//			 	IWorkspace ws = ResourcesPlugin.getWorkspace();
	//				project = ws.getRoot().getProject("default.bpmn");
	//				projectDir = project.getLocationURI().getPath();
	//				file = new File(projectDir + "/" + "default.owl");
	//		}
	//		else {
	if(PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage().getActiveEditor() != null){
		IEditorInput input = PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage().getActiveEditor().getEditorInput();

		if (input instanceof Bpmn2DiagramEditorInput) {
			Bpmn2DiagramEditorInput bpmnInput = (Bpmn2DiagramEditorInput) input;
			URI inputUri = bpmnInput.getModelUri();
			String inputString = inputUri.path();
			String model = inputString.split("/")[2];
			IWorkspace ws = ResourcesPlugin.getWorkspace();
			project = ws.getRoot().getProject(model);
			filename = bpmnInput.getName();
			projectDir = project.getLocationURI().getPath();
			file = new File(projectDir + "/" + filename  + ".owl");

		} else {
			project = ((IFileEditorInput)input).getFile().getProject();
			filename = ((IFileEditorInput)input).getFile().getName();
			//IWorkspace ws = ResourcesPlugin.getWorkspace();
			projectDir = project.getLocationURI().getPath();
			file = new File(projectDir + "/" + filename.substring(0, filename.length()-5)  + ".owl");

		}
		//}

		OWLXMLOntologyFormat owlxmlFormat = new OWLXMLOntologyFormat();
		manager.getOWLManager().saveOntology(manager.getModelO(), owlxmlFormat, IRI.create(file.toURI()));


		System.out.println("Owl file saved: " + file.getAbsolutePath());
		System.out.println("Owl file saved to: " + projectDir);

		file.createNewFile();
	}

}




@Override
protected double getStringDistance(String text1, String text2) {
	return this.getJaroWinklerDistance(text1, text2, this.JWDweightThreshold, this.JWDnumChars);
}



}
