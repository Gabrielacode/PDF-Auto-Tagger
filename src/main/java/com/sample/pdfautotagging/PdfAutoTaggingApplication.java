package com.sample.pdfautotagging;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sample.pdfautotagging.models.json.PdfExtractionResponse;
import com.sample.pdfautotagging.services.PDFDocumentTagAndMarkedContentCleaner;
import com.sample.pdfautotagging.services.PdfAccessibilityTagInjector;
import com.sample.pdfautotagging.services.TaggingMappingEngine;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@SpringBootApplication
public class PdfAutoTaggingApplication {

    public static void main(String[] args)  {

       SpringApplication.run(PdfAutoTaggingApplication.class, args);

}}
