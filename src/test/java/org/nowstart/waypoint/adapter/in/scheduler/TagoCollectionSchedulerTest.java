package org.nowstart.waypoint.adapter.in.scheduler;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.nowstart.waypoint.application.port.in.CollectBusLocationUseCase;
import org.nowstart.waypoint.application.port.in.CollectionResult;
import org.nowstart.waypoint.application.service.ReferenceDataCollectionState;
import org.nowstart.waypoint.domain.type.CollectionApiType;
import org.nowstart.waypoint.domain.type.CollectionStatus;

import java.time.Instant;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verify;

class TagoCollectionSchedulerTest {

    @Test
    @DisplayName("시작 기준정보 수집 중이면 위치 스케줄러는 실행하지 않는다")
    void collectLocationsSkipsWhileStartupReferenceDataIsRunning() {
        // given: 시작 기준정보 수집이 아직 진행 중인 상태
        CollectBusLocationUseCase useCase = mock(CollectBusLocationUseCase.class);
        ReferenceDataCollectionState collectionState = new ReferenceDataCollectionState();
        TagoCollectionScheduler scheduler = new TagoCollectionScheduler(useCase, collectionState);

        // when: 위치 스케줄러가 실행된다
        scheduler.collectLocations();

        // then: 기준정보 없는 상태로 위치 수집을 먼저 실행하지 않는다
        verifyNoInteractions(useCase);
    }

    @Test
    @DisplayName("시작 기준정보 수집 중이어도 노선 기준정보가 준비되면 위치 스케줄러를 실행한다")
    void collectLocationsRunsAfterStartupRoutesAreReady() {
        // given: 시작 기준정보 수집 중 노선 기준정보 저장이 끝난 상태
        CollectBusLocationUseCase useCase = mock(CollectBusLocationUseCase.class);
        ReferenceDataCollectionState collectionState = new ReferenceDataCollectionState();
        collectionState.markRoutesReady();
        TagoCollectionScheduler scheduler = new TagoCollectionScheduler(useCase, collectionState);
        CollectionResult result = new CollectionResult(
                CollectionApiType.BUS_LOCATION,
                CollectionStatus.SUCCESS,
                1,
                0,
                "locations=1, routeFailures=0",
                Instant.parse("2026-06-15T00:00:00Z"),
                Instant.parse("2026-06-15T00:00:01Z")
        );
        given(useCase.collect()).willReturn(result);

        // when: 위치 스케줄러가 실행된다
        scheduler.collectLocations();

        // then: 실제 위치 수집을 실행한다
        verify(useCase).collect();
    }

    @Test
    @DisplayName("시작 기준정보 수집이 끝나면 노선 준비 여부와 무관하게 위치 스케줄러를 실행한다")
    void collectLocationsRunsAfterStartupReferenceDataIsFinished() {
        // given: 시작 기준정보 수집이 끝난 상태
        CollectBusLocationUseCase useCase = mock(CollectBusLocationUseCase.class);
        ReferenceDataCollectionState collectionState = new ReferenceDataCollectionState();
        collectionState.markStartupFinished();
        TagoCollectionScheduler scheduler = new TagoCollectionScheduler(useCase, collectionState);
        CollectionResult result = new CollectionResult(
                CollectionApiType.BUS_LOCATION,
                CollectionStatus.EMPTY,
                0,
                0,
                "수집된 노선 기준 데이터가 없습니다.",
                Instant.parse("2026-06-15T00:00:00Z"),
                Instant.parse("2026-06-15T00:00:01Z")
        );
        given(useCase.collect()).willReturn(result);

        // when: 위치 스케줄러가 실행된다
        scheduler.collectLocations();

        // then: 실제 위치 수집을 실행한다
        verify(useCase).collect();
    }
}
