package ar.com.personalfinances.entity;

import lombok.Getter;
import lombok.Setter;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import java.util.Objects;

@Getter
@Setter
@Entity
@Table(name = "instance_task_configs")
public class InstanceTaskConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @Column(name = "config_key")
    private String configKey;

    @NotNull
    @Column(name = "config_value")
    private String configValue;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "task_id")
    private InstanceTask instanceTask;

    @Override
    public String toString() {
        return "InstanceTaskConfig [" +
                ((id != null) ? "id=" + id + ", " : "") +
                ((configKey != null) ? "configKey='" + configKey + "', " : "") +
                ((configValue != null) ? "configValue='" + configValue + "', " : "") +
                ((instanceTask != null) ? "task=" + instanceTask + ", " : "") +
                "]";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        InstanceTaskConfig that = (InstanceTaskConfig) o;
        return Objects.equals(id, that.id) && Objects.equals(configKey, that.configKey) && Objects.equals(configValue, that.configValue) && Objects.equals(instanceTask, that.instanceTask);
    }

    @Override
    public int hashCode() {
        if (this.id != null) return this.id.hashCode();
        return Objects.hash(id, configKey, configValue, instanceTask);
    }
}