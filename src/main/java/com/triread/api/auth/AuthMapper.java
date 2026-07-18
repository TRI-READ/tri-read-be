package com.triread.api.auth;

import java.time.Instant;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface AuthMapper {

    AuthUser findByLoginName(String loginName);

    AuthUser findById(long userId);

    List<AuthUser> findAll();

    int insert(AuthUser user);

    int updateLastLoginAt(
            @Param("userId") long userId,
            @Param("lastLoginAt") Instant lastLoginAt
    );

    int updateRole(@Param("userId") long userId, @Param("appRole") String appRole);

    int countEnabledAdmins();

    int promoteBootstrapAdmin(String loginName);
}
