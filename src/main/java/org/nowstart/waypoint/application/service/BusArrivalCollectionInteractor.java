package org.nowstart.waypoint.application.service;

import org.nowstart.waypoint.application.port.in.CollectBusArrivalUseCase;
import org.nowstart.waypoint.application.port.in.CollectionResult;
import org.nowstart.waypoint.application.port.out.ArrivalObservationSettings;
import org.nowstart.waypoint.application.port.out.LoadTagoArrivalPort;
import org.nowstart.waypoint.application.port.out.SaveTransitDataPort;
import org.nowstart.waypoint.domain.type.CollectionApiType;
import org.nowstart.waypoint.domain.type.CollectionStatus;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class BusArrivalCollectionInteractor implements CollectBusArrivalUseCase {

    private final TagoCityCodeResolver cityCodeResolver;
    private final ArrivalObservationSettings arrivalObservationSettings;
    private final LoadTagoArrivalPort arrivalPort;
    private final SaveTransitDataPort saveTransitDataPort;
    private final CollectionRunSupport runSupport;

    public BusArrivalCollectionInteractor(
            TagoCityCodeResolver cityCodeResolver,
            ArrivalObservationSettings arrivalObservationSettings,
            LoadTagoArrivalPort arrivalPort,
            SaveTransitDataPort saveTransitDataPort,
            CollectionRunSupport runSupport
    ) {
        this.cityCodeResolver = cityCodeResolver;
        this.arrivalObservationSettings = arrivalObservationSettings;
        this.arrivalPort = arrivalPort;
        this.saveTransitDataPort = saveTransitDataPort;
        this.runSupport = runSupport;
    }

    @Override
    public CollectionResult collectObservationStops() {
        CollectionRunSupport.CollectionRun run = runSupport.start(
                CollectionApiType.BUS_ARRIVAL,
                "changwon-observation-stop-arrivals",
                "{\"scope\":\"observation-stops\"}"
        );
        try {
            List<String> sourceNodeIds = arrivalObservationSettings.arrivalObservationSourceNodeIds();
            if (sourceNodeIds.isEmpty()) {
                return runSupport.finish(run, CollectionStatus.EMPTY, 0, 0,
                        "waypoint.collection.arrival-observation-source-node-ids 설정이 비어 있습니다.");
            }

            String cityCode = cityCodeResolver.resolve();
            int rowCount = 0;
            int failureCount = 0;
            for (String sourceNodeId : sourceNodeIds) {
                try {
                    List<LoadTagoArrivalPort.TagoBusArrival> arrivals = arrivalPort.loadArrivals(cityCode, sourceNodeId);
                    rowCount += saveTransitDataPort.saveArrivalSnapshots(cityCode, sourceNodeId, arrivals);
                } catch (RuntimeException ex) {
                    failureCount++;
                }
            }

            CollectionStatus status = status(rowCount, failureCount);
            return runSupport.finish(run, status, rowCount, failureCount,
                    "arrivals=" + rowCount + ", stopFailures=" + failureCount);
        } catch (RuntimeException ex) {
            return runSupport.fail(run, ex);
        }
    }

    private static CollectionStatus status(int rowCount, int failureCount) {
        if (rowCount == 0 && failureCount == 0) {
            return CollectionStatus.EMPTY;
        }
        return failureCount > 0 ? CollectionStatus.PARTIAL : CollectionStatus.SUCCESS;
    }
}
