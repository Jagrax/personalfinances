package ar.com.personalfinances.entity;

import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Getter
@Setter
@Entity
@Table(name = "instance_tasks")
public class InstanceTask {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @Column(name = "task_key", unique = true)
    private String taskKey;

    @Column(name = "task_service", nullable = false)
    private String taskService;

    @NotNull
    @Column(name = "description")
    private String description;

    @NotNull
    @Column(name = "cron_expression")
    private String cronExpression;

    @Column(name = "last_execution")
    private LocalDateTime lastExecution;

    @Column(name = "enabled")
    private boolean enabled = true;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "instanceTask", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    private List<InstanceTaskConfig> configs = new ArrayList<>();

    public Map<String, String> getConfisAsMap() {
        return configs.stream().collect(Collectors.toMap(InstanceTaskConfig::getConfigKey, InstanceTaskConfig::getConfigValue));
    }

    @Override
    public String toString() {
        return "InstanceTask [" +
                ((id != null) ? "id=" + id + ", " : "") +
                ((taskKey != null) ? "taskKey='" + taskKey + "', " : "") +
                ((description != null) ? "description='" + description + "', " : "") +
                ((cronExpression != null) ? "cronExpression='" + cronExpression + "', " : "") +
                ((lastExecution != null) ? "lastExecution=" + lastExecution + ", " : "") +
                "enabled=" + enabled + ", " +
                ((createdAt != null) ? "createdAt=" + createdAt + ", " : "") +
                ((updatedAt != null) ? "updatedAt=" + updatedAt + ", " : "") +
                ((configs != null) ? "configs=" + Arrays.toString(configs.toArray()) + ", " : "") +
                "]";
    }

    @Override
    public boolean equals(Object o) {

        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        InstanceTask that = (InstanceTask) o;
        return enabled == that.enabled && Objects.equals(id, that.id) && Objects.equals(taskKey, that.taskKey) && Objects.equals(description, that.description) && Objects.equals(cronExpression, that.cronExpression) && Objects.equals(lastExecution, that.lastExecution) && Objects.equals(createdAt, that.createdAt) && Objects.equals(updatedAt, that.updatedAt) && Objects.equals(configs, that.configs);
    }

    @Override
    public int hashCode() {
        if (this.id != null) return this.id.hashCode();
        return Objects.hash(id, taskKey, description, cronExpression, lastExecution, enabled, createdAt, updatedAt, configs);
    }
}
