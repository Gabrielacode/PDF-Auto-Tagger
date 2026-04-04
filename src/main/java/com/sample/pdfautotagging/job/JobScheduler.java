package com.sample.pdfautotagging.job;

import com.sample.pdfautotagging.entities.PdfJob;
import com.sample.pdfautotagging.entities.PdfJobStatus;
import com.sample.pdfautotagging.repositories.PdfJobRepository;
import com.sample.pdfautotagging.services.PDFAccessibilityTaggingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.Limit;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

@Component
@Slf4j

public class JobScheduler {
    private final PDFAccessibilityTaggingService pdfAccessibilityTaggingService;
    private final PdfJobRepository pdfJobRepository;


    private final Executor threadPoolExecutor;

    public JobScheduler(PDFAccessibilityTaggingService pdfAccessibilityTaggingService, PdfJobRepository pdfJobRepository, @Qualifier("pdfJobQueueExecutor") Executor threadPoolExecutor) {
        this.pdfAccessibilityTaggingService = pdfAccessibilityTaggingService;
        this.pdfJobRepository = pdfJobRepository;
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
                    pdfJobRepository.save(pdfJob);
                } catch (Exception ignored) {

                }
            }
        };
        threadPoolExecutor.execute(runnable);
    }
}
