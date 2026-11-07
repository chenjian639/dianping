package com.dianping.service;

import com.dianping.dto.NearbyCanteenQuery;
import com.dianping.entity.Canteen;
import com.dianping.mapper.CanteenMapper;
import com.dianping.vo.NearbyCanteenVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Slf4j
@Service
public class NearbyCanteenService {
    // Same spherical radius as Redis GEO; one Java calculation defines both result paths.
    private static final double EARTH_RADIUS_METERS = 6372797.560856;
    private final CanteenMapper mapper;
    private final CanteenGeoIndexService index;

    public NearbyCanteenService(CanteenMapper mapper, CanteenGeoIndexService index) {
        this.mapper = mapper;
        this.index = index;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public List<NearbyCanteenVO> nearby(NearbyCanteenQuery query) {
        long revision = mapper.revision();
        List<Canteen> canteens = mapper.selectEnabled();
        Set<Long> candidates = null;
        try {
            List<Long> ids = index.search(revision, canteens.size(), query.getLongitude().doubleValue(),
                    query.getLatitude().doubleValue(), query.getRadiusMeters());
            if (ids != null && !touchesGeoBoundary(query)) {
                candidates = new HashSet<>(ids);
            }
        } catch (RuntimeException e) {
            // Do not log coordinates or persist the user's reference location.
            log.warn("食堂GEO索引不可用，本次回退数据库候选点计算, errorType={}", e.getClass().getSimpleName());
        }
        Set<Long> indexedIds = candidates;
        return canteens.stream()
                .filter(canteen -> indexedIds == null || indexedIds.contains(canteen.getId()))
                .map(canteen -> toNearby(canteen, query))
                .filter(canteen -> canteen.distanceMeters() <= query.getRadiusMeters())
                .sorted(Comparator.comparingDouble(NearbyCanteenVO::distanceMeters)
                        .thenComparing(NearbyCanteenVO::id))
                .limit(query.getLimit())
                .toList();
    }

    private NearbyCanteenVO toNearby(Canteen canteen, NearbyCanteenQuery query) {
        double distance = distanceMeters(query.getLongitude().doubleValue(), query.getLatitude().doubleValue(),
                canteen.getLongitude().doubleValue(), canteen.getLatitude().doubleValue());
        return new NearbyCanteenVO(canteen.getId(), canteen.getName(), canteen.getAddress(),
                canteen.getLongitude(), canteen.getLatitude(), canteen.getEntranceInstructions(),
                distance, "STRAIGHT_LINE", "WGS84");
    }

    private boolean touchesGeoBoundary(NearbyCanteenQuery query) {
        // GEO's grid bounding boxes can omit points at the date line / supported latitude edge.
        // Keep the accepted coordinate range and use the authoritative small-data fallback there.
        double angularRadius = (query.getRadiusMeters() + 1.0) / EARTH_RADIUS_METERS;
        double latitude = query.getLatitude().doubleValue();
        double longitudeSpan = Math.toDegrees(Math.asin(Math.min(1,
                Math.sin(angularRadius) / Math.cos(Math.toRadians(latitude)))));
        return Math.abs(query.getLongitude().doubleValue()) + longitudeSpan >= 180
                || Math.abs(latitude) + Math.toDegrees(angularRadius) >= 85.05112878;
    }

    static double distanceMeters(double fromLongitude, double fromLatitude, double toLongitude, double toLatitude) {
        double latitudeDelta = Math.toRadians(toLatitude - fromLatitude);
        double longitudeDelta = Math.toRadians(toLongitude - fromLongitude);
        double a = Math.pow(Math.sin(latitudeDelta / 2), 2)
                + Math.cos(Math.toRadians(fromLatitude)) * Math.cos(Math.toRadians(toLatitude))
                * Math.pow(Math.sin(longitudeDelta / 2), 2);
        return 2 * EARTH_RADIUS_METERS * Math.asin(Math.sqrt(Math.min(1, Math.max(0, a))));
    }
}
