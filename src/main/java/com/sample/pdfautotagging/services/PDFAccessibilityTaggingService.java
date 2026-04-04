package com.sample.pdfautotagging.services;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sample.pdfautotagging.entities.PdfJob;
import com.sample.pdfautotagging.entities.PdfJobStatus;
import com.sample.pdfautotagging.error.ErrorResponse;
import com.sample.pdfautotagging.models.json.PdfData;
import com.sample.pdfautotagging.models.json.PdfExtractionResponse;
import com.sample.pdfautotagging.repositories.PdfJobRepository;


import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.io.MemoryUsageSetting;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.documentinterchange.logicalstructure.PDMarkInfo;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.nio.file.CopyOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.*;

@Slf4j
@Service
public class PDFAccessibilityTaggingService {
    /**
     * This is the main service that connects all the other services
     */

    private final PdfJobRepository pdfJobRepository;

    private final String jobDownloadFolder;

    public PDFAccessibilityTaggingService(PdfJobRepository pdfJobRepository, @Value("${JOB_DOWNLOAD_FOLDER}") String jobDownloadFolder) {
        this.pdfJobRepository = pdfJobRepository;
        this.jobDownloadFolder = jobDownloadFolder;
    }

    public void  saveToFilePath(Path outputPath , InputStream inputStream) throws IOException {
        if(!Files.exists(outputPath)){
            Files.createFile(outputPath);
        }
        Files.copy(inputStream, outputPath, StandardCopyOption.REPLACE_EXISTING);
    }
    public ResponseEntity<?> registerPdfJob(MultipartFile pdfFile , MultipartFile jsonFile, boolean shouldSkipMarkedFiles){
        //Here we would want to download the files into  the service file system
        //After generating the job id
        UUID jobId = UUID.randomUUID();
        //The file path is {jobDownloadFolder}/jobs/{idOfJob}/input/
        Path jobDownloadPath = Path.of(jobDownloadFolder, "jobs", jobId.toString(), "input");
        Path pdfFilePath = jobDownloadPath.resolve("inputPdf.pdf");
        Path jsonFilePath = jobDownloadPath.resolve("inputJson.json");

        try {
            //We would then ensure the job download directory is created
            if (!Files.exists(jobDownloadPath)) {
                Files.createDirectory(jobDownloadPath);
            }
            saveToFilePath(pdfFilePath,pdfFile.getInputStream());
            saveToFilePath(jsonFilePath,jsonFile.getInputStream());


            //Then we would save the Job to the DB
            PdfJob pdfJob = new PdfJob();
            pdfJob.setJobId(jobId.toString());
            pdfJob.setPdfFilePath(pdfFilePath.toString());
            pdfJob.setJsonFilePath(jsonFilePath.toString());
            pdfJob.setJobStatus(PdfJobStatus.PENDING);
            pdfJob.setShouldSkipTaggedFile(shouldSkipMarkedFiles);

            pdfJobRepository.save(pdfJob);
            //Then return the Job Id
            Map<String ,Object> newResultHashMap = new HashMap<>();
            newResultHashMap.put("jobId",jobId.toString());


            return  ResponseEntity.ok(newResultHashMap);
        } catch (Exception e) {

            //If it fails we would have to quicly delete the directory to avoid stale files that have no job in the DB
            try{
                Files.deleteIfExists(jobDownloadPath);
            } catch (Exception ignored) {

            }

            //We just construct a simple Map and return it
           var errorResponse = new ErrorResponse(e.getMessage(),500);
            return  ResponseEntity.internalServerError().body(errorResponse);

        }

    }

    //We now going to change this to take in a job

    public void  tagPdf(PdfJob pdfJob) throws Exception {
//Tells PDFBox: "Use RAM for the DOM, but offload the heavy byte streams to a temp file"
        MemoryUsageSetting memoryConfig = MemoryUsageSetting.setupMixed(120 * 1024 * 1024); // 120MB RAM limit per file
        //We would have to first download the files from the appropraite files
        Path pdfFilePath =Path.of(pdfJob.getPdfFilePath());
        Path jsonFilePath = Path.of(pdfJob.getJsonFilePath());
        Path jobOutputDownloadPath = Path.of(jobDownloadFolder, "jobs", pdfJob.getJobId(), "output");
        Path pdfOutputPath = jobOutputDownloadPath.resolve("outputPdf.pdf");

        //We would just create the directory
        if(Files.exists(jobOutputDownloadPath)){
            Files.createDirectory(jobOutputDownloadPath);
        }

        //Convert them to thier appropraite files
        File pdfFile = pdfFilePath.toFile();
        File jsonFile = jsonFilePath.toFile();

        //We would need to make some checks first
        //1 Is the Json File or Pdf File Empty
        if(!pdfFile.exists() || !jsonFile.exists()){
         //We throw an exception
            throw  new RuntimeException("Pdf Or Json Files Dont Exist");
        }



        try (PDDocument pdfDocument = Loader.loadPDF(pdfFile,memoryConfig.streamCache)) {
            // If should skip marked files is true
            if (pdfJob.isShouldSkipTaggedFile()) {
                PDMarkInfo markInfo = pdfDocument.getDocumentCatalog().getMarkInfo();
                if (markInfo != null && markInfo.isMarked()) {
                    // PDF is already tagged. Return it exactly as it came in.
                   // We would save it back to the file system as the output


                    saveToFilePath(pdfOutputPath, Files.newInputStream(pdfFile.toPath()));

                }
            }

            //If not we will
            //First parse the Json into its appropriate Model Object
            ObjectMapper objectMapper = new ObjectMapper();
            objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

            PdfExtractionResponse jsonResponse = objectMapper.readValue(jsonFile, PdfExtractionResponse.class);
            PdfData data = jsonResponse.getData();

            //We would need to clean the document for any tags or others ,so we have a clean slate

            PDFDocumentTagAndMarkedContentCleaner pdfDocumentTagAndMarkedContentCleaner =new PDFDocumentTagAndMarkedContentCleaner(pdfDocument);
            pdfDocumentTagAndMarkedContentCleaner.cleanDocument();
            //We don't want an empty pages JSON and an empty Page from the Document
            if(pdfDocument.getPages() == null || pdfDocument.getPages().getCount() <=0){
                 throw  new IllegalStateException("PDF File doesn't have pages");
            }
            if(data.getPages() == null || data.getPages().isEmpty()) {
                throw  new IllegalStateException("Json File doesn't have Pages corresponding to PDF Pages");
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

            //Then we save the new PDF Document to  the file

             pdfDocument.save(pdfOutputPath.toAbsolutePath().toString());


        }
    }
}
