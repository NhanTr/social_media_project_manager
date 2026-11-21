package com.nhantr.moderation_service.service;

import com.nhantr.common.api.ApiException;
import com.nhantr.moderation_service.client.IdentityClient;
import com.nhantr.moderation_service.client.NotificationClient;
import com.nhantr.moderation_service.client.PostClient;
import com.nhantr.moderation_service.testutil.TestModerationRepositories;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ModerationReportValidationTests {
    private TestModerationRepositories.FakeReportRepository reportRepository;
    private TestModerationRepositories.FakeModerationAuditLogRepository auditLogRepository;
    private IdentityClient identityClient;
    private PostClient postClient;
    private NotificationClient notificationClient;
    private ModerationService service;

    private static final Long REPORTER_ID = 1L;
    private static final Long OTHER_USER_ID = 2L;
    private static final Long POST_ID = 100L;

    @BeforeEach
    void setUp() {
        reportRepository = new TestModerationRepositories.FakeReportRepository();
        auditLogRepository = new TestModerationRepositories.FakeModerationAuditLogRepository();
        identityClient = mock(IdentityClient.class);
        postClient = mock(PostClient.class);
        notificationClient = mock(NotificationClient.class);

        service = new ModerationService(
                reportRepository,
                auditLogRepository,
                identityClient,
                postClient,
                notificationClient
        );

        when(identityClient.exists(eq(OTHER_USER_ID))).thenReturn(true);
        when(postClient.get(eq(POST_ID)))
                .thenReturn(new PostClient.PostSummary(POST_ID, OTHER_USER_ID, "ACTIVE"));
    }

    @Test
    void createReportWithBothOrNeitherTargetFails() {
        var bothTarget = new ModerationService.CreateReportRequest(POST_ID, OTHER_USER_ID, "Inappropriate content");
        assertThatThrownBy(() -> service.create(REPORTER_ID, bothTarget))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException apiEx = (ApiException) ex;
                    assertThat(apiEx.status().value()).isEqualTo(400);
                    assertThat(apiEx.code()).isEqualTo("REPORT_TARGET_INVALID");
                });

        var neitherTarget = new ModerationService.CreateReportRequest(null, null, "Inappropriate content");
        assertThatThrownBy(() -> service.create(REPORTER_ID, neitherTarget))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException apiEx = (ApiException) ex;
                    assertThat(apiEx.status().value()).isEqualTo(400);
                    assertThat(apiEx.code()).isEqualTo("REPORT_TARGET_INVALID");
                });
    }

    @Test
    void userCannotReportThemselves() {
        var selfUserReport = new ModerationService.CreateReportRequest(null, REPORTER_ID, "Self harassment");
        assertThatThrownBy(() -> service.create(REPORTER_ID, selfUserReport))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException apiEx = (ApiException) ex;
                    assertThat(apiEx.status().value()).isEqualTo(400);
                    assertThat(apiEx.code()).isEqualTo("SELF_REPORT_INVALID");
                });
    }

    @Test
    void userCannotReportTheirOwnPost() {
        Long myPostId = 105L;
        when(postClient.get(eq(myPostId)))
                .thenReturn(new PostClient.PostSummary(myPostId, REPORTER_ID, "ACTIVE"));

        var selfPostReport = new ModerationService.CreateReportRequest(myPostId, null, "Regret posting this photo");
        assertThatThrownBy(() -> service.create(REPORTER_ID, selfPostReport))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException apiEx = (ApiException) ex;
                    assertThat(apiEx.status().value()).isEqualTo(400);
                    assertThat(apiEx.code()).isEqualTo("SELF_POST_REPORT_INVALID");
                });
    }

    @Test
    void cannotReportPostThatIsAlreadyDeletedOrHidden() {
        Long deletedPostId = 106L;
        when(postClient.get(eq(deletedPostId)))
                .thenReturn(new PostClient.PostSummary(deletedPostId, OTHER_USER_ID, "DELETED"));

        var reportDeleted = new ModerationService.CreateReportRequest(deletedPostId, null, "Spam post");
        assertThatThrownBy(() -> service.create(REPORTER_ID, reportDeleted))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException apiEx = (ApiException) ex;
                    assertThat(apiEx.status().value()).isEqualTo(400);
                    assertThat(apiEx.code()).isEqualTo("POST_ALREADY_INACTIVE");
                });
    }

    @Test
    void duplicatePendingReportForSamePostThrowsConflict() {
        var validReport = new ModerationService.CreateReportRequest(POST_ID, null, "Hate speech content");

        // First report succeeds
        var first = service.create(REPORTER_ID, validReport);
        assertThat(first).isNotNull();
        assertThat(first.status()).isEqualTo("PENDING");

        // Second report by same user for same post while pending -> 409 Conflict
        assertThatThrownBy(() -> service.create(REPORTER_ID, validReport))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException apiEx = (ApiException) ex;
                    assertThat(apiEx.status().value()).isEqualTo(409);
                    assertThat(apiEx.code()).isEqualTo("REPORT_DUPLICATE_PENDING");
                });
    }

    @Test
    void duplicatePendingReportForSameUserThrowsConflict() {
        var validUserReport = new ModerationService.CreateReportRequest(null, OTHER_USER_ID, "Impersonation account");

        // First report succeeds
        var first = service.create(REPORTER_ID, validUserReport);
        assertThat(first).isNotNull();
        assertThat(first.status()).isEqualTo("PENDING");

        // Second report by same user for same target user while pending -> 409 Conflict
        assertThatThrownBy(() -> service.create(REPORTER_ID, validUserReport))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException apiEx = (ApiException) ex;
                    assertThat(apiEx.status().value()).isEqualTo(409);
                    assertThat(apiEx.code()).isEqualTo("REPORT_DUPLICATE_PENDING");
                });
    }
}
