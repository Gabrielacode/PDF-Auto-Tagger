package com.sample.pdfautotagging;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sample.pdfautotagging.models.Box;
import com.sample.pdfautotagging.models.PdfExtractionResponse;
import com.sample.pdfautotagging.services.PdfAccessibilityTagInjector;
import com.sample.pdfautotagging.services.TaggingMappingEngine;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.TextPosition;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

@SpringBootApplication
public class PdfAutoTaggingApplication {

    public static void main(String[] args)  {

      //  SpringApplication.run(PdfAutoTaggingApplication.class, args)
        File pdfFile = new File("/home/garbi/Documents/PDF_Auto_Tagging/drylab.pdf");
        //We want to get the correspondingJson
        File pdfJsonFile = new File ("/home/garbi/Documents/PDF_Auto_Tagging/drylab.json");
        try (PDDocument pdDocument = Loader.loadPDF(pdfFile)) {
            System.out.println("Starting");

            ObjectMapper objectMapper = new ObjectMapper();
            objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

            var jsonResponse = objectMapper.readValue(pdfJsonFile, PdfExtractionResponse.class);
            var data = jsonResponse.getData();




            //We just want to get the first page
            //PDPage page = pdDocument.getPage(0);
            TaggingMappingEngine taggingMappingEngine = new TaggingMappingEngine(data.getPages().get(0));
            taggingMappingEngine.setStartPage(1);
            taggingMappingEngine.setEndPage(1);
            taggingMappingEngine.getText(pdDocument);
            System.out.println("Pass I Complete");

            //Now we have matched the Operators to the appropraite Boxes , we will inject the accessibility tags

            PdfAccessibilityTagInjector injector = new PdfAccessibilityTagInjector(
                    data.getPages().get(0), pdDocument, taggingMappingEngine.listOfPdfTextBlocksInThePage);
            injector.initialize();

            List<Object> modifiedTokens = injector.injectAccessibilityTokensForText();
            System.out.println("Injection of Accessibility Tokens Done");


            //We have then injected the accessibility tokens into the PdF Stream
            //We would need to construct a Structure Tree to tell the pdf document , that we have accessibility tags at those locations we put the MCID tags
            //And write the content to the document

            injector.writeToPdfPage(pdDocument,0,modifiedTokens);
            //Then write it
            //Then we arrange the structure tree
            injector.buildStructureTree();
            System.out.println("Pass 3 Complete!");
            // ==========================================
            // SAVE THE PDF
            // ==========================================
            File outputFile = new File("/home/garbi/Documents/PDF_Auto_Tagging/Tagged_Output.pdf");
            pdDocument.save(outputFile);
            System.out.println("Successfully saved to: " + outputFile.getAbsolutePath());



        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    static void drawTextDotOnPositionOnImage(Graphics2D imageGraphics, TextPosition textPosition){
        //We want to show show a small dot on the textposition
        var x = Math.round(textPosition.getX());
        var y = Math.round(textPosition.getY());
        var height = Math.round(textPosition.getHeight());
        var width = Math.round(textPosition.getWidth());

        float x_Cord = textPosition.getXDirAdj();
        float y_Cord = textPosition.getYDirAdj() - textPosition.getHeightDir();
        float w = textPosition.getWidthDirAdj();
        float h = textPosition.getHeightDir();

        imageGraphics.setColor(Color.RED);
        imageGraphics.draw3DRect(Math.round(x_Cord), Math.round(y_Cord), Math.round(w), Math.round(h),false);

    }

}
