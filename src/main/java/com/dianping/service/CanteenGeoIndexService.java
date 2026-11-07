package com.dianping.service;

import com.dianping.entity.Canteen;
import com.dianping.mapper.CanteenMapper;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/** Rebuildable, small-campus index. No business transaction calls this service. */
@Service
public class CanteenGeoIndexService {
    public static final String GEO_KEY = "canteen:{locations}:geo";
    public static final String READY_KEY = "canteen:{locations}:ready";
    private static final List<String> KEYS = List.of(GEO_KEY, READY_KEY);
    static final DefaultRedisScript<Long> REBUILD = new DefaultRedisScript<>("""
            local current = redis.call('GET', KEYS[2])
            if current and (#current > #ARGV[1] or (#current == #ARGV[1] and current > ARGV[1])) then
                return 0
            end
            redis.call('DEL', KEYS[2])
            redis.call('DEL', KEYS[1])
            for i = 2, #ARGV, 3 do
                redis.call('GEOADD', KEYS[1], ARGV[i], ARGV[i+1], ARGV[i+2])
            end
            redis.call('SET', KEYS[2], ARGV[1])
            return 1
            """, Long.class);
    @SuppressWarnings("rawtypes")
    private static final DefaultRedisScript<List> SEARCH = new DefaultRedisScript<>("""
            if redis.call('GET', KEYS[2]) ~= ARGV[1] then return {'NOT_READY'} end
            if redis.call('ZCARD', KEYS[1]) ~= tonumber(ARGV[2]) then return {'NOT_READY'} end
            local ids = redis.call('GEOSEARCH', KEYS[1], 'FROMLONLAT', ARGV[3], ARGV[4],
                'BYRADIUS', ARGV[5], 'm', 'ASC')
            table.insert(ids, 1, 'READY')
            return ids
            """, List.class);
    private final CanteenMapper mapper;
    private final StringRedisTemplate redis;

    public CanteenGeoIndexService(CanteenMapper mapper, StringRedisTemplate redis) {
        this.mapper = mapper;
        this.redis = redis;
    }

    /** The DB lock fences concurrent rebuilders and committed location changes across instances. */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void rebuild() {
        long revision = mapper.lockRevision();
        List<Canteen> canteens = mapper.selectEnabled();
        List<String> arguments = new ArrayList<>();
        arguments.add(String.valueOf(revision));
        for (Canteen canteen : canteens) {
            arguments.add(canteen.getLongitude().toPlainString());
            arguments.add(canteen.getLatitude().toPlainString());
            arguments.add(String.valueOf(canteen.getId()));
        }
        redis.execute(REBUILD, KEYS, arguments.toArray());
    }

    /** null means unavailable/incomplete; an empty list means a verified empty GEO result. */
    public List<Long> search(long revision, int enabledCount, double longitude, double latitude, int radius) {
        List<?> result = redis.execute(SEARCH, KEYS, String.valueOf(revision), String.valueOf(enabledCount),
                String.valueOf(longitude), String.valueOf(latitude), String.valueOf(radius + 1.0));
        if (result == null || result.isEmpty() || !"READY".equals(result.get(0))) {
            return null;
        }
        return result.stream().skip(1).map(value -> Long.valueOf(value.toString())).toList();
    }
}
