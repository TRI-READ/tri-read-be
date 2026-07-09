package com.triread.api.auth;

import java.time.Instant;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface AuthMapper {

    AuthUser findByLoginName(String loginName);

    int insert(AuthUser user);

    int updateLastLoginAt(
            @Param("userId") long userId,
            @Param("lastLoginAt") Instant lastLoginAt
    );
}
