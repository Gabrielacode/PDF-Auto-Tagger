package com.sample.pdfautotagging.entities;

//This would be our job entity for keeping track of processed Jobs

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@Entity
@AllArgsConstructor
@NoArgsConstructor

public class PdfJob {

    @Id
    String jobId;

    //Then the file path for the PDF and the JSON
    String pdfFilePath;
    String jsonFilePath;
    //The callback Url
    String callbackUrl;
    String outputPdfFilePath;
    //Then the status of the Job
    //We would have PENDING, PROCESSING COMPLETED,FAILED
    PdfJobStatus jobStatus;
    //And a simple error Message in case
    String errorMessage;
    boolean shouldSkipTaggedFile;
}
