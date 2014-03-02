package edu.asu.html.parser;

import java.io.IOException;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

public class HtmlParser {

	/**
	 * @param args
	 */
	//private String htmlContent;
	private Document doc;

	public HtmlParser(String htmlSrc, boolean isUrl) {
		try{
			if(!isUrl){
				//this.htmlContent = htmlSrc;
				this.doc = Jsoup.parse(htmlSrc);
			}else{
				this.doc  = Jsoup.connect(htmlSrc).get();
				
			}
		}catch(IOException io){
			io.printStackTrace();
			System.out.println("error in fetching web content from the URL");
		}
	}

	public Document getDocumentObject() {
		return this.doc;
	}
	
	
}
