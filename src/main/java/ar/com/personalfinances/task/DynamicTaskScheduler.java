package ar.com.personalfinances.task;

import ar.com.personalfinances.entity.InstanceTask;
import ar.com.personalfinances.repository.InstanceTaskRepository;
import ar.com.personalfinances.util.CommonResult;
import com.cronutils.model.Cron;
import com.cronutils.model.CronType;
import com.cronutils.model.definition.CronDefinitionBuilder;
import com.cronutils.model.time.ExecutionTime;
import com.cronutils.parser.CronParser;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

@Slf4j
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
        List<InstanceTask> tasks = instanceTaskRepository.findByEnabledIsTrue();
        for (InstanceTask task : tasks) {
            try {
                Object taskService = applicationContext.getBean(task.getTaskService());
                if (!(taskService instanceof BaseInstanceTaskService)) {
                    log.error("[scheduler] El bean {} no extiende BaseInstanceTaskService, se omite la tarea {}", task.getTaskService(), task.getTaskKey());
                    continue;
                }
                BaseInstanceTaskService service = (BaseInstanceTaskService) taskService;

                taskScheduler.schedule(() -> executeTask(task, service), new CronTrigger(task.getCronExpression()));

                if (task.getLastExecution() == null || !wasLastExecutionOnTime(task)) {
                    taskScheduler.schedule(() -> executeTask(task, service), Instant.now());
                }
            } catch (Exception e) {
                log.error("[scheduler] No se pudo registrar la tarea {}", task.getTaskKey(), e);
            }
        }
    }

    private boolean wasLastExecutionOnTime(InstanceTask task) {
        CronParser parser = new CronParser(CronDefinitionBuilder.instanceDefinitionFor(CronType.QUARTZ));
        Cron cron = parser.parse(task.getCronExpression());
        ExecutionTime executionTime = ExecutionTime.forCron(cron);

        ZonedDateTime now = ZonedDateTime.now();
        ZonedDateTime lastExpectedExecution = executionTime.lastExecution(now)
                .orElseThrow(() -> new IllegalStateException("No se pudo calcular la última ejecución esperada para el cron " + task.getCronExpression()));

        ZonedDateTime actualLastExecution = task.getLastExecution().atZone(ZoneId.systemDefault());

        return !actualLastExecution.isBefore(lastExpectedExecution);
    }

    private void executeTask(InstanceTask task, BaseInstanceTaskService service) {
        if (wasTooSoon(task, service)) return;

        try {
            CommonResult result = service.runTask(task);
            if (result.isError()) {
                log.error("[scheduler] La tarea {} finalizo con error: {}", task.getTaskKey(), result.getMessage());
                return;
            }
            if (result.isWarning()) {
                log.warn("[scheduler] La tarea {} finalizo con advertencia: {}", task.getTaskKey(), result.getMessage());
            }
            updateLastExecution(task);
        } catch (Exception e) {
            log.error("[scheduler] Error ejecutando la tarea {}", task.getTaskKey(), e);
        }
    }

    private boolean wasTooSoon(InstanceTask task, BaseInstanceTaskService service) {
        int minHours = service.getMinHoursBetweenRuns();
        LocalDateTime lastExecution = task.getLastExecution();
        if (minHours <= 0 || lastExecution == null) return false;
        if (Duration.between(lastExecution, LocalDateTime.now()).toHours() >= minHours) return false;

        log.info("[scheduler] Tarea {} omitida: la última ejecución fue hace menos de {} horas", task.getTaskKey(), minHours);
        return true;
    }

    private void updateLastExecution(InstanceTask task) {
        LocalDateTime executionTime = LocalDateTime.now();
        instanceTaskRepository.updateLastExecution(task.getId(), executionTime);
        task.setLastExecution(executionTime);
    }
}
