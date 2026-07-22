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
            @Param("createdByUserId") long createdByUserId,
            @Param("expiresAt") java.time.Instant expiresAt,
            @Param("maxUses") Integer maxUses
    );

    List<GroupData.GroupRow> findMyGroups(long userId);

    GroupData.GroupRow findGroupForMember(
            @Param("groupId") long groupId,
            @Param("userId") long userId
    );

    List<GroupData.MemberRow> findMembers(long groupId);

    GroupData.MemberRow findMember(@Param("groupId") long groupId,
                                   @Param("userId") long userId);

    List<GroupData.InviteManagementRow> findInvites(long groupId);

    List<GroupData.ActivityRow> findWeeklyActivity(
            @Param("groupId") long groupId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate,
            @Param("today") LocalDate today
    );

    GroupData.InviteRow findInviteForUpdate(String codeHash);

    int disableGroupInvites(long groupId);

    int disableInvite(@Param("groupId") long groupId, @Param("inviteId") long inviteId);

    int consumeInvite(long inviteId);

    int deleteMember(@Param("groupId") long groupId, @Param("userId") long userId);

    int updateMemberRole(@Param("groupId") long groupId, @Param("userId") long userId,
                         @Param("role") String role);
}
