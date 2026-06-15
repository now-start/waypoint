package org.nowstart.waypoint.application.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.nowstart.waypoint.application.port.in.CollectionResult;
import org.nowstart.waypoint.application.port.out.SaveTransitDataPort;
import org.nowstart.waypoint.application.port.out.TagoApiException;
import org.nowstart.waypoint.domain.type.CollectionApiType;
import org.nowstart.waypoint.domain.type.CollectionStatus;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class CollectionRunSupportTest {

    @Mock
    private SaveTransitDataPort saveTransitDataPort;

    @Test
    @DisplayName("TAGO 실패 메시지에 serviceKey가 포함되어도 저장 메시지와 결과 메시지는 마스킹한다")
    void failMasksServiceKeyInTagoErrorMessage() {
        // given: serviceKey가 포함된 TAGO 예외
        CollectionRunSupport runSupport = new CollectionRunSupport(saveTransitDataPort);
        CollectionRunSupport.CollectionRun run = new CollectionRunSupport.CollectionRun(
                1L,
                CollectionApiType.BUS_LOCATION,
                Instant.parse("2026-06-15T00:00:00Z")
        );
        TagoApiException exception = new TagoApiException(
                "TAGO API 호출에 실패했습니다.",
                0,
                "CLIENT_ERROR",
                "I/O error on GET request for \"http://example.test/path?serviceKey=secret-key&_type=json\""
        );

        // when: 수집 실패를 기록한다
        CollectionResult result = runSupport.fail(run, exception);

        // then: 저장되는 resultMessage와 errorMessage 모두 키를 마스킹한다
        ArgumentCaptor<String> resultMessage = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> errorMessage = ArgumentCaptor.forClass(String.class);
        then(saveTransitDataPort).should().finishCollectionRun(
                org.mockito.ArgumentMatchers.eq(1L),
                org.mockito.ArgumentMatchers.eq(CollectionStatus.FAILED),
                org.mockito.ArgumentMatchers.eq(0),
                org.mockito.ArgumentMatchers.eq("CLIENT_ERROR"),
                resultMessage.capture(),
                org.mockito.ArgumentMatchers.eq(0),
                errorMessage.capture()
        );
        assertThat(resultMessage.getValue()).contains("serviceKey=***");
        assertThat(errorMessage.getValue()).contains("serviceKey=***");
        assertThat(result.message()).contains("serviceKey=***");
        assertThat(result.message()).doesNotContain("secret-key");
    }
}
