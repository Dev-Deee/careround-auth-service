package com.dayo.careround.authservice.dto.response;

import java.util.List;

// total counts every problem account; items holds at most `cap` of them (sorted by role, then email).
public record WardProblemsResponse(
        int total,
        int cap,
        List<WardProblemResponse> items
) {}
