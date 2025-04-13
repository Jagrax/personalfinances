package ar.com.personalfinances.task;

import ar.com.personalfinances.entity.InstanceTask;
import ar.com.personalfinances.repository.InstanceTaskRepository;
import com.cronutils.model.Cron;
import com.cronutils.model.CronType;
import com.cronutils.model.definition.CronDefinitionBuilder;
import com.cronutils.model.time.ExecutionTime;
import com.cronutils.parser.CronParser;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

@Component
public class DynamicTaskScheduler implements InitializingBean {

    @Autowired
    private InstanceTaskRepository instanceTaskRepository;

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private TaskScheduler taskScheduler;

    @Override
    public void afterPropertiesSet() throws Exception {
        // Leer todas las tareas habilitadas
        List<InstanceTask> tasks = instanceTaskRepository.findByEnabledIsTrue();
        for (InstanceTask task : tasks) {
            // Obtener el servicio correspondiente por el nombre de la clase
            Object taskService = applicationContext.getBean(task.getTaskService());

            // Si el cron no ha sido ejecutado aún o está por ejecutarse, programamos la tarea
            taskScheduler.schedule(() -> executeTask(task, taskService), new CronTrigger(task.getCronExpression()));

            // Si la hora de la última ejecución no coincide, ejecutamos la tarea
            if (task.getLastExecution() == null || !wasLastExecutionOnTime(task)) {
                executeTask(task, taskService);
            }
        }
    }

    private boolean wasLastExecutionOnTime(InstanceTask task) {
        String cronExpression = task.getCronExpression();
        CronParser parser = new CronParser(CronDefinitionBuilder.instanceDefinitionFor(CronType.QUARTZ));
        Cron cron = parser.parse(cronExpression);
        ExecutionTime executionTime = ExecutionTime.forCron(cron);

        ZonedDateTime now = ZonedDateTime.now();
        ZonedDateTime lastExpectedExecution = executionTime.lastExecution(now)
                .orElseThrow(() -> new IllegalStateException("No se pudo calcular la última ejecución esperada para el cron"));

        ZonedDateTime actualLastExecution = task.getLastExecution().atZone(ZoneId.systemDefault());

        return !actualLastExecution.isBefore(lastExpectedExecution); // true si se ejecutó a tiempo
    }


    private void executeTask(InstanceTask task, Object taskService) {
        try {
            // Llamar al método correspondiente en el servicio
            Method method = taskService.getClass().getMethod("runTask", InstanceTask.class);
            method.invoke(taskService, task); // Ejecutamos el método correspondiente
            updateLastExecution(task); // Actualizamos la última ejecución
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void updateLastExecution(InstanceTask task) {
        task.setLastExecution(LocalDateTime.now());
        instanceTaskRepository.save(task); // Actualizar en base de datos
    }
}
