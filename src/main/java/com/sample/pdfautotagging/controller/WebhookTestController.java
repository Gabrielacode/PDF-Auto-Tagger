package com.sample.pdfautotagging.controller;

//Pls this is to test the web hook , this is not an endpoint
//Take Note
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@RestController
@RequestMapping("/api/v1/test-webhook")
public class WebhookTestController {

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<String> receiveTestWebhook(
            @RequestHeader(value = "X-Timestamp", required = false) String timestamp,
            @RequestHeader(value = "X-Signature", required = false) String signature,
            @RequestParam("jobId") String jobId,
            @RequestParam(value = "pdfFile", required = false) MultipartFile pdfFile,
            @RequestParam(value = "errorMessage", required = false) String errorMessage) {

        log.info("\n================ WEBHOOK RECEIVED ================");
        log.info("Job ID: {}", jobId);
        log.info("X-Timestamp: {}", timestamp);
        log.info("X-Signature: {}", signature);

        if (pdfFile != null) {
            log.info("Job Status: SUCCESS");
            log.info("Received File: {} (Size: {} bytes)", pdfFile.getOriginalFilename(), pdfFile.getSize());
        } else if (errorMessage != null) {
            log.info("Job Status: FAILED");
            log.info("Error Message: {}", errorMessage);
        } else {
            log.warn("Received webhook with no file and no error message!");
        }
        
        log.info("==================================================\n");

        return ResponseEntity.ok("Webhook processed successfully!");
    }
}