package ar.com.personalfinances.util;

import ar.com.personalfinances.service.CryptoService;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter
public class CryptoConverter implements AttributeConverter<String, String> {

    @Override
    public String convertToDatabaseColumn(String raw) {
        return CryptoService.encrypt(raw);
    }

    @Override
    public String convertToEntityAttribute(String enc) {
        return CryptoService.decrypt(enc);
    }
}