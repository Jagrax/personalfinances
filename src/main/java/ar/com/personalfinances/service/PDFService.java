package ar.com.personalfinances.service;

import org.springframework.web.multipart.MultipartFile;

public interface PDFService {

    String extractText(MultipartFile file) throws Exception;
}
