package pl.edu.pk.pkampus.modules.dormitory;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "rooms",
        uniqueConstraints = @UniqueConstraint(name = "uq_dormitory_room", columnNames = {"dormitory_id", "room_number"})
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Room {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "dormitory_id", nullable = false)
    private Dormitory dormitory;

    @Column(name = "room_number", length = 10, nullable = false)
    private String roomNumber;

    @Column(name = "floor", nullable = false)
    private Integer floor;

    @Builder.Default
    @Column(name = "capacity", nullable = false)
    private Integer capacity = 2;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /**
     * Applies a partial update: null fields are ignored, blank room numbers are ignored.
     * Uniqueness and floor-range validation stay in the service layer.
     */
    public void applyPatch(String roomNumber, Integer floor, Integer capacity) {
        if (roomNumber != null && !roomNumber.isBlank()) {
            this.roomNumber = roomNumber.trim();
        }
        if (floor != null) {
            this.floor = floor;
        }
        if (capacity != null) {
            this.capacity = capacity;
        }
    }
}
