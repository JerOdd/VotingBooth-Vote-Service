package com.votingbooth.vote.api.controller;

import com.votingbooth.vote.api.model.dto.VoteRequest;
import com.votingbooth.vote.api.model.dto.VoteResultResponse;
import com.votingbooth.vote.api.service.VoteService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/votes")
public class VoteController {

    private final VoteService voteService;

    public VoteController(VoteService voteService) {
        this.voteService = voteService;
    }

    @PostMapping
    public ResponseEntity<Void> vote(@RequestBody VoteRequest voteRequest) {
        voteService.vote(voteRequest);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @GetMapping("/{lawId}")
    public ResponseEntity<VoteResultResponse> getVoteResult(@PathVariable UUID lawId) {
        return ResponseEntity.ok(voteService.getVoteResult(lawId));
    }

}
