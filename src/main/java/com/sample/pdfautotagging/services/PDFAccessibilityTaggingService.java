package com.sample.pdfautotagging.services;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sample.pdfautotagging.error.ErrorResponse;
import com.sample.pdfautotagging.models.json.PdfData;
import com.sample.pdfautotagging.models.json.PdfExtractionResponse;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.documentinterchange.logicalstructure.PDMarkInfo;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
public class PDFAccessibilityTaggingService {
    /**
     * This is the main service that connects all the other services
     */


    public ResponseEntity<?> tagPdf(MultipartFile pdfFile , MultipartFile jsonFile, boolean shouldSkipMarkedFiles){
        //We would need to make some checks first
        //1 Is the Json File or Pdf File Empty
        if(pdfFile.isEmpty() || jsonFile.isEmpty()){

            return ResponseEntity.badRequest().
                    body(new ErrorResponse("PDF File or JSON File is empty", HttpStatus.BAD_REQUEST.value()));
        }

        try (PDDocument pdfDocument = Loader.loadPDF(pdfFile.getBytes())) {
            // If should skip marked files is true
            if (shouldSkipMarkedFiles) {
                PDMarkInfo markInfo = pdfDocument.getDocumentCatalog().getMarkInfo();
                if (markInfo != null && markInfo.isMarked()) {
                    // PDF is already tagged. Return it exactly as it came in.
                    pdfDocument.close();
                    //Return the file
                    return ResponseEntity.ok()
                            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"already_tagged_" + pdfFile.getOriginalFilename() + "\"")
                            .contentType(MediaType.APPLICATION_PDF)
                            .body(pdfFile.getBytes());
                }
            }

            //If not we will
            //First parse the Json into its appropriate Model Object
            ObjectMapper objectMapper = new ObjectMapper();
            objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

            PdfExtractionResponse jsonResponse = objectMapper.readValue(jsonFile.getInputStream(), PdfExtractionResponse.class);
            PdfData data = jsonResponse.getData();

            //We would need to clean the document for any tags or others ,so we have a clean slate

            PDFDocumentTagAndMarkedContentCleaner pdfDocumentTagAndMarkedContentCleaner =new PDFDocumentTagAndMarkedContentCleaner(pdfDocument);
            pdfDocumentTagAndMarkedContentCleaner.cleanDocument();
            //We don't want an empty pages JSON and an empty Page from the Document
            if(pdfDocument.getPages() == null || pdfDocument.getPages().getCount() <=0){
                 return ResponseEntity.badRequest().
                        body(new ErrorResponse("PDF File doesn't have Pages ", HttpStatus.BAD_REQUEST.value()));
            }
            if(data.getPages() == null || data.getPages().isEmpty()) {
                return ResponseEntity.badRequest().
                        body(new ErrorResponse(" Json File doesn't have Pages corresponding to PDF Pages ", HttpStatus.BAD_REQUEST.value()));
            }

            //Then we  map each pdf Page
            //Getting all the text boxes and text lines
            List<TaggingMappingEngine> listOfMappedPagesInformation = new ArrayList<>();

            for (int i = 0; i<pdfDocument.getPages().getCount();i++){
                if (i >= data.getPages().size()) break;
                var jsonPage = data.getPages().get(i);
                TaggingMappingEngine taggingMappingEngine = new TaggingMappingEngine(jsonPage);
                taggingMappingEngine.setStartPage(i+1);
                taggingMappingEngine.setEndPage(i+1);
                //Then we process the text
                taggingMappingEngine.getText(pdfDocument);

                listOfMappedPagesInformation.add(taggingMappingEngine);
            }

            //Then perform our accessibility injection of the document
            //Putting the necessary Structure Elements on teh Document
            PdfAccessibilityTagInjector pdfAccessibilityTagInjector = new PdfAccessibilityTagInjector(data,pdfDocument,listOfMappedPagesInformation);
            pdfAccessibilityTagInjector.transformPDFDocumentAccessibility();

            //Then we save the new PDF Document to an Resource

            ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();

            pdfDocument.save(byteArrayOutputStream);

           // Return it to the client as a downloadable file
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"tagged_" + pdfFile.getOriginalFilename() + "\"")
                    .contentType(MediaType.APPLICATION_PDF)
                    .body(byteArrayOutputStream.toByteArray());


        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
