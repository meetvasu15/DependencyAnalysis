package edu.asu.javascript;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import org.mozilla.javascript.ast.AstNode;

public class JsDoc {
	/*
	 * This is a list of all the Dom ids referenced by the javascript 
	 */
	private ArrayList<String> idFetchedEltList;
	private ArrayList<String> classFetchedEltList;
	
   /* this is a map of the dom id referened by javascript
	* eg var element = document.getElementById("myHtmlId");
	* key: "element", value: "myHtmlId"
	*/
	private Map<String, String> variableIdentifier;
	
	/* This is a 2D map the key of the parent map is function name or "global", relates to the scope of the identifiers 
	 * the inner map key is the name of the variable and  the value is the ASTNode itself.
	 */
	private Map<String, Map<String, AstNode>> identifierMap;
	
	/*
	 * The following map keeps track of Dom element objects that tried to write or change some property of the element
	 * for example
	 * var elt = document.getElementById("someId");
	 * elt.style.display="none";
	 * or
	 * elt.appendChild("any bizzare content");
	 * key: someElt Value = List of all line numbers where it was accessed
	 */
	private Map<String, ArrayList<Integer>> domEltWrittenToMap;
	
	public JsDoc (){
		idFetchedEltList = new ArrayList<String>();
		classFetchedEltList = new ArrayList<String>();
		setVariableIdentifier(new HashMap<String, String>());
		domEltWrittenToMap = new HashMap<String, ArrayList<Integer>>();
		
	}
	
	public ArrayList<String> getIdFetchedEltList() {
		return idFetchedEltList;
	}
	public void setIdFetchedEltList(ArrayList<String> idFetchedEltList) {
		this.idFetchedEltList = idFetchedEltList;
	}
	public ArrayList<String> getClassFetchedEltList() {
		return classFetchedEltList;
	}
	public void setClassFetchedEltList(ArrayList<String> classFetchedEltList) {
		this.classFetchedEltList = classFetchedEltList;
	}

	public Map<String, String> getVariableIdentifier() {
		return variableIdentifier;
	}

	public void setVariableIdentifier(Map<String, String> variableIdentifier) {
		this.variableIdentifier = variableIdentifier;
	}

	public Map<String, Map<String, AstNode>> getIdentifierMap() {
		return identifierMap;
	}

	public void setIdentifierMap(Map<String, Map<String, AstNode>> identifierMap) {
		this.identifierMap = identifierMap;
	}
	
	public void setDomEltWrittenToMap(String key, int lineNo){
		if(!domEltWrittenToMap.containsKey(key)){
			domEltWrittenToMap.put(key, (new ArrayList<Integer>()));
		}
		domEltWrittenToMap.get(key).add(lineNo);
	}
	public Map<String, ArrayList<Integer>> getDomEltWrittenToMap(){
		return domEltWrittenToMap;
	}
}
