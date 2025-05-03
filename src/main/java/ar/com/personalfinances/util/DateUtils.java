package ar.com.personalfinances.util;

import de.jollyday.HolidayCalendar;
import de.jollyday.HolidayManager;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

public class DateUtils {

    private final static SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy");
    private final static List<LocalDate> notWorkingDays;

    static {
        try {
            notWorkingDays = List.of(
                    convertirDateALocalDate(sdf.parse("02/05/2025")) // Feriado turístico
            );
        } catch (ParseException e) {
            throw new RuntimeException(e);
        }
    }

    public static String format(Date date) {
        return sdf.format(date);
    }

    public static boolean isSameDay(Date date1, Date date2) {
        LocalDate localDate1 = date1.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
        LocalDate localDate2 = date2.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
        return localDate1.isEqual(localDate2);
    }

    private static final HolidayManager holidayManager = HolidayManager.getInstance(HolidayCalendar.ARGENTINA);

    /**
     * Verifica si una fecha es feriado en Argentina.
     */
    public static boolean esFeriado(Date fecha) {
        final LocalDate date = convertirDateALocalDate(fecha);
        return holidayManager.isHoliday(date) || notWorkingDays.contains(date);
    }

    /**
     * Devuelve el último día hábil antes de una fecha si esta cae en un feriado o fin de semana.
     */
    public static Date obtenerUltimoDiaHabil(Date fecha) {
        Calendar cal = Calendar.getInstance();
        cal.setTime(fecha);

        while (esFeriado(cal.getTime()) || isWeekend(cal)) {
            cal.add(Calendar.DAY_OF_MONTH, -1);
        }

        return cal.getTime();
    }

    /**
     * Verifica si una fecha cae en sábado o domingo.
     */
    public static boolean isWeekend(Calendar cal) {
        int weekday = cal.get(Calendar.DAY_OF_WEEK);
        return weekday == Calendar.SATURDAY || weekday == Calendar.SUNDAY;
    }

    /**
     * Convierte un Date a LocalDate para poder comparar con Jollyday.
     */
    private static java.time.LocalDate convertirDateALocalDate(Date date) {
        Calendar cal = Calendar.getInstance();
        cal.setTime(date);
        return java.time.LocalDate.of(
                cal.get(Calendar.YEAR),
                cal.get(Calendar.MONTH) + 1,
                cal.get(Calendar.DAY_OF_MONTH)
        );
    }

    public static Date addMonths(Date date, int months) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(date);
        calendar.add(Calendar.MONTH, months);
        return calendar.getTime();
    }
}