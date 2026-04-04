package com.sample.pdfautotagging.repositories;

import com.sample.pdfautotagging.entities.PdfJob;
import com.sample.pdfautotagging.entities.PdfJobStatus;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface PdfJobRepository extends JpaRepository<PdfJob, UUID> {

    //We would need a small method to just get the pending jobs from the db
    @Query("SELECT job FROM PdfJob  job WHERE job.jobStatus = :status  ")
    List<PdfJob> getJobsByJobStatus(PdfJobStatus status , Limit limit);

}
