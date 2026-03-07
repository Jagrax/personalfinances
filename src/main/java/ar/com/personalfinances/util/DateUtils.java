package ar.com.personalfinances.util;

import de.jollyday.HolidayCalendar;
import de.jollyday.HolidayManager;

import java.text.SimpleDateFormat;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.List;
import java.util.Map;

public class DateUtils {

    private final static SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy");
    private final static DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private final static DateTimeFormatter TIMESTAMP_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");
    private final static List<LocalDate> notWorkingDays = List.of(
                  LocalDate.of(2025,  5,  2) // Feriado turístico
                , LocalDate.of(2025,  6, 20) // Paso a la Inmortalidad del Gral. Manuel Belgrano
                , LocalDate.of(2025,  8, 15) // Feriado turístico (por feriado del domingo 17 - Paso a la Inmortalidad del Gral. José de San Martín)
                , LocalDate.of(2025, 11, 21) // Feriado turístico (por feriado del Juéves 20 - Día de a Soberanía Nacional)
                , LocalDate.of(2025, 11, 24) // Día de la Soberanía Nacional (20/11)
                , LocalDate.of(2026,  2, 16) // Feriado por carnaval
                , LocalDate.of(2026,  2, 17) // Feriado por carnaval
    );
    public static final Map<String, String> MONTHS_ES = Map.ofEntries(
            Map.entry("Ene","Jan"),
            Map.entry("Feb","Feb"),
            Map.entry("Mar","Mar"),
            Map.entry("Abr","Apr"),
            Map.entry("May","May"),
            Map.entry("Jun","Jun"),
            Map.entry("Jul","Jul"),
            Map.entry("Ago","Aug"),
            Map.entry("Sep","Sep"),
            Map.entry("Oct","Oct"),
            Map.entry("Nov","Nov"),
            Map.entry("Dic","Dec")
    );

    public static String format(Date date) {
        return sdf.format(date);
    }

    public static String format(LocalDate localDate) {
        return format(localDate, FORMATTER);
    }

    public static String format(LocalDate localDate, DateTimeFormatter formatter) {
        if (localDate == null) return null;
        return localDate.format(formatter);
    }

    public static String format(LocalDateTime localDateTime) {
        if (localDateTime == null) return null;
        return localDateTime.format(TIMESTAMP_FORMATTER);
    }

    private static final HolidayManager holidayManager = HolidayManager.getInstance(HolidayCalendar.ARGENTINA);

    /**
     * Verifica si una fecha es feriado en Argentina.
     */
    public static boolean esFeriado(LocalDate date) {
        return holidayManager.isHoliday(date) || notWorkingDays.contains(date);
    }

    /**
     * Verifica si una fecha cae en sábado o domingo.
     */
    public static boolean isWeekend(LocalDate date) {
        DayOfWeek day = date.getDayOfWeek();
        return day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY;
    }
}