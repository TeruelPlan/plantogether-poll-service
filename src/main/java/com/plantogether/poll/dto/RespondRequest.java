package com.plantogether.poll.dto;

import com.plantogether.poll.domain.VoteStatus;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RespondRequest {

  @NotNull private UUID slotId;

  @NotNull private VoteStatus status;
}
