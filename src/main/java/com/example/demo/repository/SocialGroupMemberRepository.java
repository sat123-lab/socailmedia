package com.example.demo.repository;

import com.example.demo.entity.SocialGroup;
import com.example.demo.entity.SocialGroupMember;
import com.example.demo.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface SocialGroupMemberRepository extends JpaRepository<SocialGroupMember, Long> {

    boolean existsByGroupAndUser(SocialGroup group, User user);

    Optional<SocialGroupMember> findByGroupAndUser(SocialGroup group, User user);

    List<SocialGroupMember> findByGroup(SocialGroup group);

    @Query("SELECT m FROM SocialGroupMember m JOIN FETCH m.group g WHERE m.user.id = :userId ORDER BY g.createdAt DESC")
    List<SocialGroupMember> findByUserIdWithGroup(@Param("userId") Long userId);
}
