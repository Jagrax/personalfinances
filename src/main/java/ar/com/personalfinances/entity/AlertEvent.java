package ar.com.personalfinances.entity;

import javax.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;

import java.util.Date;

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
    @DateTimeFormat(pattern = "yyyy-MM-dd HH24:mm:ss")
    private Date timestamp;

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

    @Column(name = "details")
    private String details;

    // Algunos constructores comodos

    public AlertEvent() {
    }

    public AlertEvent(EntityType entityType, Long entityId, Long userId, EntityEvent event, String details) {
        this.timestamp = new Date();
        this.entityType = entityType;
        this.entityId = entityId;
        this.userId = userId;
        this.event = event;
        this.details = details;
    }
}