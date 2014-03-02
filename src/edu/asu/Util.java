package edu.asu;

import java.util.ArrayList;

public class Util {
	public static  boolean isBlankString(String str){
		if(str == null || str.trim().equals("")){
			return true;
		}
		return false;
	}
	
	public static  boolean compareString(String str1, String str2){
		if(str1 != null && str2 != null && (str1.trim()).equalsIgnoreCase(str2.trim())){
			return true;
		}
		return false;
	}
	
	public static ArrayList<String> getDOMSelectors(){
		ArrayList<String> retList = new ArrayList<String>();
		retList.add(Constants.GET_ELEMENT_BY_ID);
		retList.add(Constants.GET_ELEMENTS_BY_CLASS_NAME);
		retList.add(Constants.GET_ELEMENTS_BY_NAME);
		retList.add(Constants.GET_ELEMENTS_BY_TAG_NAME); 
		return retList;
	}

}
