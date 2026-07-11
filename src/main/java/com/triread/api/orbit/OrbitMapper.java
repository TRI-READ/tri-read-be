package com.triread.api.orbit;

import java.time.LocalDate;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface OrbitMapper {
    List<OrbitData.OrbitAttemptRow> findAttempts(
            @Param("userId") long userId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );
}
