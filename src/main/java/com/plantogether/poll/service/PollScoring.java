package com.plantogether.poll.service;

import com.plantogether.poll.domain.PollResponse;
import com.plantogether.poll.domain.VoteStatus;

import java.util.Collection;

public final class PollScoring {

    private PollScoring() {
    }

    public static int scoreForSlot(Collection<PollResponse> responses) {
        int yes = 0;
        int maybe = 0;
        for (PollResponse r : responses) {
            if (r.getStatus() == VoteStatus.YES) {
                yes++;
            } else if (r.getStatus() == VoteStatus.MAYBE) {
                maybe++;
            }
        }
        return (yes * 2) + maybe;
    }
}
