package edu.asu.javascript.parser;

import java.util.Iterator;
import java.util.List;

import org.mozilla.javascript.Parser;
import org.mozilla.javascript.Token;
import org.mozilla.javascript.ast.Assignment;
import org.mozilla.javascript.ast.AstNode;
import org.mozilla.javascript.ast.AstRoot;
import org.mozilla.javascript.ast.FunctionCall;
import org.mozilla.javascript.ast.Name;
import org.mozilla.javascript.ast.NodeVisitor;
import org.mozilla.javascript.ast.PropertyGet;
import org.mozilla.javascript.ast.StringLiteral;
import org.mozilla.javascript.ast.VariableInitializer;

import edu.asu.Constants;
import edu.asu.Util;
import edu.asu.javascript.JsDoc;

public class JavascriptParser {

	public JsDoc jsDocObj;

	public JavascriptParser() {
		jsDocObj = new JsDoc();
	}

	public void parser(String jsString) {
		
		NodeVisitor nodeVisitor = new NodeVisitor() {
			public boolean visit(AstNode node) {
				int type = node.getType();
				 //try handle situations like object.property
				   
				  
				if (type == Token.CALL) {
					isDocumentSelector(node);
				}
				 if (type == Token.ASSIGN){
					   //handleExpressionStatement(node); 
						  // System.out.println();
						   isDomIdentifierAssignment((Assignment)node);
				 } 
				return true;
			}
		};
		
		
		AstRoot astRoot = new Parser().parse(jsString, "uri", 1);
		//The following code may be useful in future for getting all filenames.
	/*	List<AstNode> statList = astRoot.getStatements();
		for (Iterator<AstNode> iter = statList.iterator(); iter.hasNext();) {
			FunctionNode fNode = (FunctionNode) iter.next();
			AstNode body = fNode.getBody();
			System.out.println(body.getFirstChild());
			System.out.println(body.getFirstChild().getNext()); 
			System.out.println("*** function Name : " + fNode.getName()
					+ ", paramCount : " + fNode.getParamCount() + ", depth : "
					+ fNode.depth()); 
			AstNode bNode = fNode.getBody();
			Block block = (Block) bNode;
			// visitBody(block);
		}*/
		
		astRoot.visit(nodeVisitor);
		System.out.println("Js ---> DOM \n\nListing DOM IDs read by Javascript :-");
		for(String elt: jsDocObj.getIdFetchedEltList()){
			
			System.out.println(elt);
		} 

		System.out.println("\nListing DOM IDs written to by Javascript :-");
		for(String eltDomId: jsDocObj.getDomEltWrittenToMap().keySet()){
			System.out.print("The DOM id "+eltDomId+" was written on line numbers ");
			for(Integer lineNum: jsDocObj.getDomEltWrittenToMap().get(eltDomId)){
				System.out.print(lineNum+", ");
			}
			System.out.println("");
		}
		System.out.println("**** +++ ****");

	}
	
	//This method also checks whether it is a element object we already know of and is being tried to modified using a assignment
		// example var elt = document.getElementById("element");
		//elt.style.display = "none";
		//Here elt is modified therefore it is a dependency
	public boolean isDomIdentifierAssignment(Assignment assignExpr){
		//System.out.println(assignExpr.getLineno());
		if (assignExpr.getLeft() != null && assignExpr.getLeft()  instanceof PropertyGet) {
			PropertyGet property = (PropertyGet) assignExpr.getLeft();

			// check if the identifier exists in our identifier list
			String assignmentObject = getLeftMostPropertyName(property);
		 if (assignmentObject != null && jsDocObj.getVariableIdentifier().containsKey(assignmentObject)) {
			 jsDocObj.setDomEltWrittenToMap(jsDocObj.getVariableIdentifier().get(assignmentObject), property.getLineno());
				
				//System.out.println("The DOM id \""+jsDocObj.getVariableIdentifier().get(assignmentObject)+"\" was accessed on line number "+property.getLineno());
			}
		}
		return false;
	}
	
	
	public String getLeftMostPropertyName(PropertyGet prop){ 
		if(prop.getLeft() instanceof  PropertyGet){
			return getLeftMostPropertyName((PropertyGet)prop.getLeft());
			
		}else if(prop.getLeft() instanceof Name){
			return prop.getLeft().getString();
		}
		return null;
		
	}
	
	//This method creates a list of all the dom references using document.getElementById("element")
	//This method also checks whether it is a element object we already know of and is being tried to modified using a function call
	// example var elt = document.getElementById("element");
	//elt.appendChild(somethingElse);
	//Here elt is modified therefore it is a dependency 
	
	public boolean isDocumentSelector(AstNode node) {
		
		/*VariableInitializer elementDeclaration = (VariableInitializer) node.getParent();
		elementDeclaration.getTarget().getString();
		elementDeclaration.getScope();*/
		FunctionCall functionExp = (FunctionCall) node;

		// check it is of type PropertyGet
		if (functionExp.getTarget() != null && functionExp.getTarget() instanceof PropertyGet) {
			PropertyGet property = (PropertyGet) functionExp.getTarget();

			// check if the call is made on the document object
			if (property.getLeft() != null
					&& Util.compareString(property.getLeft().getString(),Constants.DOCUMENT_OBJECT)) {

				// what function is called on the document object
				for (String attribute : Util.getDOMSelectors()) {
					
					if (Util.compareString(property.getRight().getString(), attribute)) {
						
						// Access all args and add to the list
						List allargs = functionExp.getArguments();
						for (Iterator iter = allargs.iterator(); iter.hasNext();) {
							Object currArg = iter.next();
							
							if (currArg instanceof StringLiteral) {
								StringLiteral strArg = (StringLiteral) currArg;
								jsDocObj.getIdFetchedEltList().add(strArg.getValue());
								//put the var identifier and value in the map eg.
								//var answer = document.getElementById("element");
								//key for map is answer and value is element.
								if(getVarInitializer(functionExp.getParent()) != null){
									jsDocObj.getVariableIdentifier().put(getVarInitializer(functionExp.getParent()), strArg.getValue());
								}
								//getVarInitializer(functionExp.getParent());
							}
							//TO do:
							// This is very dangerous, you are saving the instances reference not the actual value being passed
							else if (currArg instanceof Name) {
								Name varArg = (Name) currArg;
								jsDocObj.getIdFetchedEltList().add(varArg.getString());
								//jsDocObj.getIdentifierMap().put(key, varArg.getString())
							}
						}
						return true;
					}

				}
			}
			//check whether it is a identifier we already know of and is being tried to accessed on its properties.
			else if(property.getLeft() != null){
				//check identifier in existing list
				if(jsDocObj.getVariableIdentifier().containsKey(property.getLeft().getString())){
					jsDocObj.setDomEltWrittenToMap(jsDocObj.getVariableIdentifier().get(property.getLeft().getString()), property.getLineno());
					//System.out.println("The DOM element referencing id \""+jsDocObj.getVariableIdentifier().get(property.getLeft().getString())+"\" in the html was accessed on line number "+property.getLineno());
				}
			}

		}
		return false;
	}
	
	// this method returns the reference string of a variable initializer node
	public String getVarInitializer(AstNode node){
		if(node != null && node instanceof VariableInitializer){
			return ((VariableInitializer)node).getTarget().getString();  
		}
		return null;
	}

}
