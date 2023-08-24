package ar.com.personalfinances.util;

import ar.com.personalfinances.entity.User;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.security.core.context.SecurityContextHolder;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Slf4j
public class ApplicationUtils {

    public static String getChangeLog(Object o, Object original) {
        Class<?> clazz = o.getClass();
        Field[] fields = clazz.getDeclaredFields();
        List<String> changeLog = new ArrayList<>();

        try {
            for (Field field : fields) {
                field.setAccessible(true);
                Object valorNuevo = field.get(o);
                Object valorOriginal = field.get(original);

                if (!Objects.equals(valorNuevo, valorOriginal)) {
                    changeLog.add(field.getName() + "[" + valorOriginal + "] -> [" + valorNuevo + "]");
                }
            }
        } catch (IllegalAccessException e) {
            log.error("[getChangeLog] Se produjo un error al intentar comparar los campos de [" + o + "] y [" + original + "]", e);
        }

        return String.join(", ", changeLog);
    }

    public static <T> T cloneEntity(T entity) {
        if (entity == null) {
            return null;
        }

        try {
            Class<?> entityClass = entity.getClass();
            T clonedEntity = (T) entityClass.newInstance();

            BeanUtils.copyProperties(entity, clonedEntity);

            return clonedEntity;
        } catch (InstantiationException | IllegalAccessException e) {
            throw new RuntimeException("Error al clonar la entidad", e);
        }
    }

    public static User getUserFromSession() {
        // Intento recuperar el Usuario logueado
        User user = (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (user == null) {
            throw new RuntimeException("No se pudo recuperar el usuario asociado a authentication.principal");
        }

        return user;
    }

    public static String getCacheSafeValue() {
        long ts = System.currentTimeMillis();
        return Long.toString(ts, Character.MAX_RADIX);
    }
}