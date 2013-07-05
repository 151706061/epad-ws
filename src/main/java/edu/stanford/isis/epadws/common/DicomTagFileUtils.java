/*
 * Copyright 2012 The Board of Trustees of the Leland Stanford Junior University.
 * Author: Daniel Rubin, Alan Snyder, Debra Willrett. All rights reserved. Possession
 * or use of this program is subject to the terms and conditions of the Academic
 * Software License Agreement available at:
 *   http://epad.stanford.edu/license/
 */
package edu.stanford.isis.epadws.common;

import edu.stanford.isis.epadws.server.ProxyLogger;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;

/**
 *
 *
 * @author alansnyder
 */
public class DicomTagFileUtils {

    private DicomTagFileUtils(){}

    private static final ProxyLogger logger = ProxyLogger.getInstance();

    /* Need to expect the tags can have two versions. Either a UNICODE tag, or not. */
    public static final String PATIENT_NAME = "Patient\u2019s Name";
    public static final String PATIENT_NAME_ALT = "Patient's Name";
    public static final String PATIENT_SEX = "Patient\u2019s Sex";
    public static final String PATIENT_SEX_ALT = "Patient's Sex";
    public static final String PATIENT_AGE = "Patient\u2019s Age";
    public static final String PATIENT_AGE_ALT = "Patient's Age";
    public static final String PATIENT_BIRTHDATE = "Patient\u2019s Birth Date";
    public static final String PATIENT_BIRTHDATE_ALT = "Patient's Birth Date";
    public static final String REFERRING_PHYSICIAN = "Referring Physician\u2019s Name";
    public static final String REFERRING_PHYSICIAN_ALT = "Referring Physician's Name";


    public static final String STUDY_UID = "Study Instance UID";
    public static final String SERIES_UID = "Series Instance UID";
    public static final String SOP_INST_UID = "SOP Instance UID";
    public static final String SOP_CLASS_UID = "SOP Class UID";

    public static final String MODALITY = "Modality";
    public static final String PIXEL_SPACING = "Pixel Spacing";
    public static final String INSTANCE_NUMBER = "Instance Number";
    public static final String SERIES_NUMBER = "Series Number";
    public static final String STUDY_DATE = "Study Date";
    public static final String STUDY_TIME = "Study Time";
    public static final String STUDY_DESCRIPTION = "Study Description";
    public static final String SERIES_DESCRIPTION = "Series Description";
    public static final String PATIENT_ID = "Patient ID";



    public static final String STUDY_ID = "Study ID";
    public static final String ACQUISITION_NUMBER = "Acquisition Number";
    public static final String ROWS = "Rows";
    public static final String COLUMNS = "Columns";
    public static final String WINDOW_CENTER = "Window Center";
    public static final String WINDOW_WIDTH = "Window Width";



    public static final String PERFORMING_PHYSICIAN = "Name of Physician(s) Reading Study";
    public static final String ACCESSION_NUM= "Accession Number";

    public static final String BODY_PART = "";
    public static final String LATERALITY = "";
    public static final String INSTITUTION = "Institution Name";
    public static final String STATION_NAME = "Station Name";
    public static final String DEPARTMENT = "";

    //tags that are unique to each image.
    public static final String SLICE_LOCATION = "Slice Location";
    public static final String CONTENT_TIME = "Content Time";

    //
    public static final String IMAGE_POSITION = "Image Position (Patient)";// might be a redundant version of slice location.


    /**
     * 
     * @param tagName String
     * @param tagMap Map
     * @return String
     */
    public static String getTag(String tagName, Map<String,String> tagMap){
        return tagMap.get(tagName);
    }

    /**
     * The the tag file. The *.tag file should be read in. If a *.dcm file is read in then the extension
     * is swapped for a *.tag.
     * @param tagFile File name ending with *.tag. If a *.dcm is entered then a *.tag is substituted.
     * @return Map of String key and String values.
     * @throws IOException - when file not found for example.
     */
    public static Map<String,String> readTagFile(File tagFile)
            throws IOException
    {
    	System.out.println("Read the tag file from = "+tagFile.getAbsolutePath());
        Map<String,String> tagMap = new HashMap<String,String>();

        if(tagFile.getName().toLowerCase().endsWith(".dcm")){
            tagFile = new File(createTagFilePath(tagFile.getAbsolutePath()));
        }

        FileInputStream fstream = new FileInputStream(tagFile);
        DataInputStream in = new DataInputStream(fstream);
        BufferedReader br = new BufferedReader(new InputStreamReader(in));
        
        //int l=0;
        String line;
        while( (line=br.readLine())!=null ){
            parseLine(line,tagMap);
        }

        System.out.println("Number of tags= "+tagMap.size());
        
        br.close();
        in.close();
        fstream.close();

        return tagMap;
    }

    /**
     * Call them method on a client side when getting the tag file as "plain/text" on a web page.
     * @param textPage String the entire tag file as a string.
     * @return Map of String keys to String values.
     */
    public static Map<String,String> parseText(String textPage){
        Map<String,String> retVal = new HashMap<String, String>();

        String[] lines = textPage.split("\n");
        for(String line : lines){
            parseLine(line,retVal);
        }
        return retVal;
    }

   /**
    *
    * @param line String
    * @param map Map
    */
    private static void parseLine(String line, Map<String,String> map){
        try{
            int p1 = line.indexOf("[");
            int p2 = line.indexOf("]");

            if(p1<0 || p2<=p1){
                return;
            }

            String value = line.substring(p1+1,p2);
            String key = line.substring(p2+1);

            if( value!=null && key!=null){
            	String v1=key.trim();
            	String v2=value.trim();
            	if(!v2.equals(""))
            		map.put(v1, v2);
            }

        }catch(Exception e){
            logger.warning("DicomTagFileUtils had: "+line,e);
        }
    }

    /**
     * Remove the extension and replace with a *.tags extension.
     * @param dicomFile String this is expected to be a dicom file path.
     * @return String tag file path
     * @throws IllegalStateException if dicomFile doesn't end with extension *.dcm
     */
    public static String createTagFilePath(String dicomFile){

        int i = dicomFile.toLowerCase().indexOf(".dcm");
        if( i<0 ){
            //this isn't a dicom file, throw an exception.
            throw new IllegalStateException("Expecting only DICOM files. file="+dicomFile+" , toLower()"+dicomFile.toLowerCase());
        }

        String base = dicomFile.substring(0,i);
        return base+".tag";
    }

}