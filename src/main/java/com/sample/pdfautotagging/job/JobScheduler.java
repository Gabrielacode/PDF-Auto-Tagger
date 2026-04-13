package com.sample.pdfautotagging.job;

import com.sample.pdfautotagging.encryption.HmacUtil;
import com.sample.pdfautotagging.entities.PdfJob;
import com.sample.pdfautotagging.entities.PdfJobStatus;
import com.sample.pdfautotagging.repositories.PdfJobRepository;
import com.sample.pdfautotagging.services.PDFAccessibilityTaggingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.FileSystemResource;
import org.springframework.data.domain.Limit;
import org.springframework.http.HttpEntity;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

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

    public void queuePdfJobs(){
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
                log.error("Pool is full, leaving job {} in queue " ,job.getJobId());
                break; // Stop trying to submit the rest of the list
            }
        }

    }

    public void processPdfJob(PdfJob pdfJob){
        //We would submit the task to the executor
        Runnable runnable = () -> {
            log.info("Starting Processing for Job with Id {}",pdfJob.getJobId());
            try {
                pdfAccessibilityTaggingService.tagPdf(pdfJob);
                //After we would set it to completed in the db
                pdfJob.setJobStatus(PdfJobStatus.COMPLETED);

            }catch (Exception e){

                pdfJob.setJobStatus(PdfJobStatus.FAILED);
                pdfJob.setErrorMessage(e.getMessage());
            }finally {
                //TODO Discuss how the file will be sent to the other services
                //And whether it  will be used
                try {


                     var savedJob = pdfJobRepository.save(pdfJob);
                    //Then we will try to send  to the call back to it

                    //We want to get the token from the call back url
                    var callBackUrl = savedJob.getCallbackUrl();
                    var token =  hmacUtil.extractTokenFromUrl(callBackUrl);
                    //Then we sign the token with the timestamp
                    var timestamp = String.valueOf(System.currentTimeMillis());
                    var payload = token+"|"+timestamp;

                    var signedHeader = hmacUtil.signToken(payload);
                    //We will attach the headers for the signature and the timestamp
                    RestClient webClient = RestClient.builder().
                            baseUrl(savedJob.getCallbackUrl())
                            .build();
                    //Then the message will be
                    if(savedJob.getJobStatus() == PdfJobStatus.FAILED){


                         MultipartBodyBuilder multipartBodyBuilder = new MultipartBodyBuilder();
                         multipartBodyBuilder.part("jobId",savedJob.getJobId(), MediaType.TEXT_PLAIN);
                         multipartBodyBuilder.part("errorMessage",savedJob.getErrorMessage());

                         var multipartBody = multipartBodyBuilder.build();
                       var response =  webClient.post()
                               .header("X-Timestamp",timestamp)
                               .header("X-Signature",signedHeader)
                                .contentType(MediaType.MULTIPART_FORM_DATA)
                        .body(multipartBody);

                    }else if (savedJob.getJobStatus() == PdfJobStatus.COMPLETED){
                        MultipartBodyBuilder multipartBodyBuilder = new MultipartBodyBuilder();
                        multipartBodyBuilder.part("jobId",savedJob.getJobId(), MediaType.TEXT_PLAIN);
                        multipartBodyBuilder.part("pdfFile",new FileSystemResource(pdfJob.getOutputPdfFilePath()),MediaType.APPLICATION_PDF);

                        var multipartBody = multipartBodyBuilder.build();
                        var response =  webClient.post()
                                .header("X-Timestamp",timestamp)
                                .header("X-Signature",signedHeader)
                                .contentType(MediaType.MULTIPART_FORM_DATA)
                                .body(multipartBody)
                                .retrieve()
                                .toBodilessEntity();

                        //If the job was completed  after a successfully status code , we would delete all the files in the folder
                        if(response.getStatusCode().is2xxSuccessful()){
                             var result = pdfAccessibilityTaggingService.deleteJobFolder(savedJob.getJobId());
                             if(result){
                                 log.info(" Successfully deleted folder with Job Id {} ",savedJob.getJobId());
                             }else {
                                 log.error(" Failed to  deleted folder with Job Id {} ",savedJob.getJobId());
                             }
                        }

                    }
                } catch (Exception ignored) {

                }
                log.info("Finishing Processing for Job with Id {}",pdfJob.getJobId());
            }
        };
        threadPoolExecutor.execute(runnable);
    }
}
