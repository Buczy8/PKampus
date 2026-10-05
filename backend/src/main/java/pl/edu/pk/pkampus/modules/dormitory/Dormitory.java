package pl.edu.pk.pkampus.modules.dormitory;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;
import java.time.LocalTime;
import java.util.UUID;

@Entity
@Table(name = "dormitories")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Dormitory implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "name", length = 100, nullable = false)
    private String name;

    @Column(name = "code", length = 10, nullable = false, unique = true)
    private String code;

    @Column(name = "address", nullable = false)
    private String address;

    @Column(name = "floors_count", nullable = false)
    private Integer floorsCount;

    @Column(name = "laundry_opening_time", nullable = false)
    private LocalTime laundryOpeningTime;

    @Column(name = "laundry_closing_time", nullable = false)
    private LocalTime laundryClosingTime;

    @Column(name = "laundry_slot_duration_minutes", nullable = false)
    private Integer laundrySlotDurationMinutes;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
