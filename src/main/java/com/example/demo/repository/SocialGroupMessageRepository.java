package com.example.demo.repository;

import com.example.demo.entity.SocialGroup;
import com.example.demo.entity.SocialGroupMessage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public interface SocialGroupMessageRepository extends JpaRepository<SocialGroupMessage, Long>,
        JpaSpecificationExecutor<SocialGroupMessage> {

    List<SocialGroupMessage> findByGroupOrderByCreatedAtAsc(SocialGroup group);

    // PostgreSQL-safe: Uses Specifications instead of nullable parameters in JPQL.
    default Page<SocialGroupMessage> adminSearch(
            Long senderId,
            LocalDateTime from,
            LocalDateTime to,
            boolean encryptedOnly,
            Pageable pageable) {

        Specification<SocialGroupMessage> spec = (root, query, cb) -> {
            List<jakarta.persistence.criteria.Predicate> predicates = new ArrayList<>();

            if (senderId != null) {
                predicates.add(cb.equal(root.get("sender").get("id"), senderId));
            }
            if (from != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), from));
            }
            if (to != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"), to));
            }
            if (encryptedOnly) {
                predicates.add(cb.isTrue(root.get("encrypted")));
            }

            query.orderBy(cb.desc(root.get("createdAt")));
            return cb.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };

        return findAll(spec, pageable);
    }
}
