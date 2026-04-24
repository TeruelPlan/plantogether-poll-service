package com.plantogether.poll.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreatePollRequest {

  @NotBlank
  @Size(max = 255)
  private String title;

  @NotNull
  @Size(min = 2, max = 20, message = "Between 2 and 20 date slots are required")
  @Valid
  private List<SlotRequest> slots;

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class SlotRequest {

    @NotNull @FutureOrPresent private LocalDate startDate;

    @NotNull private LocalDate endDate;

    @JsonIgnore
    @AssertTrue(message = "endDate must be on or after startDate")
    public boolean isValidDateRange() {
      if (startDate == null || endDate == null) return true;
      return !endDate.isBefore(startDate);
    }
  }
}
