package ar.com.personalfinances.entity;

import ar.com.personalfinances.util.DateUtils;
import lombok.Getter;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;

import javax.persistence.*;
import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "alert_events")
public class AlertEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "timestamp", nullable = false)
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime timestamp;

    @Column(name = "entity_type")
    @Enumerated(EnumType.STRING)
    private EntityType entityType;

    @Column(name = "entity_id")
    private Long entityId;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "event")
    @Enumerated(EnumType.STRING)
    private EntityEvent event;

    @Column(name = "details", length = 4000)
    private String details;

    // Algunos constructores comodos

    public AlertEvent() {
    }

    public AlertEvent(EntityType entityType, Long entityId, Long userId, EntityEvent event, String details) {
        this.timestamp = LocalDateTime.now();
        this.entityType = entityType;
        this.entityId = entityId;
        this.userId = userId;
        this.event = event;
        this.details = details;
    }

    @Override
    public String toString() {
        return "AlertEvent [" +
                ((id != null) ? "id=" + id + ", " : "") +
                ((timestamp != null) ? "timestamp=" + DateUtils.format(timestamp) + ", " : "") +
                ((entityType != null) ? "entityType=" + entityType + ", " : "") +
                ((entityId != null) ? "entityId=" + entityId + ", " : "") +
                ((userId != null) ? "userId=" + userId + ", " : "") +
                ((event != null) ? "event=" + event + ", " : "") +
                ((details != null) ? "details='" + details + "', " : "") +
                "]";
    }
}