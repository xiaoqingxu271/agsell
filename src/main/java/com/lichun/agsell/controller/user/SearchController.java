package com.lichun.agsell.controller.user;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lichun.agsell.common.BaseResponse;
import com.lichun.agsell.model.dto.SearchRequest;
import com.lichun.agsell.model.vo.ProductSearchVO;
import com.lichun.agsell.service.HotWordService;
import com.lichun.agsell.service.SearchService;
import com.lichun.agsell.utils.ResultUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "用户端-搜索接口", description = "商品搜索（全文检索+高亮）、热门搜索词")
@RestController
@RequestMapping("/product")
@RequiredArgsConstructor
public class SearchController {

    private final SearchService searchService;
    private final HotWordService hotWordService;

    @Operation(summary = "商品搜索（全文检索+高亮）")
    @GetMapping("/search")
    public BaseResponse<Page<ProductSearchVO>> search(SearchRequest request) {
        return ResultUtils.success(searchService.search(request));
    }

    @Operation(summary = "热门搜索词")
    @GetMapping("/search/hot")
    public BaseResponse<List<String>> hotWords(@RequestParam(defaultValue = "10") int limit) {
        return ResultUtils.success(hotWordService.listHotWords(limit));
    }
}
