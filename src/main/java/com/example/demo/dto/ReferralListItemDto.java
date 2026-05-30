package com.example.demo.dto;

import com.example.demo.entity.Referral;
import com.example.demo.entity.ReferralStatus;
import com.example.demo.entity.User;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Row in the "Invited users" list on the dashboard. Carries enough
 * profile chrome to render the avatar + name without a second fetch,
 * but never leaks the referee's email or phone.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReferralListItemDto {

    private Long id;
    private String code;
    private ReferralStatus status;
    private String source;

    private LocalDateTime createdAt;
    private LocalDateTime signedUpAt;
    private LocalDateTime activatedAt;

    /** Anything the fraud service flagged. Empty when clean. */
    private String fraudFlags;

    // ----- referee chrome (null on PENDING_CLICK rows) -----
    private Long refereeUserId;
    private String refereeName;
    private String refereeProfileImage;
    private boolean refereeVerified;

    public static ReferralListItemDto from(Referral r, User referee) {
        return ReferralListItemDto.builder()
                .id(r.getId())
                .code(r.getCode())
                .status(r.getStatus())
                .source(r.getSource())
                .createdAt(r.getCreatedAt())
                .signedUpAt(r.getSignedUpAt())
                .activatedAt(r.getActivatedAt())
                .fraudFlags(r.getFraudFlags())
                .refereeUserId(referee == null ? null : referee.getId())
                .refereeName(referee == null ? null : referee.getName())
                .refereeProfileImage(referee == null ? null : referee.getProfileImage())
                .refereeVerified(referee != null && referee.isVerified())
                .build();
    }
}
