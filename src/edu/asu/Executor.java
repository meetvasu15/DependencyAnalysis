package edu.asu;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;

import org.jsoup.nodes.Attribute;
import org.jsoup.nodes.Attributes;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import edu.asu.html.HtmlUtil;
import edu.asu.html.parser.HtmlParser;
import edu.asu.javascript.parser.JavascriptParser;
public class Executor {

	/**
	 * @param args
	 */
	public static ArrayList<String> allJsRelUrl = new ArrayList<String>();
	public static String hostUrl;
	public static void main(String[] args) {
		if(args.length == 0){ 
		
		//HtmlParser htmlParser = new HtmlParser("http://lead2.poly.asu.edu/painreport/", true);
			hostUrl = "http://ec2-54-225-54-52.compute-1.amazonaws.com/codecorpus/spriteanimation/";
			System.out.println("Running dependency analysis for "+hostUrl);
		}else{
			hostUrl = args[0];
		}
		HtmlParser htmlParser = new HtmlParser(hostUrl, true);
		Elements allElts = HtmlUtil.getAllHtmlElements(htmlParser.getDocumentObject());
		getDOMEvents(allElts);
		getExternalLinkDependencies(htmlParser.getDocumentObject());
		String jsString = fetchJavascriptFromUrl(allJsRelUrl);
		getDOMCss3Dependencies(allElts); 
	/*	String jsString = "function jsString(){" +
									"var a = 1; " +
									"var mydiv = 'heyIamInsideVariable';" +
									"var domElement = document.getElementById('myDivContainer');"+
									"document.getElementsByName().val='23444'; " +
							"} " +
							"function anotherFunc(e){" +
									"var b =3;" +
							"}";*/
		getJavascriptFunctions(jsString);
		
	}
	
	// O(n^2) :( Think! Think! Think!
	// distinguish in js written in the DOM itself and external function call
	// we need to get more intelligent on eventlisteners being attached by javascript..
	// But to me its a Javascript to HTML dependency not the other way... :|
	public static HashMap<String, String> getDOMEvents(Elements allElts){
		 HashMap<String, String> retMap = new HashMap<String, String>();
		ArrayList<String> allDomEventList= HtmlUtil.getAllHtmlListeners();
		Attributes attrs;
		System.out.println("Listing all HTML --> Javascript dependencies:-\n");
		for(Element oneElt: allElts){
			attrs = oneElt.attributes();
			for (Attribute oneAttr: attrs){
				if(allDomEventList.contains(oneAttr.getKey().trim())){
					retMap.put(oneAttr.getKey(), oneAttr.getValue());
					System.out.println("Listener Type: "+oneAttr.getKey()+", dependent JS: "+oneAttr.getValue());
				}
			}
			
		}
		System.out.println("\n");
		return retMap;
		
	}
	
	// again this is O(n^2), we need to modify and merge this method with getDomEvents coz the same
	//two lists are being iterated in nested for loops
	// This method only looks for class attribute in an element for now
	public static HashMap<String, String> getDOMCss3Dependencies(Elements allElts){
		 HashMap<String, String> retMap = new HashMap<String, String>();
		ArrayList<String> allDomCssDepList= HtmlUtil.getAllHtmlCssSelectorList();
		Attributes attrs;
		System.out.println("Listing all HTML --> Css3 dependencies:-\n");
		for(Element oneElt: allElts){
			attrs = oneElt.attributes();
			for (Attribute oneAttr: attrs){
				if(allDomCssDepList.contains(oneAttr.getKey().trim())){
					retMap.put(oneAttr.getKey(), oneAttr.getValue());
					System.out.println("Attr Type: "+oneAttr.getKey()+", dependent CSS class: "+oneAttr.getValue());
				}
			}
			
		}
		System.out.println("\n");
		return retMap;
		
	}
	
	public static HashMap<String, String> getExternalLinkDependencies(Document doc){
		HashMap<String, String> retMap = new HashMap<String, String>();
		// get all JS links
		System.out.println("Listing all HTML --> JS ext dpendencies:-\n");
		Elements allElts = doc.select("script");
		Attributes attrs;
		for(Element oneElt: allElts){
			attrs = oneElt.attributes();
			if(attrs.hasKey("src")){
				retMap.put("js", attrs.get("src"));
				System.out.println(attrs.get("src"));
				allJsRelUrl.add(hostUrl+attrs.get("src"));
			}
			
		}
		System.out.println("\n");
		// get all CSS links
		System.out.println("Listing all HTML --> CSS ext dpendencies:-\n");
		Elements allCSSElts = doc.select("link");
		Attributes CSSAttrs;
		for(Element oneElt: allCSSElts){
			CSSAttrs = oneElt.attributes();
			if((CSSAttrs.hasKey("rel") && CSSAttrs.get("rel").equalsIgnoreCase("stylesheet"))
					|| (CSSAttrs.hasKey("type") && CSSAttrs.get("type").equalsIgnoreCase("text/css"))){
				retMap.put("css", CSSAttrs.get("href"));
				System.out.println(CSSAttrs.get("href"));
			}
			
		}
		System.out.println("\n");
		return retMap;
	}
	
	public static void getJavascriptFunctions(String jsString){
		JavascriptParser jp = new JavascriptParser();
		jp.parser(jsString);
	}
	
	public static String fetchJavascriptFromUrl(ArrayList<String> UrlList){
		StringBuilder allJavascriptContent= new StringBuilder();
		for(String oneURL: UrlList){
			   try {
				URL oneJsFile = new URL(oneURL);
				 BufferedReader in = new BufferedReader(new InputStreamReader(oneJsFile.openStream()));
					        String inputLine;
					        while ((inputLine = in.readLine()) != null){
					        	allJavascriptContent.append(inputLine+" \n");
					        }
					        in.close();
			} catch (MalformedURLException e) {
				System.out.println("ERROR in fetching Javascript from the HTML Page, probably because of a malformed URL");
				e.printStackTrace();
			} catch (IOException e) {
				System.out.println("ERROR in fetching Javascript from the HTML Page");
				e.printStackTrace();
			}
		}
		return allJavascriptContent.toString();
	}
}
