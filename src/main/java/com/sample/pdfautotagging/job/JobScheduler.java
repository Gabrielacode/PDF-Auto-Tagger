package com.sample.pdfautotagging.job;

import com.sample.pdfautotagging.encryption.HmacUtil;
import com.sample.pdfautotagging.entities.PdfJob;
import com.sample.pdfautotagging.entities.PdfJobStatus;
import com.sample.pdfautotagging.error.CustomException;
import com.sample.pdfautotagging.repositories.PdfJobRepository;
import com.sample.pdfautotagging.services.PDFAccessibilityTaggingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.FileSystemResource;
import org.springframework.data.domain.Limit;
import org.springframework.http.*;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

@Component
@Slf4j

public class JobScheduler {
    private final PDFAccessibilityTaggingService pdfAccessibilityTaggingService;
    private final PdfJobRepository pdfJobRepository;
    private final HmacUtil hmacUtil;


    private final Executor threadPoolExecutor;

    public JobScheduler(PDFAccessibilityTaggingService pdfAccessibilityTaggingService, PdfJobRepository pdfJobRepository, HmacUtil hmacUtil, @Qualifier("pdfJobQueueExecutor") Executor threadPoolExecutor) {
        this.pdfAccessibilityTaggingService = pdfAccessibilityTaggingService;
        this.pdfJobRepository = pdfJobRepository;
        this.hmacUtil = hmacUtil;
        this.threadPoolExecutor = threadPoolExecutor;
    }

    //We would just have a Scheduler that will run after  the current jobs are done
    @Scheduled(fixedDelay = 3000)

    public void queuePdfJobs() {
        //Its job is to get pending jobs in the db and send them to be processed
        //If the queue we would not mark it

        List<PdfJob> pendingJobs = pdfJobRepository.getJobsByJobStatus(PdfJobStatus.PENDING, Limit.of(2)); // Try to grab up to 3 jobs

        for (PdfJob job : pendingJobs) {
            try {
                //Update the state
                job.setJobStatus(PdfJobStatus.PROCESSING);
                pdfJobRepository.save(job);
                // Attempt to hand it to the Thread Pool
                processPdfJob(job);


            } catch (RejectedExecutionException e) {
                //If we failed ,we set it back to Pending
                job.setJobStatus(PdfJobStatus.PENDING);
                pdfJobRepository.save(job);
                // The pool is full
                // We just catch the error and do nothing.
                // The job remains 'PENDING' in the DB and will be picked up on the next cycle.
                log.error("Pool is full, leaving job {} in queue ", job.getJobId());
                break; // Stop trying to submit the rest of the list
            }
        }

    }

    public void processPdfJob(PdfJob pdfJob) {
        //We would submit the task to the executor
        Runnable runnable = () -> {

            //We want to set the MDC
            MDC.put("jobId", "[Job Id : " + pdfJob.getJobId() + "] ");
            log.info("Starting Processing for Job with Id {}", pdfJob.getJobId());
            try {
                pdfAccessibilityTaggingService.tagPdf(pdfJob);
                //After we would set it to completed in the db
                pdfJob.setJobStatus(PdfJobStatus.COMPLETED);

            } catch (Exception e) {

                //So first we would check for if the  Retry Count  is more than 3
                //If it is not  up to 3 or more , we will set it to be pending so the Job rescheduled again and increase the retry count
                int currentJobRetryCount = pdfJob.getRetryCount();
                if (currentJobRetryCount < 3) {
                    pdfJob.setRetryCount(currentJobRetryCount + 1);
                    pdfJob.setJobStatus(PdfJobStatus.PENDING);
                    var savedPdfJob = pdfJobRepository.save(pdfJob);

                    //Then we log the error and the retry count of the job
                    log.error("Failed to process Job  with Id {} with error {}, Retrying for the {} time ", savedPdfJob.getJobId(), e.getMessage(), savedPdfJob.getRetryCount());
                } else {
                    pdfJob.setJobStatus(PdfJobStatus.FAILED);

                    if (e instanceof CustomException) {
                        log.error("Error occurred In Job Processing ", e);
                        pdfJob.setErrorMessage(e.getMessage());
                    } else {
                        //We just want to create a new Custom Exception and get the error message
                        CustomException unknownErrorException = new CustomException("An error occurred while trying to remediate your PDF,Kindly try again or contact support", pdfJob.getJobId(), HttpStatus.INTERNAL_SERVER_ERROR, "internal-error", e);

                        //Then if the retry failed , we would then save the error message and log it so that the developer we would know what wrong  ,

                        log.error("Error occurred In Job Processing ", unknownErrorException.getCause());
                        pdfJob.setErrorMessage(unknownErrorException.getMessage());
                    }
                }


                }finally{
                    //TODO Discuss how the file will be sent to the other services
                    //And whether it  will be used
                //We will save to the db then send the request and retry

                try {
                    pdfJobRepository.save(pdfJob);
                    pdfAccessibilityTaggingService.sendRequest(pdfJob);
                } catch (NoSuchAlgorithmException | InvalidKeyException e) {
                    log.error("Failed to generate X- Signature Header  ",e);
                } catch (Exception e) {
                   log.error("Failed to clean up Job Processing  ",e);
                }
                log.info("Finishing Processing for Job with Id {}", pdfJob.getJobId());
                }

            //Then we remove the MDC
            MDC.remove("jobId");
            } ;
            threadPoolExecutor.execute(runnable);
        }

        //We want to retryable from the network error and 401 error and 422  for 3 times
    //And if the HMAC fails from signing




    }

