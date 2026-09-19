package com.lichun.agsell.controller.user;

import com.lichun.agsell.common.BaseResponse;
import com.lichun.agsell.model.dto.SeckillOrderRequest;
import com.lichun.agsell.model.vo.OrderCreateVO;
import com.lichun.agsell.model.vo.SeckillDetailVO;
import com.lichun.agsell.model.vo.SeckillListItemVO;
import com.lichun.agsell.service.SeckillService;
import com.lichun.agsell.utils.ResultUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 用户端-秒杀
 */
@Tag(name = "用户端-秒杀", description = "秒杀活动浏览与抢购")
@RestController
@RequestMapping("/seckill")
@RequiredArgsConstructor
public class SeckillController {

    private final SeckillService seckillService;

    @Operation(summary = "秒杀活动列表（filter=1 进行中；0 未开始+进行中；不传 全部）")
    @GetMapping("/list")
    public BaseResponse<List<SeckillListItemVO>> list(@RequestParam(required = false) Integer filter) {
        return ResultUtils.success(seckillService.listActivities(filter));
    }

    @Operation(summary = "秒杀活动详情（按对外活动编号）")
    @GetMapping("/detail/{code}")
    public BaseResponse<SeckillDetailVO> detail(@PathVariable String code) {
        return ResultUtils.success(seckillService.getActivityDetail(code));
    }

    @Operation(summary = "秒杀下单（Redis 原子预扣 + 创建待付款订单）")
    @PostMapping("/order")
    public BaseResponse<OrderCreateVO> order(@RequestBody SeckillOrderRequest request) {
        return ResultUtils.success(seckillService.createSeckillOrder(request));
    }
}
