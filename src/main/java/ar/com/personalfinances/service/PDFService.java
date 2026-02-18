package ar.com.personalfinances.service;

import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

public interface PDFService {

    String extractText(MultipartFile file) throws IOException;

    String extractText(byte[] pdfBytes) throws IOException;
}
