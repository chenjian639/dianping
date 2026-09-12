package com.dianping.common;

import lombok.Data;

import java.util.List;

@Data
public class PageResult<T> {

    private Long total;

    private Boolean hasNext;

    private List<T> records;
}