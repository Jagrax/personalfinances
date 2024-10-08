package ar.com.personalfinances.util;

import ar.com.personalfinances.entity.User;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.Hibernate;
import org.springframework.beans.BeanUtils;
import org.springframework.security.core.context.SecurityContextHolder;

import java.lang.reflect.Field;
import java.util.*;

@Slf4j
public class ApplicationUtils {

    public static String getChangeLog(Object o, Object original) {
        Class<?> clazz = o.getClass();
        Field[] fields = clazz.getDeclaredFields();
        List<String> changeLog = new ArrayList<>();

        // Me aseguro de tener el objeto real y no un Hibernate Proxy en la mano
        o = Hibernate.unproxy(o);
        original = Hibernate.unproxy(original);

        try {
            for (Field field : fields) {
                field.setAccessible(true);
                Object valorNuevo = field.get(o);
                Object valorOriginal = field.get(original);

                try {
                    // Me aseguro de tener el objeto real y no un Hibernate Proxy en la mano
                    valorOriginal = Hibernate.unproxy(valorOriginal);
                    valorNuevo = Hibernate.unproxy(valorNuevo);
                    // Intento obtener el atributo id si lo tiene
                    Field originalId = valorOriginal != null ? valorOriginal.getClass().getDeclaredField("id") : null;
                    Field nuevoId = valorNuevo != null ? valorNuevo.getClass().getDeclaredField("id") : null;
                    if (originalId != null) {
                        originalId.setAccessible(true);
                        valorOriginal = originalId.get(valorOriginal);
                    }
                    if (nuevoId != null) {
                        nuevoId.setAccessible(true);
                        valorNuevo = nuevoId.get(valorNuevo);
                    }
                } catch (NoSuchFieldException e) {
                }

                if (!Objects.equals(valorNuevo, valorOriginal)) {
                    // Caso particular, si edito los miembros de un grupo o de un gasto, tendria que tener la lista como IDs, pero es medio incomprensible de leer a nivel AlertEvent. Por ahora dejamos anotado como cambio el size de la List
                    if (valorOriginal instanceof List && valorNuevo instanceof List) {
                        valorOriginal = "size=" + ((List<?>) valorOriginal).size();
                        valorNuevo = "size=" + ((List<?>) valorNuevo).size();
                    }
                    changeLog.add(field.getName() + "[" + valorOriginal + "] -> [" + valorNuevo + "]");
                }
            }
        } catch (IllegalAccessException e) {
            log.error("[getChangeLog] Se produjo un error al intentar comparar los campos de [" + o + "] y [" + original + "]", e);
        }

        return String.join(", ", changeLog);
    }

    public static <T> T cloneEntity(T entity, boolean setIdAttributeToNullIfPresent) {
        if (entity == null) {
            return null;
        }

        try {
            Class<?> entityClass = entity.getClass();
            T clonedEntity = (T) entityClass.newInstance();

            BeanUtils.copyProperties(entity, clonedEntity);

            if (setIdAttributeToNullIfPresent) {
                Arrays.stream(clonedEntity.getClass().getDeclaredFields())
                        .filter(field -> field.getName().equals("id"))
                        .findFirst()
                        .ifPresent(idField -> {
                            idField.setAccessible(true);
                            try {
                                idField.set(clonedEntity, null);
                            } catch (IllegalAccessException e) {
                                throw new RuntimeException("Se produjo un error al intentar establecer en null el atributo [id] de la entidad clonada. " + clonedEntity, e);
                            }
                        });
            }
            return clonedEntity;
        } catch (InstantiationException | IllegalAccessException e) {
            throw new RuntimeException("Error al clonar la entidad", e);
        }
    }

    public static User getUserFromSession() {
        return getUserFromSession(true);
    }

    public static User getUserFromSession(boolean throwExceptionOnNull) {
        // Intento recuperar el Usuario logueado
        User user = (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (user == null && throwExceptionOnNull) {
            throw new RuntimeException("No se pudo recuperar el usuario asociado a authentication.principal");
        }

        return user;
    }

    public static String getCacheSafeValue() {
        long ts = System.currentTimeMillis();
        return Long.toString(ts, Character.MAX_RADIX);
    }
}