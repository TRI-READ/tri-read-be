package com.triread.api.group;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.triread.api.common.ApiException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

@ExtendWith(MockitoExtension.class)
class GroupServiceTest {

    private static final long OWNER_ID = 3L;
    private static final long MEMBER_ID = 7L;
    private static final long GROUP_ID = 41L;
    private static final Instant NOW = Instant.parse("2026-07-11T03:00:00Z");

    @Mock
    private GroupMapper groupMapper;

    @Mock
    private GroupInviteCodeService inviteCodeService;

    private GroupService groupService;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        groupService = new GroupServiceImpl(groupMapper, inviteCodeService, clock);
    }

    @Test
    void createGroupCreatesOwnerAndInitialInvite() {
        doAnswer(invocation -> {
            GroupData.GroupInsert group = invocation.getArgument(0);
            group.setId(GROUP_ID);
            return 1;
        }).when(groupMapper).insertGroup(any(GroupData.GroupInsert.class));
        givenGeneratedInviteCode();
        GroupData.GroupRow groupRow = ownerGroupRow();
        when(groupMapper.findGroupForMember(GROUP_ID, OWNER_ID)).thenReturn(groupRow);
        when(groupMapper.findMembers(GROUP_ID)).thenReturn(List.of(ownerMemberRow()));

        GroupService.CreatedGroupResponse result = groupService.createGroup(
                OWNER_ID,
                "  Morning Readers  ",
                "  Read together  "
        );

        ArgumentCaptor<GroupData.GroupInsert> groupCaptor =
                ArgumentCaptor.forClass(GroupData.GroupInsert.class);
        verify(groupMapper).insertGroup(groupCaptor.capture());
        assertThat(groupCaptor.getValue().getName()).isEqualTo("Morning Readers");
        assertThat(groupCaptor.getValue().getDescription()).isEqualTo("Read together");
        verify(groupMapper).insertMember(GROUP_ID, OWNER_ID, "OWNER");
        verify(groupMapper).insertInvite(GROUP_ID, "invite-hash", OWNER_ID);
        assertThat(result.inviteCode()).isEqualTo("ABCDE-FGHIJ");
        assertThat(result.group().role()).isEqualTo("OWNER");
        assertThat(result.group().members()).hasSize(1);
    }

    @Test
    void joinGroupCreatesMemberAndConsumesInvite() {
        givenSubmittedInviteCode();
        GroupData.InviteRow invite = new GroupData.InviteRow(
                91L,
                GROUP_ID,
                null,
                null,
                0
        );
        when(groupMapper.findInviteForUpdate("invite-hash")).thenReturn(invite);
        when(groupMapper.findGroupForMember(GROUP_ID, MEMBER_ID))
                .thenReturn(null, memberGroupRow());
        when(groupMapper.findMembers(GROUP_ID)).thenReturn(List.of(
                ownerMemberRow(),
                new GroupData.MemberRow(MEMBER_ID, "New Reader", "MEMBER", NOW)
        ));

        GroupService.GroupDetail result = groupService.joinGroup(
                MEMBER_ID,
                "abcde-fghij"
        );

        verify(groupMapper).insertMember(GROUP_ID, MEMBER_ID, "MEMBER");
        verify(groupMapper).consumeInvite(91L);
        assertThat(result.role()).isEqualTo("MEMBER");
        assertThat(result.members()).hasSize(2);
    }

    @Test
    void joinGroupRejectsExistingMemberWithoutConsumingInvite() {
        givenSubmittedInviteCode();
        when(groupMapper.findInviteForUpdate("invite-hash")).thenReturn(
                new GroupData.InviteRow(91L, GROUP_ID, null, null, 0)
        );
        when(groupMapper.findGroupForMember(GROUP_ID, MEMBER_ID))
                .thenReturn(memberGroupRow());

        assertThatThrownBy(() -> groupService.joinGroup(MEMBER_ID, "ABCDE-FGHIJ"))
                .isInstanceOfSatisfying(ApiException.class, exception -> {
                    assertThat(exception.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(exception.getCode()).isEqualTo("GROUP_ALREADY_JOINED");
                });

        verify(groupMapper, never()).insertMember(GROUP_ID, MEMBER_ID, "MEMBER");
        verify(groupMapper, never()).consumeInvite(91L);
    }

    @Test
    void joinGroupRejectsExpiredInvite() {
        givenSubmittedInviteCode();
        when(groupMapper.findInviteForUpdate("invite-hash")).thenReturn(
                new GroupData.InviteRow(
                        91L,
                        GROUP_ID,
                        NOW.minusSeconds(1),
                        10,
                        0
                )
        );

        assertThatThrownBy(() -> groupService.joinGroup(MEMBER_ID, "ABCDE-FGHIJ"))
                .isInstanceOfSatisfying(ApiException.class, exception -> {
                    assertThat(exception.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(exception.getCode()).isEqualTo("INVALID_INVITE_CODE");
                });

        verify(groupMapper, never()).insertMember(GROUP_ID, MEMBER_ID, "MEMBER");
    }

    @Test
    void renewInviteRotatesCodesForOwner() {
        when(groupMapper.findGroupForMember(GROUP_ID, OWNER_ID)).thenReturn(ownerGroupRow());
        givenGeneratedInviteCode();

        GroupService.InviteCodeResponse result = groupService.renewInvite(
                GROUP_ID,
                OWNER_ID
        );

        verify(groupMapper).disableGroupInvites(GROUP_ID);
        verify(groupMapper).insertInvite(GROUP_ID, "invite-hash", OWNER_ID);
        assertThat(result.inviteCode()).isEqualTo("ABCDE-FGHIJ");
    }

    @Test
    void renewInviteRejectsRegularMember() {
        when(groupMapper.findGroupForMember(GROUP_ID, MEMBER_ID)).thenReturn(memberGroupRow());

        assertThatThrownBy(() -> groupService.renewInvite(GROUP_ID, MEMBER_ID))
                .isInstanceOfSatisfying(ApiException.class, exception -> {
                    assertThat(exception.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
                    assertThat(exception.getCode()).isEqualTo("GROUP_OWNER_REQUIRED");
                });

        verify(groupMapper, never()).disableGroupInvites(GROUP_ID);
    }

    @Test
    void weeklyActivityRanksMembersAndKeepsTies() {
        LocalDate monday = LocalDate.of(2026, 7, 6);
        LocalDate friday = LocalDate.of(2026, 7, 10);
        when(groupMapper.findGroupForMember(GROUP_ID, OWNER_ID)).thenReturn(ownerGroupRow());
        when(groupMapper.findWeeklyActivity(GROUP_ID, monday, friday.plusDays(2),
                LocalDate.of(2026, 7, 11))).thenReturn(List.of(
                activityRow(OWNER_ID, "Owner", 2, 16, 1, 2, 1, true, 40),
                activityRow(MEMBER_ID, "Member", 2, 16, 0, 2, 1, false, 40),
                activityRow(9L, "New Reader", 1, 7, 0, 0, 0, false, 17)
        ));

        GroupService.GroupActivity result = groupService.getWeeklyActivity(GROUP_ID, OWNER_ID);

        assertThat(result.startDate()).isEqualTo(monday);
        assertThat(result.endDate()).isEqualTo(friday);
        assertThat(result.todayCompletedCount()).isEqualTo(1);
        assertThat(result.ranking()).extracting(GroupService.MemberActivity::rank)
                .containsExactly(1, 1, 3);
        assertThat(result.ranking().getFirst().averageScore()).isEqualTo(8);
    }

    private void givenGeneratedInviteCode() {
        when(inviteCodeService.generateCode()).thenReturn("ABCDE-FGHIJ");
        when(inviteCodeService.normalize("ABCDE-FGHIJ")).thenReturn("ABCDEFGHIJ");
        when(inviteCodeService.hash("ABCDEFGHIJ")).thenReturn("invite-hash");
    }

    private void givenSubmittedInviteCode() {
        when(inviteCodeService.normalize(any(String.class))).thenReturn("ABCDEFGHIJ");
        when(inviteCodeService.hash("ABCDEFGHIJ")).thenReturn("invite-hash");
    }

    private GroupData.GroupRow ownerGroupRow() {
        return new GroupData.GroupRow(
                GROUP_ID,
                "Morning Readers",
                "Read together",
                "OWNER",
                1,
                NOW
        );
    }

    private GroupData.GroupRow memberGroupRow() {
        return new GroupData.GroupRow(
                GROUP_ID,
                "Morning Readers",
                "Read together",
                "MEMBER",
                2,
                NOW
        );
    }

    private GroupData.MemberRow ownerMemberRow() {
        return new GroupData.MemberRow(OWNER_ID, "Owner", "OWNER", NOW);
    }

    private GroupData.ActivityRow activityRow(
            long userId,
            String displayName,
            int completedDays,
            int totalCorrect,
            int perfectCount,
            int recoveredCount,
            int fullyLitCount,
            boolean todayCompleted,
            int activityScore
    ) {
        return new GroupData.ActivityRow(userId, displayName, "MEMBER", completedDays,
                totalCorrect, perfectCount, recoveredCount, fullyLitCount,
                todayCompleted, activityScore);
    }
}
