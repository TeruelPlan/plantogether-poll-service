package com.plantogether.poll.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;

import java.util.UUID;

@Entity
@Table(name = "poll_response")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PollResponse {

    @Id
    @GeneratedValue
    @UuidGenerator(style = UuidGenerator.Style.TIME)
    @Column(updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "poll_slot_id", nullable = false)
    private PollSlot pollSlot;

    @Column(name = "device_id", nullable = false)
    private UUID deviceId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private VoteStatus status;
}
