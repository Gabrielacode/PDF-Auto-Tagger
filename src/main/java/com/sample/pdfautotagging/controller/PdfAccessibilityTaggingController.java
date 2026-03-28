package com.sample.pdfautotagging.controller;

import com.sample.pdfautotagging.services.PDFAccessibilityTaggingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@RestController
@RequestMapping("/api/v1/accessibility")
@RequiredArgsConstructor
public class PdfAccessibilityTaggingController {
    private final PDFAccessibilityTaggingService pdfAccessibilityTaggingService;


    @PostMapping(value = "/tag-pdf", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> tagPdf(
            @RequestParam("pdfFile") MultipartFile pdfFile,
            @RequestParam("jsonFile") MultipartFile jsonFile,
            @RequestParam(value = "skipMarkedFiles", defaultValue = "false") boolean skipMarkedFiles
    ){
        log.info("Request came through");

        var result =  pdfAccessibilityTaggingService.tagPdf(pdfFile,jsonFile,skipMarkedFiles);
        log.info("Request Ended");
        return  result;
    }

}
