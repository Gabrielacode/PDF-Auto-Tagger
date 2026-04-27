package com.sample.pdfautotagging.services;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sample.pdfautotagging.encryption.HmacUtil;
import com.sample.pdfautotagging.entities.PdfJob;
import com.sample.pdfautotagging.entities.PdfJobStatus;
import com.sample.pdfautotagging.error.CustomException;
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
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.*;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.util.FileSystemUtils;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.nio.file.CopyOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.*;

@Slf4j
@Service
public class PDFAccessibilityTaggingService {
    /**
     * This is the main service that connects all the other services
     */

    private final PdfJobRepository pdfJobRepository;

    private final String jobDownloadFolder;
    private final HmacUtil hmacUtil;

    public PDFAccessibilityTaggingService(PdfJobRepository pdfJobRepository, @Value("${JOB_DOWNLOAD_FOLDER}") String jobDownloadFolder, HmacUtil hmacUtil) {
        this.pdfJobRepository = pdfJobRepository;
        this.jobDownloadFolder = jobDownloadFolder;
        this.hmacUtil = hmacUtil;
    }

    public void  saveToFilePath(Path outputPath , InputStream inputStream) throws IOException {
        if(!Files.exists(outputPath)){
            Files.createFile(outputPath);
        }
        Files.copy(inputStream, outputPath, StandardCopyOption.REPLACE_EXISTING);
    }
    public ResponseEntity<?> registerPdfJob(MultipartFile pdfFile , MultipartFile jsonFile, boolean shouldSkipMarkedFiles, String callbackUrl){
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
                Files.createDirectories(jobDownloadPath);
            }
            saveToFilePath(pdfFilePath,pdfFile.getInputStream());
            saveToFilePath(jsonFilePath,jsonFile.getInputStream());


            //Then we would save the Job to the DB
            PdfJob pdfJob = new PdfJob();
            pdfJob.setJobId(jobId.toString());
            pdfJob.setPdfFilePath(pdfFilePath.toString());
            pdfJob.setJsonFilePath(jsonFilePath.toString());
            pdfJob.setJobStatus(PdfJobStatus.PENDING);
            pdfJob.setCallbackUrl(callbackUrl);
            pdfJob.setShouldSkipTaggedFile(shouldSkipMarkedFiles);

            pdfJobRepository.save(pdfJob);
            //Then return the Job Id
            Map<String ,Object> newResultHashMap = new HashMap<>();
            newResultHashMap.put("jobId",jobId.toString());


