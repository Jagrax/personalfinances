package ar.com.personalfinances.util;

import de.jollyday.Holiday;
import de.jollyday.HolidayCalendar;
import de.jollyday.HolidayManager;

import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Calendar;
import java.util.Date;
import java.util.Set;

public class DateUtils {

    private final static SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy");

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
        Calendar cal = Calendar.getInstance();
        cal.setTime(fecha);
        int year = cal.get(Calendar.YEAR);

        Set<Holiday> feriados = holidayManager.getHolidays(year);
        return feriados.stream().anyMatch(h -> h.getDate().equals(convertirDateALocalDate(fecha)));
    }

    /**
     * Devuelve el último día hábil antes de una fecha si esta cae en un feriado o fin de semana.
     */
    public static Date obtenerUltimoDiaHabil(Date fecha) {
        Calendar cal = Calendar.getInstance();
        cal.setTime(fecha);

        while (esFeriado(cal.getTime()) || esFinDeSemana(cal)) {
            cal.add(Calendar.DAY_OF_MONTH, -1);
        }

        return cal.getTime();
    }

    /**
     * Verifica si una fecha cae en sábado o domingo.
     */
    public static boolean esFinDeSemana(Calendar cal) {
        int diaSemana = cal.get(Calendar.DAY_OF_WEEK);
        return diaSemana == Calendar.SATURDAY || diaSemana == Calendar.SUNDAY;
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
}