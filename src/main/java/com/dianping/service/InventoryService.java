package com.dianping.service;
import com.dianping.entity.Product;
import com.dianping.mapper.*;
import com.dianping.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.util.*;
@Service
@RequiredArgsConstructor
public class InventoryService {
    private final InventoryMapper movements;
    private final ProductMapper products;

    @Transactional(propagation=Propagation.MANDATORY)
    public void record(Long productId,int delta,String reason,String key) {
        movements.record(productId,delta,reason,key);
    }

    @Transactional
    public void adjust(Long merchantId,Long productId,String requestId,int delta) {
        Product product=products.lockById(productId);
        requireOwner(product,merchantId);
        Integer previous=movements.adjustment(productId,requestId);
        if (previous!=null) {
            if (previous!=delta) throw new BusinessException("幂等键已用于不同调整数量");
            return;
        }
        if (delta==0 || movements.adjust(productId,delta)!=1) throw new BusinessException("库存调整超出范围");
        movements.record(productId,delta,"ADJUST",requestId);
    }

    public List<Map<String,Object>> history(Long merchantId,Long productId) {
        requireOwner(products.selectById(productId),merchantId);
        return movements.history(productId);
    }

    private void requireOwner(Product product,Long merchantId) {
        if (product==null || !Objects.equals(product.getRestaurantId(),merchantId))
            throw new BusinessException("商品不存在或无权限");
    }
}
