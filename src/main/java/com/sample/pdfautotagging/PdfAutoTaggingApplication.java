package com.sample.pdfautotagging;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sample.pdfautotagging.models.Box;
import com.sample.pdfautotagging.models.PdfExtractionResponse;
import com.sample.pdfautotagging.services.PDFDocumentTagAndMarkedContentCleaner;
import com.sample.pdfautotagging.services.PdfAccessibilityTagInjector;
import com.sample.pdfautotagging.services.TaggingMappingEngine;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.text.TextPosition;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@SpringBootApplication
public class PdfAutoTaggingApplication {

    public static void main(String[] args)  {

      //  SpringApplication.run(PdfAutoTaggingApplication.class, args)
        File pdfFile = new File("/home/garbi/Documents/PDF_Auto_Tagging/Final Year Project Topics_2005_2026.docx (copy).pdf");
        //We want to get the correspondingJson
        File pdfJsonFile = new File ("/home/garbi/Documents/PDF_Auto_Tagging/final_year_project.json");
        try (PDDocument pdDocument = Loader.loadPDF(pdfFile)) {
            System.out.println("Starting");

            ObjectMapper objectMapper = new ObjectMapper();
            objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

            var jsonResponse = objectMapper.readValue(pdfJsonFile, PdfExtractionResponse.class);
            var data = jsonResponse.getData();

            //We would text our cleaning capabilities

            //First we clean the pdf for any marked content
            PDFDocumentTagAndMarkedContentCleaner pdfDocumentTagAndMarkedContentCleaner =new PDFDocumentTagAndMarkedContentCleaner(pdDocument);
            pdfDocumentTagAndMarkedContentCleaner.cleanDocument();

            //We don't want an empty pages JSON and an empty Page from the Document
            if(pdDocument.getPages() == null || pdDocument.getPages().getCount() <=0) return;
            if(data.getPages() == null || data.getPages().isEmpty()) return;

            //Then we  map each pdf Page
            List<TaggingMappingEngine> listOfMappedPagesInformation = new ArrayList<>();

            for (int i = 0; i<pdDocument.getPages().getCount();i++){
                if (i >= data.getPages().size()) break;
                var jsonPage = data.getPages().get(i);
                TaggingMappingEngine taggingMappingEngine = new TaggingMappingEngine(jsonPage);
                taggingMappingEngine.setStartPage(i+1);
                taggingMappingEngine.setEndPage(i+1);
                //Then we process the text
                taggingMappingEngine.getText(pdDocument);

                listOfMappedPagesInformation.add(taggingMappingEngine);
            }

            //Then perfom our accessibiity injection of the document
            PdfAccessibilityTagInjector pdfAccessibilityTagInjector = new PdfAccessibilityTagInjector(data,pdDocument,listOfMappedPagesInformation);
            pdfAccessibilityTagInjector.transformPDFDocumentAccessibility();

            //Then we write it to a Document file

            File outputFile = new File("/home/garbi/Documents/PDF_Auto_Tagging/tagged_output.pdf");
            pdDocument.save(outputFile);
            System.out.println("SUCCESS! Tagged PDF saved to: " + outputFile.getAbsolutePath());


    }catch (IOException e) {
          e.printStackTrace();
        }


}}
