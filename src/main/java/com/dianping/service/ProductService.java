package com.dianping.service;

import com.dianping.common.PageResult;
import com.dianping.dto.CreateProductDTO;
import com.dianping.dto.UpdateProductDTO;
import com.dianping.entity.Product;
import com.dianping.exception.BusinessException;
import com.dianping.mapper.ProductMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@lombok.RequiredArgsConstructor
public class ProductService {

    private final ProductMapper productMapper;
    private final InventoryService inventory;

    public List<Product> findByRestaurantId(Long restaurantId) {
        return productMapper.selectByRestaurantId(restaurantId);
    }

    @org.springframework.transaction.annotation.Transactional
    public Long createProduct(
            Long restaurantId,
            CreateProductDTO dto) {

        Product dish = new Product();

        dish.setRestaurantId(restaurantId);
        dish.setName(dto.getName());
        dish.setDescription(dto.getDescription());
        dish.setPrice(dto.getPrice());
        dish.setStock(dto.getStock());

        // 新商品默认上架
        dish.setStatus(1);

        productMapper.insert(dish);
        inventory.record(dish.getId(),dish.getStock(),"OPENING","CREATE");

        return dish.getId();
    }

    public void updateProduct(
            Long restaurantId,
            Long dishId,
            UpdateProductDTO dto) {

        int rows = productMapper.updateProduct(
                dishId,
                restaurantId,
                dto.getName(),
                dto.getDescription(),
                dto.getPrice()
        );

        if (rows == 0) {
            throw new BusinessException("商品不存在或无权限修改");
        }
    }

    public void updateProductStatus(
            Long restaurantId,
            Long dishId,
            Integer status) {

        if (status != 0 && status != 1) {
            throw new BusinessException("商品状态只能是0或1");
        }

        int rows = productMapper.updateStatus(
                dishId,
                restaurantId,
                status
        );

        if (rows == 0) {
            throw new BusinessException("商品不存在或无权限操作");
        }
    }

    public void deleteProduct(
            Long restaurantId,
            Long dishId) {

        int rows = productMapper.deleteProduct(
                dishId,
                restaurantId
        );

        if (rows == 0) {
            throw new BusinessException("商品不存在或无权限删除");
        }
    }

    public PageResult<Product> getMerchantProducts(
            Long restaurantId,
            Integer page,
            Integer pageSize,
            Integer status) {

        if (page < 1) {
            throw new BusinessException("page不能小于1");
        }

        if (pageSize < 1 || pageSize > 100) {
            throw new BusinessException("pageSize必须在1到100之间");
        }

        if (status != null && status != 0 && status != 1) {
            throw new BusinessException("商品状态只能是0或1");
        }

        int offset = (page - 1) * pageSize;

        List<Product> records =
                productMapper.selectMerchantProductsPage(
                        restaurantId,
                        status,
                        offset,
                        pageSize
                );

        Long total =
                productMapper.countMerchantProducts(
                        restaurantId,
                        status
                );

        PageResult<Product> result = new PageResult<>();
        result.setTotal(total);
        result.setRecords(records);

        return result;
    }
}