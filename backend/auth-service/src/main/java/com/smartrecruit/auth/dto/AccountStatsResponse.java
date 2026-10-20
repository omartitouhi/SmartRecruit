package com.smartrecruit.auth.dto;

import com.smartrecruit.auth.model.AccountStatus;
import com.smartrecruit.auth.model.Role;

import java.util.Map;

/** Every role and status is present, with 0 when empty. */
public record AccountStatsResponse(long total, Map<Role, Long> byRole, Map<AccountStatus, Long> byStatus) {
}
