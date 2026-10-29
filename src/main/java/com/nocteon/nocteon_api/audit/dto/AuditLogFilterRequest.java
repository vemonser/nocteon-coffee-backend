package com.nocteon.nocteon_api.audit.dto;

import java.time.Instant;

import com.nocteon.nocteon_api.common.dto.BaseFilterRequest;

import lombok.Getter;
import lombok.Setter;

/**
 * The filters the audit page sends.
 *
 * <p>{@code action} is a CONTAINS match on the stored action name, not an exact
 * enum value: the page offers one search box, and typing "product" must find
 * PRODUCT_CREATE, PRODUCT_UPDATE and PRODUCT_DELETE in one go.
 *
 * <p>{@code actor} takes either a numeric user id or a fragment of the actor's
 * email — the two columns the table actually stores ({@code user_id},
 * {@code user_email}). There is no username column on audit_logs, so a
 * username cannot be filtered on; see DECISIONS.md.
 *
 * <p>{@code since}/{@code until} are instants, inclusive. The page sends the
 * start and the end of the chosen local day so "the 3rd" means the reader's
 * 3rd, not UTC's.
 */
@Getter
@Setter
public class AuditLogFilterRequest extends BaseFilterRequest {

    private String action;
    private String actor;
    private String entityType;
    private Long entityId;
    private Instant since;
    private Instant until;
}
