package com.plantogether.poll.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;

@Entity
@Table(name = "poll")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Poll {

  @Id
  @GeneratedValue
  @UuidGenerator(style = UuidGenerator.Style.TIME)
  @Column(updatable = false, nullable = false)
  private UUID id;

  @Column(name = "trip_id", nullable = false)
  private UUID tripId;

  @Column(nullable = false, length = 255)
  private String title;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private PollStatus status;

  @Column(name = "locked_slot_id")
  private UUID lockedSlotId;

  @Column(name = "created_by", nullable = false)
  private UUID createdBy;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @OneToMany(
      mappedBy = "poll",
      cascade = CascadeType.ALL,
      orphanRemoval = true,
      fetch = FetchType.LAZY)
  @OrderBy("slotIndex ASC")
  @Builder.Default
  private List<PollSlot> slots = new ArrayList<>();
}
