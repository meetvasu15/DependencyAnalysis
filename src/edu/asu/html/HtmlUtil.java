package edu.asu.html;

import java.util.ArrayList;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;

public class HtmlUtil {
	public static Elements getAllHtmlElements(Document doc){
		return doc.body().select("*");
	}
	
	public static Elements getAllHtmlEltAttr(Document doc, String attr){
		return doc.body().select(attr);
	}
	
	public static ArrayList<String> getAllHtmlListeners(){
		ArrayList<String> retList = new ArrayList<String>();
		retList.add("onclick");
		retList.add("ondblclick");
		retList.add("onmousedown");
		retList.add("onmousemove");
		retList.add("onmouseover");
		retList.add("onmouseout");
		retList.add("onmouseup");
		retList.add("onkeydown");
		retList.add("onkeypress");
		retList.add("onkeyup");
		retList.add("onblur");
		retList.add("onchange");
		retList.add("onfocus");
		retList.add("onreset");
		retList.add("onselect");
		retList.add("onsubmit");
		retList.add("onunload");
		retList.add("onload");
		retList.add("onerror");
		retList.add("onscroll");
		retList.add("onresize");
		return retList;
	}
	public static ArrayList<String> getAllHtmlCssSelectorList(){
		ArrayList<String> retList = new ArrayList<String>();
		retList.add("class"); 
		return retList;
	}
	
}
