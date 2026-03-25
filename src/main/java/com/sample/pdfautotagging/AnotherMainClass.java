package com.sample.pdfautotagging;

import com.sample.pdfautotagging.models.json.Page;
import com.sample.pdfautotagging.services.TaggingMappingEngine;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;

import java.io.File;
import java.io.IOException;

public class AnotherMainClass {

    public static void main(String[] args) {

        File pdfFile = new File("/home/garbi/Documents/PDF_Auto_Tagging/Testing the Pdf_Clean .pdf");

        try (PDDocument pdDocument = Loader.loadPDF(pdfFile)) {
            System.out.println("Starting");


            //We would text our cleaning capabilities

            //First we clean the pdf for any marked content

            TaggingMappingEngine taggingMappingEngine = new TaggingMappingEngine(new Page());
            taggingMappingEngine.getText(pdDocument);


    } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }}
