package com.triread.api.auth;

import java.time.Instant;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface AuthMapper {

    AuthUser findByLoginName(String loginName);

    AuthUser findById(long userId);

    List<AuthUser> findAll(@Param("offset") int offset, @Param("limit") int limit);

    long countAll();

    int insert(AuthUser user);

    int updateLastLoginAt(
            @Param("userId") long userId,
            @Param("lastLoginAt") Instant lastLoginAt
    );

    int updateRole(@Param("userId") long userId, @Param("appRole") String appRole);

    int updatePinHash(@Param("userId") long userId, @Param("pinHash") String pinHash);

    int updateEnabled(@Param("userId") long userId, @Param("enabled") boolean enabled);

    int countEnabledAdmins();

    int promoteBootstrapAdmin(String loginName);
}