            return  ResponseEntity.ok(newResultHashMap);
        } catch (Exception e) {

            log.error("Error from Job Processing {}",e.getMessage(),e);
            //If it fails we would have to quicly delete the directory to avoid stale files that have no job in the DB
            try{

                deleteJobFolder(jobId.toString());
            } catch (Exception ignored) {

            }

            //We just construct a simple Map and return it
           var errorResponse = new ErrorResponse(e.getMessage(),500);
            return  ResponseEntity.internalServerError().body(errorResponse);

        }

    }

    public boolean deleteJobFolder (String jobId){
        try{
            Path jobDownloadPath = Path.of(jobDownloadFolder, "jobs", jobId);
            //We are expecting this to be a directory

             var isDeleted =FileSystemUtils.deleteRecursively(jobDownloadPath);
             return isDeleted;
        } catch (IOException e) {
            log.error("Failed to delete Job Folder for Job Id {}", jobId);
        }
        return false;
    }
    //We now going to change this to take in a job



    @Retryable(
            retryFor = {
                    HttpClientErrorException.Unauthorized.class, HttpClientErrorException.UnprocessableEntity.class,
                    ResourceAccessException.class,
                    NoSuchAlgorithmException.class, InvalidKeyException.class
            },
            maxAttempts = 3,
            backoff = @Backoff(delay = 5000),
            recover ="recoverFailure"
    )
    public void sendRequest(PdfJob savedJob) throws NoSuchAlgorithmException, InvalidKeyException {
        //We want to get the token from the call back url
        var callBackUrl = savedJob.getCallbackUrl();
        var token = hmacUtil.extractTokenFromUrl(callBackUrl);
        //Then we sign the token with the timestamp
        var timestamp = String.valueOf(System.currentTimeMillis());
        var payload = token + "|" + timestamp;

        var signedHeader = hmacUtil.signToken(payload);
        //We will attach the headers for the signature and the timestamp
        RestClient webClient = RestClient.builder().
                baseUrl(savedJob.getCallbackUrl())
                .build();
        //Then the message will be

        ResponseEntity<Void> response;
        if (savedJob.getJobStatus() == PdfJobStatus.FAILED) {
            MultiValueMap<String,Object> multipartBody = new LinkedMultiValueMap<>();
            multipartBody.add("jobId",savedJob.getJobId());
            multipartBody.add("errorMessage",savedJob.getErrorMessage());

            response = webClient.post()
                    .header("X-Timestamp", timestamp)
                    .header("X-Signature", signedHeader)
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(multipartBody)
                    .retrieve()
                    .toBodilessEntity();

        }
        else if (savedJob.getJobStatus() == PdfJobStatus.COMPLETED) {
            MultiValueMap<String,Object> multipartBody = new LinkedMultiValueMap<>();

            multipartBody.add("jobId", savedJob.getJobId());
            multipartBody.add("pdfFile", new FileSystemResource(savedJob.getOutputPdfFilePath()));


            response = webClient.post()
                    .header("X-Timestamp", timestamp)
                    .header("X-Signature", signedHeader)
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(multipartBody)
                    .retrieve()
                    .toBodilessEntity();
        }
        else {
            response = null;
        }
        if(response == null) return;

        //Then we want to check , if the response good is 200 , we proceed to clear the folder
        //If the job was completed  after a successfully status code , we would delete all the files in the folder
        if (response.getStatusCode().is2xxSuccessful()) {
            var result = deleteJobFolder(savedJob.getJobId());
            if (result) {
                log.info(" Successfully deleted folder with Job Id {} ", savedJob.getJobId());
            } else {
                log.error(" Failed to  deleted folder with Job Id {} ", savedJob.getJobId());
            }
        }else if(response.getStatusCode().isSameCodeAs(HttpStatusCode.valueOf(404))){

            //We also abort
            var result = deleteJobFolder(savedJob.getJobId());
            if (result) {
                log.info(" Successfully deleted folder with Job Id {} ", savedJob.getJobId());
            } else {
                log.error(" Failed to  deleted folder with Job Id {} ", savedJob.getJobId());
            }
        }
    }

    //Then our recover method will be

    @Recover()
    public void recoverFailure(Exception exception , PdfJob pdfJob){
        log.error("Failed to send info to the Main Service ",exception);
        deleteJobFolder(pdfJob.getJobId());
    }
        //If the retry fails we log it and just delete the folder



    public void  tagPdf(PdfJob pdfJob) throws Exception {
//Tells PDFBox: "Use RAM for the DOM, but offload the heavy byte streams to a temp file"
        MemoryUsageSetting memoryConfig = MemoryUsageSetting.setupMixed(120 * 1024 * 1024); // 120MB RAM limit per file
        //We would have to first download the files from the appropraite files
        Path pdfFilePath =Path.of(pdfJob.getPdfFilePath());
        Path jsonFilePath = Path.of(pdfJob.getJsonFilePath());
        Path jobOutputDownloadPath = Path.of(jobDownloadFolder, "jobs", pdfJob.getJobId(), "output");
        Path pdfOutputPath = jobOutputDownloadPath.resolve("outputPdf.pdf");

        //We would just create the directory
        if(!Files.exists(jobOutputDownloadPath)){
            Files.createDirectories(jobOutputDownloadPath);
        }

        //Convert them to thier appropraite files
        File pdfFile = pdfFilePath.toFile();
        File jsonFile = jsonFilePath.toFile();

        //We would need to make some checks first
        //1 Is the Json File or Pdf File Empty
        if(!pdfFile.exists()){

            log.error("Pdf File Doesnt Not Exist or Is Empty");
            throw new  CustomException("Pdf File is Empty or doesn't exist",pdfJob.getJobId(),HttpStatus.BAD_REQUEST,"pdf-file-not-exist",null);
        }
        if(!pdfFile.exists() || !jsonFile.exists()){
         //We throw an exception
            log.error("Json File Doesnt Not Exist or Is Empty");
            throw new  CustomException("An error occurred while trying to remediate your PDF,Kindly try again or contact support ",pdfJob.getJobId(),HttpStatus.INTERNAL_SERVER_ERROR,"json-file-not-exist",null);

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

            PdfData data;
            try {
                PdfExtractionResponse jsonResponse = objectMapper.readValue(jsonFile, PdfExtractionResponse.class);
                 data = jsonResponse.getData();
            } catch (Exception e) {
                log.error("Failed to process Json for this Job",e);
                throw new  CustomException("An error occurred while trying to remediate your PDF,Kindly try again or contact support ",pdfJob.getJobId(),HttpStatus.INTERNAL_SERVER_ERROR,"json-file-failed-to-deserialize",e);
            }

            //If null we remove or stop from here
            if(data == null){
                log.error("Failed to process Json for this Job");
                throw new  CustomException("An error occurred while trying to remediate your PDF,Kindly try again or contact support ",pdfJob.getJobId(),HttpStatus.INTERNAL_SERVER_ERROR,"json-file-failed-to-deserialize",null);
            }


            //We would need to clean the document for any tags or others ,so we have a clean slate

            PDFDocumentTagAndMarkedContentCleaner pdfDocumentTagAndMarkedContentCleaner =new PDFDocumentTagAndMarkedContentCleaner(pdfDocument);
            pdfDocumentTagAndMarkedContentCleaner.cleanDocument();
            //We don't want an empty pages JSON and an empty Page from the Document
            if(pdfDocument.getPages() == null || pdfDocument.getPages().getCount() <=0){
//                 throw  new IllegalStateException("PDF File doesn't have pages");
                log.error("PDF File doesn't have pages");
                throw new  CustomException("PDF File has no pages , Please confirm the PDF file is in order",pdfJob.getJobId(),HttpStatus.BAD_REQUEST,"pdf-file-no-pages",null);
            }
            if(data.getPages() == null || data.getPages().isEmpty()) {
                log.error("Json File doesn't have Pages corresponding to PDF Pages, Confirm");
                throw new  CustomException("An error occurred while trying to remediate your PDF,Kindly try again or contact support ",pdfJob.getJobId(),HttpStatus.INTERNAL_SERVER_ERROR,"json-file-page-mismatch-pdf-file-page",null);
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
            PdfAccessibilityTagInjector pdfAccessibilityTagInjector = new PdfAccessibilityTagInjector(data,pdfDocument,listOfMappedPagesInformation,pdfJob);
            pdfAccessibilityTagInjector.transformPDFDocumentAccessibility();

            //Then we save the new PDF Document to  the file

             pdfDocument.save(pdfOutputPath.toAbsolutePath().toString());

             //Then we would set the path to it
            pdfJob.setOutputPdfFilePath(pdfOutputPath.toAbsolutePath().toString());


        }
    }
}
