package com.earmo.onlyoffice.integration.model;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.Collections;
import java.util.List;

@Data
@Accessors(chain = true)
@Schema(description = "分页响应结果")
public class PageRespVo<T> {

    @Schema(description = "分页结果列表")
    private List<T> result;

    @Schema(description = "每页条数")
    private int pageSize;

    @Schema(description = "当前页码")
    private int currentPage;

    @Schema(description = "总记录数")
    private int totalCount;

    public PageRespVo() {
        this.result = Collections.emptyList();
    }

    public PageRespVo(int currentPage, int pageSize, int totalCount, List<T> result) {
        this.currentPage = currentPage;
        this.pageSize = pageSize;
        this.totalCount = totalCount;
        this.result = result == null ? Collections.emptyList() : result;
    }
}
