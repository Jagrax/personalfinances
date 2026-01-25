package ar.com.personalfinances.util;

import ar.com.personalfinances.service.CryptoService;

import javax.persistence.AttributeConverter;
import javax.persistence.Converter;

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