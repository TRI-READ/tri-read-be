package com.triread.api.group;

import java.util.List;
import java.time.LocalDate;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface GroupMapper {

    int insertGroup(GroupData.GroupInsert group);

    int insertMember(
            @Param("groupId") long groupId,
            @Param("userId") long userId,
            @Param("role") String role
    );

    int insertInvite(
            @Param("groupId") long groupId,
            @Param("codeHash") String codeHash,
            @Param("createdByUserId") long createdByUserId
    );

    List<GroupData.GroupRow> findMyGroups(long userId);

    GroupData.GroupRow findGroupForMember(
            @Param("groupId") long groupId,
            @Param("userId") long userId
    );

    List<GroupData.MemberRow> findMembers(long groupId);

    List<GroupData.ActivityRow> findWeeklyActivity(
            @Param("groupId") long groupId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate,
            @Param("today") LocalDate today
    );

    GroupData.InviteRow findInviteForUpdate(String codeHash);

    int disableGroupInvites(long groupId);

    int consumeInvite(long inviteId);
}
