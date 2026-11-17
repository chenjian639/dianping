package com.trade.service;

import com.trade.common.PageResult;
import com.trade.dto.CreateProductDTO;
import com.trade.dto.UpdateProductDTO;
import com.trade.entity.Product;
import com.trade.exception.BusinessException;
import com.trade.mapper.ProductMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@lombok.RequiredArgsConstructor
public class ProductService {

    private final ProductMapper productMapper;
    private final InventoryService inventory;

    public List<Product> findByMerchantId(Long merchantId) {
        return productMapper.selectByMerchantId(merchantId);
    }

    @org.springframework.transaction.annotation.Transactional
    public Long createProduct(
            Long merchantId,
            CreateProductDTO dto) {

        Product product = new Product();

        product.setMerchantId(merchantId);
        product.setName(dto.getName());
        product.setDescription(dto.getDescription());
        product.setPrice(dto.getPrice());
        product.setStock(dto.getStock());

        // 新商品默认上架
        product.setStatus(1);

        productMapper.insert(product);
        inventory.record(product.getId(),product.getStock(),"OPENING","CREATE");

        return product.getId();
    }

    public void updateProduct(
            Long merchantId,
            Long productId,
            UpdateProductDTO dto) {

        int rows = productMapper.updateProduct(
                productId,
                merchantId,
                dto.getName(),
                dto.getDescription(),
                dto.getPrice()
        );

        if (rows == 0) {
            throw new BusinessException("商品不存在或无权限修改");
        }
    }

    public void updateProductStatus(
            Long merchantId,
            Long productId,
            Integer status) {

        if (status != 0 && status != 1) {
            throw new BusinessException("商品状态只能是0或1");
        }

        int rows = productMapper.updateStatus(
                productId,
                merchantId,
                status
        );

        if (rows == 0) {
            throw new BusinessException("商品不存在或无权限操作");
        }
    }

    public void deleteProduct(
            Long merchantId,
            Long productId) {

        int rows = productMapper.deleteProduct(
                productId,
                merchantId
        );

        if (rows == 0) {
            throw new BusinessException("商品不存在或无权限删除");
        }
    }

    public PageResult<Product> getMerchantProducts(
            Long merchantId,
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
                        merchantId,
                        status,
                        offset,
                        pageSize
                );

        Long total =
                productMapper.countMerchantProducts(
                        merchantId,
                        status
                );

        PageResult<Product> result = new PageResult<>();
        result.setTotal(total);
        result.setRecords(records);

        return result;
    }
}