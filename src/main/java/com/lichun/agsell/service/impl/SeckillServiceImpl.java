package com.lichun.agsell.service.impl;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lichun.agsell.common.BaseContext;
import com.lichun.agsell.exception.BusinessException;
import com.lichun.agsell.exception.ErrorCode;
import com.lichun.agsell.mapper.OrderItemMapper;
import com.lichun.agsell.mapper.OrderMapper;
import com.lichun.agsell.mapper.ProductMapper;
import com.lichun.agsell.mapper.ProductSpecMapper;
import com.lichun.agsell.mapper.SeckillActivityMapper;
import com.lichun.agsell.mapper.SysUserAddressMapper;
import com.lichun.agsell.model.dto.SeckillActivityRequest;
import com.lichun.agsell.model.dto.SeckillOrderRequest;
import com.lichun.agsell.model.entity.*;
import com.lichun.agsell.model.enums.OrderStatusEnum;
import com.lichun.agsell.model.vo.OrderCreateVO;
import com.lichun.agsell.model.vo.SeckillActivityVO;
import com.lichun.agsell.model.vo.SeckillDetailVO;
import com.lichun.agsell.model.vo.SeckillListItemVO;
import com.lichun.agsell.service.SeckillRedisService;
import com.lichun.agsell.service.SeckillService;
import com.lichun.agsell.utils.ThrowUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 秒杀服务实现
 * 核心原则：Redis 管"抢购资格"（Lua 原子预扣），DB 管"订单与最终库存"（唯一索引 + 支付时乐观锁），两端最终一致。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SeckillServiceImpl implements SeckillService {

    private final SeckillActivityMapper seckillActivityMapper;
    private final ProductMapper productMapper;
    private final ProductSpecMapper productSpecMapper;
    private final OrderMapper orderMapper;
    private final OrderItemMapper orderItemMapper;
    private final SysUserAddressMapper addressMapper;
    private final SeckillRedisService seckillRedisService;

    /** 待付款订单超时分钟数（与 order.timeout-minutes 对齐） */
    @Value("${order.timeout-minutes:30}")
    private int timeoutMinutes;

    // ==================== 用户端 ====================

    @Override
    public List<SeckillListItemVO> listActivities(Integer filter) {
        LocalDateTime now = LocalDateTime.now();
        List<SeckillActivity> activities = seckillActivityMapper.selectList(
                new LambdaQueryWrapper<SeckillActivity>()
                        .eq(SeckillActivity::getStatus, 1) // 仅上架活动
                        .orderByAsc(SeckillActivity::getStartTime)
                        .orderByDesc(SeckillActivity::getSort));
        // 按状态过滤
        List<SeckillActivity> filtered = activities.stream()
                .filter(act -> {
                    if (filter == null) {
                        return true;
                    }
                    int st = calcActivityStatus(act);
                    return filter == 1 ? st == 2 : st == 1 || st == 2;
                })
                .toList();
        // 按商品分组，同一商品只保留秒杀价最低的活动（多规格在详情页选择）
        Map<Long, SeckillActivity> cheapestByProduct = new LinkedHashMap<>();
        for (SeckillActivity act : filtered) {
            Long pid = act.getProductId();
            SeckillActivity existing = cheapestByProduct.get(pid);
            if (existing == null || act.getSeckillPrice().compareTo(existing.getSeckillPrice()) < 0) {
                cheapestByProduct.put(pid, act);
            }
        }
        return cheapestByProduct.values().stream()
                .map(act -> {
                    SeckillListItemVO vo = new SeckillListItemVO();
                    vo.setId(act.getId());
                    vo.setActivityCode(act.getActivityCode());
                    vo.setProductId(act.getProductId());
                    vo.setSeckillPrice(act.getSeckillPrice());
                    vo.setSeckillStock(act.getSeckillStock());
                    vo.setSeckillLimit(act.getSeckillLimit());
                    vo.setStartTime(act.getStartTime());
                    vo.setEndTime(act.getEndTime());
                    int st = calcActivityStatus(act);
                    vo.setActivityStatus(st);
                    vo.setActivityStatusText(activityStatusText(st));
                    vo.setCountdownSeconds(calcCountdown(act, st, now));
                    // 剩余库存（Redis 实时值；无缓存回退活动总库存）
                    Integer remaining = seckillRedisService.getRemainingStock(act.getId());
                    vo.setRemainingStock(remaining != null ? remaining : act.getSeckillStock());
                    vo.setProgress(calcProgress(act.getSeckillStock(), vo.getRemainingStock()));
                    // 商品信息
                    Product product = productMapper.selectById(act.getProductId());
                    if (product != null) {
                        vo.setProductName(product.getName());
                        vo.setProductImage(product.getMainImage());
                        vo.setProductPrice(product.getPrice());
                    }
                    return vo;
                })
                .toList();
    }

    @Override
    public SeckillDetailVO getActivityDetail(String activityCode) {
        ThrowUtils.throwIf(activityCode == null || activityCode.isBlank(),
                ErrorCode.PARAMS_ERROR, "活动编号不能为空");
        SeckillActivity currentActivity = seckillActivityMapper.selectOne(
                new LambdaQueryWrapper<SeckillActivity>()
                        .eq(SeckillActivity::getActivityCode, activityCode)
                        .last("LIMIT 1"));
        ThrowUtils.throwIf(currentActivity == null, ErrorCode.NOT_FOUND_ERROR, "秒杀活动不存在");
        Product product = productMapper.selectById(currentActivity.getProductId());
        ThrowUtils.throwIf(product == null || product.getStatus() != 1,
                ErrorCode.NOT_FOUND_ERROR, "商品已下架或不存在");

        LocalDateTime now = LocalDateTime.now();
        Long userId = BaseContext.getCurrentId();

        // 查询同一商品所有上架的秒杀活动（含各规格），用于规格切换
        List<SeckillActivity> allActivities = seckillActivityMapper.selectList(
                new LambdaQueryWrapper<SeckillActivity>()
                        .eq(SeckillActivity::getProductId, currentActivity.getProductId())
                        .eq(SeckillActivity::getStatus, 1)
                        .orderByAsc(SeckillActivity::getStartTime));

        // 组装规格列表
        List<SeckillDetailVO.SeckillSpecVO> specList = allActivities.stream().map(act -> {
            SeckillDetailVO.SeckillSpecVO spec = new SeckillDetailVO.SeckillSpecVO();
            spec.setActivityCode(act.getActivityCode());
            spec.setSeckillPrice(act.getSeckillPrice());
            spec.setSeckillStock(act.getSeckillStock());
            spec.setSeckillLimit(act.getSeckillLimit());
            int st = calcActivityStatus(act);
            spec.setActivityStatus(st);
            spec.setCountdownSeconds(calcCountdown(act, st, now));
            Integer remaining = seckillRedisService.getRemainingStock(act.getId());
            spec.setRemainingStock(remaining != null ? remaining : act.getSeckillStock());
            spec.setProgress(calcProgress(act.getSeckillStock(), spec.getRemainingStock()));
            spec.setUserSeckilled(userId != null && seckillRedisService.hasUserSeckilled(act.getId(), userId));
            // 规格名称与图片
            if (act.getProductSpecId() != null) {
                ProductSpec sp = productSpecMapper.selectById(act.getProductSpecId());
                if (sp != null) {
                    spec.setSpecId(sp.getId());
                    spec.setSpecName(sp.getSpecName());
                    spec.setSpecImage(sp.getImage());
                }
            } else {
                spec.setSpecId(null);
                spec.setSpecName("默认规格");
            }
            return spec;
        }).toList();

        // 顶层字段取当前活动的数据
        SeckillDetailVO vo = new SeckillDetailVO();
        vo.setId(currentActivity.getId());
        vo.setActivityCode(currentActivity.getActivityCode());
        vo.setProductId(product.getId());
        vo.setProductName(product.getName());
        vo.setProductImage(product.getMainImage());
        vo.setProductPrice(product.getPrice());
        vo.setOrigin(product.getOrigin());
        vo.setShelfLife(product.getShelfLife());
        vo.setStorage(product.getStorage());
        vo.setSeckillPrice(currentActivity.getSeckillPrice());
        vo.setSeckillStock(currentActivity.getSeckillStock());
        vo.setSeckillLimit(currentActivity.getSeckillLimit());
        vo.setStartTime(currentActivity.getStartTime());
        vo.setEndTime(currentActivity.getEndTime());
        int st = calcActivityStatus(currentActivity);
        vo.setActivityStatus(st);
        vo.setActivityStatusText(activityStatusText(st));
        vo.setCountdownSeconds(calcCountdown(currentActivity, st, now));
        Integer remaining = seckillRedisService.getRemainingStock(currentActivity.getId());
        vo.setRemainingStock(remaining != null ? remaining : currentActivity.getSeckillStock());
        vo.setProgress(calcProgress(currentActivity.getSeckillStock(), vo.getRemainingStock()));
        vo.setUserSeckilled(userId != null && seckillRedisService.hasUserSeckilled(currentActivity.getId(), userId));
        vo.setSpecs(specList);
        return vo;
    }

    @Override
    @Transactional
    public OrderCreateVO createSeckillOrder(SeckillOrderRequest request) {
        Long userId = BaseContext.getCurrentId();
        ThrowUtils.throwIf(userId == null, ErrorCode.NOT_LOGIN_ERROR);
        ThrowUtils.throwIf(request == null || request.getActivityCode() == null || request.getActivityCode().isBlank(),
                ErrorCode.PARAMS_ERROR, "秒杀活动编号不能为空");
        ThrowUtils.throwIf(request.getAddressId() == null, ErrorCode.PARAMS_ERROR, "请选择收货地址");

        // 1. 地址校验（防止使用他人地址ID下单，与普通订单口径一致）
        SysUserAddress address = addressMapper.selectById(request.getAddressId());
        ThrowUtils.throwIf(address == null, ErrorCode.PARAMS_ERROR, "收货地址不存在");
        ThrowUtils.throwIf(!Objects.equals(address.getUserId(), userId),
                ErrorCode.NO_AUTH_ERROR, "无权使用该收货地址");

        // 2. 按对外编号定位活动（自增主键不对外暴露，防枚举爬取）
        SeckillActivity activityByCode = seckillActivityMapper.selectOne(
                new LambdaQueryWrapper<SeckillActivity>()
                        .eq(SeckillActivity::getActivityCode, request.getActivityCode())
                        .last("LIMIT 1"));
        ThrowUtils.throwIf(activityByCode == null, ErrorCode.NOT_FOUND_ERROR, "秒杀活动不存在");
        Long activityId = activityByCode.getId();

        // 3. 读活动快照（Redis 优先，无则 DB 加载回填）
        SeckillActivity activity = seckillRedisService.getSnapshot(activityId);
        if (activity == null) {
            activity = seckillActivityMapper.selectById(activityId);
            ThrowUtils.throwIf(activity == null, ErrorCode.NOT_FOUND_ERROR, "秒杀活动不存在");
            seckillRedisService.saveSnapshot(activity);
        }

        // 4. 活动状态校验
        ThrowUtils.throwIf(activity.getStatus() == null || activity.getStatus() != 1,
                ErrorCode.SECKILL_ENDED, "秒杀活动已下架");
        LocalDateTime now = LocalDateTime.now();
        ThrowUtils.throwIf(now.isBefore(activity.getStartTime()), ErrorCode.SECKILL_NOT_STARTED);
        ThrowUtils.throwIf(now.isAfter(activity.getEndTime()), ErrorCode.SECKILL_ENDED);

        // 5. 商品校验（服务端重新查询，不信任快照中的商品快照状态）
        Product product = productMapper.selectById(activity.getProductId());
        ThrowUtils.throwIf(product == null || product.getStatus() != 1,
                ErrorCode.NOT_FOUND_ERROR, "商品已下架或不存在");

        // 6. Lua 原子抢购（防超卖 + 防重复）
        int result = seckillRedisService.trySeckill(activityId, userId);
        if (result == 0) {
            throw new BusinessException(ErrorCode.SECKILL_SOLD_OUT);
        }
        if (result == -2) {
            throw new BusinessException(ErrorCode.SECKILL_REPEAT);
        }
        if (result == -1) {
            // 缓存异常补偿：重新从 DB 加载并回填快照后再试一次
            activity = seckillActivityMapper.selectById(activityId);
            if (activity == null) {
                throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "秒杀活动不存在");
            }
            seckillRedisService.saveSnapshot(activity);
            result = seckillRedisService.trySeckill(activityId, userId);
            if (result == 0) {
                throw new BusinessException(ErrorCode.SECKILL_SOLD_OUT);
            }
            if (result == -2) {
                throw new BusinessException(ErrorCode.SECKILL_REPEAT);
            }
            if (result != 1) {
                throw new BusinessException(ErrorCode.SYSTEM_ERROR, "秒杀服务异常，请稍后再试");
            }
        }

        // 7. 构建秒杀订单（服务端计价：金额=秒杀价，数量=1，不信任前端）
        try {
            String orderNo = "AGS" + IdUtil.getSnowflakeNextIdStr();
            Order order = new Order();
            order.setOrderNo(orderNo);
            order.setUserId(userId);
            order.setTotalAmount(activity.getSeckillPrice());
            order.setFreight(BigDecimal.ZERO);
            order.setDiscount(BigDecimal.ZERO);
            order.setPayAmount(activity.getSeckillPrice());
            order.setStatus(OrderStatusEnum.PENDING_PAYMENT.getCode());
            order.setAddressId(address.getId());
            order.setReceiver(address.getReceiver());
            order.setPhone(address.getPhone());
            order.setAddress(address.getProvince() + address.getCity()
                    + address.getDistrict() + address.getDetail());
            order.setRemark(request.getRemark());
            order.setSeckillActivityId(activityId);
            orderMapper.insert(order);

            OrderItem item = new OrderItem();
            item.setOrderId(order.getId());
            item.setProductId(product.getId());
            item.setSpecId(activity.getProductSpecId());
            item.setProductName(product.getName());
            item.setProductImage(product.getMainImage());
            item.setSpecName(resolveSpecName(activity.getProductSpecId()));
            item.setPrice(activity.getSeckillPrice());
            item.setQuantity(1);
            item.setSubtotal(activity.getSeckillPrice());
            orderItemMapper.insert(item);

            OrderCreateVO vo = new OrderCreateVO();
            vo.setOrderNo(orderNo);
            vo.setPayAmount(activity.getSeckillPrice());
            vo.setTotalAmount(activity.getSeckillPrice());
            vo.setStatus(OrderStatusEnum.PENDING_PAYMENT.getCode());
            vo.setCreateTime(order.getCreateTime());
            vo.setExpireSeconds(computeExpireSeconds(order.getCreateTime()));
            return vo;
        } catch (DuplicateKeyException e) {
            // 唯一索引 uk_user_seckill 兜底：一人一单（补偿 Redis 名额）
            seckillRedisService.releaseSeckill(activityId, userId);
            throw new BusinessException(ErrorCode.SECKILL_REPEAT);
        } catch (Exception e) {
            // DB 异常：回补 Redis 名额，避免名额丢失
            log.error("[Seckill] 秒杀订单创建失败, activityId={}, userId={}", activityId, userId, e);
            seckillRedisService.releaseSeckill(activityId, userId);
            throw e;
        }
    }

    @Override
    public void releaseSeckillQuota(Long activityId, Long userId) {
        seckillRedisService.releaseSeckill(activityId, userId);
    }

    // ==================== 管理端 ====================

    @Override
    public Page<SeckillActivityVO> pageActivities(int pageNum, int pageSize, String keyword, Integer status) {
        LambdaQueryWrapper<SeckillActivity> wrapper = new LambdaQueryWrapper<>();
        if (status != null) {
            wrapper.eq(SeckillActivity::getStatus, status);
        }
        wrapper.orderByDesc(SeckillActivity::getCreateTime);
        Page<SeckillActivity> page = seckillActivityMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);

        Page<SeckillActivityVO> result = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        result.setRecords(page.getRecords().stream().map(act -> {
            SeckillActivityVO vo = new SeckillActivityVO();
            vo.setId(act.getId());
            vo.setActivityCode(act.getActivityCode());
            vo.setProductId(act.getProductId());
            vo.setProductSpecId(act.getProductSpecId());
            vo.setSeckillPrice(act.getSeckillPrice());
            vo.setSeckillStock(act.getSeckillStock());
            vo.setSeckillLimit(act.getSeckillLimit());
            vo.setStartTime(act.getStartTime());
            vo.setEndTime(act.getEndTime());
            vo.setStatus(act.getStatus());
            vo.setSort(act.getSort());
            vo.setCreateTime(act.getCreateTime());
            int st = calcActivityStatus(act);
            vo.setActivityStatusText(activityStatusText(st));
            Integer remaining = seckillRedisService.getRemainingStock(act.getId());
            vo.setRemainingStock(remaining != null ? remaining : act.getSeckillStock());
            // 关键字过滤（商品名）
            Product product = productMapper.selectById(act.getProductId());
            if (product != null) {
                vo.setProductName(product.getName());
                vo.setProductImage(product.getMainImage());
                vo.setProductPrice(product.getPrice());
                if (keyword != null && !keyword.isBlank()
                        && !product.getName().contains(keyword)) {
                    return null;
                }
            }
            return vo;
        }).filter(Objects::nonNull).toList());
        return result;
    }

    @Override
    public Long createActivity(SeckillActivityRequest request) {
        SeckillActivity activity = new SeckillActivity();
        applyAndValidate(request, activity, null);
        // 对外活动编号：随机 16 位大写字母数字，不可枚举（唯一索引兜底，冲突重试）
        activity.setActivityCode(generateActivityCode());
        activity.setStatus(request.getStatus() != null ? request.getStatus() : 1);
        activity.setSort(request.getSort() != null ? request.getSort() : 0);
        seckillActivityMapper.insert(activity);
        seckillRedisService.saveSnapshot(activity);
        log.info("[Seckill] 创建秒杀活动, id={}, productId={}", activity.getId(), activity.getProductId());
        return activity.getId();
    }

    @Override
    public void updateActivity(Long id, SeckillActivityRequest request) {
        SeckillActivity activity = seckillActivityMapper.selectById(id);
        ThrowUtils.throwIf(activity == null, ErrorCode.NOT_FOUND_ERROR, "秒杀活动不存在");

        // 已上架且正在进行的活动中止编辑库存/时间（防止活动进行中改库存造成 Redis 与 DB 不一致）；
        // 下架/未开始/已结束的活动允许编辑
        LocalDateTime now = LocalDateTime.now();
        boolean started = activity.getStatus() != null && activity.getStatus() == 1
                && !now.isBefore(activity.getStartTime()) && now.isBefore(activity.getEndTime());
        ThrowUtils.throwIf(started, ErrorCode.OPERATION_ERROR, "秒杀已开始，不允许修改库存与时间");

        applyAndValidate(request, activity, id);
        seckillActivityMapper.updateById(activity);
        seckillRedisService.saveSnapshot(activity);
        log.info("[Seckill] 编辑秒杀活动, id={}", id);
    }

    @Override
    public void deleteActivity(Long id) {
        SeckillActivity activity = seckillActivityMapper.selectById(id);
        ThrowUtils.throwIf(activity == null, ErrorCode.NOT_FOUND_ERROR, "秒杀活动不存在");
        seckillActivityMapper.deleteById(id);
        seckillRedisService.deleteSnapshot(id);
        log.info("[Seckill] 删除秒杀活动, id={}", id);
    }

    @Override
    public void updateActivityStatus(Long id, Integer status) {
        ThrowUtils.throwIf(status == null || (status != 0 && status != 1),
                ErrorCode.PARAMS_ERROR, "状态值无效");
        SeckillActivity activity = seckillActivityMapper.selectById(id);
        ThrowUtils.throwIf(activity == null, ErrorCode.NOT_FOUND_ERROR, "秒杀活动不存在");
        activity.setStatus(status);
        seckillActivityMapper.updateById(activity);
        seckillRedisService.updateStatusSnapshot(id, status);
        log.info("[Seckill] 秒杀活动状态变更, id={}, status={}", id, status);
    }

    // ==================== 私有方法 ====================

    /**
     * 生成不可枚举的活动编号（16 位大写字母数字，唯一索引兜底冲突重试）
     */
    private String generateActivityCode() {
        for (int i = 0; i < 5; i++) {
            String code = RandomUtil.randomStringUpper(16);
            Long exists = seckillActivityMapper.selectCount(
                    new LambdaQueryWrapper<SeckillActivity>()
                            .eq(SeckillActivity::getActivityCode, code));
            if (exists == null || exists == 0) {
                return code;
            }
        }
        throw new BusinessException(ErrorCode.SYSTEM_ERROR, "活动编号生成失败，请重试");
    }

    /**
     * 表单校验并回填实体（创建/编辑共用）
     *
     * @param selfId 编辑时传入自身ID用于区间重叠排除；创建传 null
     */
    private void applyAndValidate(SeckillActivityRequest request, SeckillActivity activity, Long selfId) {
        ThrowUtils.throwIf(request.getProductId() == null, ErrorCode.PARAMS_ERROR, "请选择秒杀商品");
        ThrowUtils.throwIf(request.getSeckillPrice() == null || request.getSeckillPrice().compareTo(BigDecimal.ZERO) <= 0,
                ErrorCode.PARAMS_ERROR, "秒杀价必须大于0");
        ThrowUtils.throwIf(request.getSeckillStock() == null || request.getSeckillStock() <= 0,
                ErrorCode.PARAMS_ERROR, "秒杀库存必须大于0");
        ThrowUtils.throwIf(request.getStartTime() == null || request.getEndTime() == null,
                ErrorCode.PARAMS_ERROR, "请选择活动起止时间");
        ThrowUtils.throwIf(!request.getEndTime().isAfter(request.getStartTime()),
                ErrorCode.PARAMS_ERROR, "结束时间必须晚于开始时间");
        int limit = request.getSeckillLimit() == null ? 1 : request.getSeckillLimit();
        ThrowUtils.throwIf(limit <= 0, ErrorCode.PARAMS_ERROR, "每人限购数量必须大于0");

        Product product = productMapper.selectById(request.getProductId());
        ThrowUtils.throwIf(product == null, ErrorCode.NOT_FOUND_ERROR, "商品不存在");
        ThrowUtils.throwIf(product.getStatus() != 1, ErrorCode.PARAMS_ERROR, "商品已下架，不能创建秒杀");

        // 定价基准：绑定规格按规格价校验，否则按商品主价校验
        ProductSpec spec = null;
        if (request.getProductSpecId() != null) {
            spec = productSpecMapper.selectById(request.getProductSpecId());
            ThrowUtils.throwIf(spec == null || !Objects.equals(spec.getProductId(), product.getId()),
                    ErrorCode.PARAMS_ERROR, "商品规格不存在");
        }
        BigDecimal basePrice = spec != null ? spec.getPrice() : product.getPrice();
        ThrowUtils.throwIf(request.getSeckillPrice().compareTo(basePrice) >= 0,
                ErrorCode.PARAMS_ERROR, "秒杀价必须低于商品现价");

        // 库存上限：绑定规格则 ≤ 规格库存，否则 ≤ 商品库存
        if (spec != null) {
            ThrowUtils.throwIf(request.getSeckillStock() > spec.getStock(),
                    ErrorCode.PARAMS_ERROR, "秒杀库存不能超过规格库存");
        } else {
            ThrowUtils.throwIf(request.getSeckillStock() > product.getStock(),
                    ErrorCode.PARAMS_ERROR, "秒杀库存不能超过商品库存");
        }

        // 同一商品同一规格时间区间不允许重叠（不同规格可同时段）
        LambdaQueryWrapper<SeckillActivity> overlapQuery = new LambdaQueryWrapper<SeckillActivity>()
                .eq(SeckillActivity::getProductId, request.getProductId())
                .eq(request.getProductSpecId() != null, SeckillActivity::getProductSpecId, request.getProductSpecId())
                .isNull(request.getProductSpecId() == null, SeckillActivity::getProductSpecId)
                .le(SeckillActivity::getStartTime, request.getEndTime())
                .ge(SeckillActivity::getEndTime, request.getStartTime());
        if (selfId != null) {
            overlapQuery.ne(SeckillActivity::getId, selfId);
        }
        Long overlapCount = seckillActivityMapper.selectCount(overlapQuery);
        ThrowUtils.throwIf(overlapCount != null && overlapCount > 0,
                ErrorCode.PARAMS_ERROR, "该商品规格在此时段已存在秒杀活动，时间区间不允许重叠");

        activity.setProductId(request.getProductId());
        activity.setProductSpecId(request.getProductSpecId());
        activity.setSeckillPrice(request.getSeckillPrice());
        activity.setSeckillStock(request.getSeckillStock());
        activity.setSeckillLimit(limit);
        activity.setStartTime(request.getStartTime());
        activity.setEndTime(request.getEndTime());
        activity.setSort(request.getSort() != null ? request.getSort() : 0);
    }

    /**
     * 活动状态：1=未开始 2=进行中 3=已结束（下架视为已结束）
     */
    private int calcActivityStatus(SeckillActivity activity) {
        if (activity.getStatus() == null || activity.getStatus() != 1) {
            return 3;
        }
        LocalDateTime now = LocalDateTime.now();
        if (now.isBefore(activity.getStartTime())) {
            return 1;
        }
        if (now.isAfter(activity.getEndTime())) {
            return 3;
        }
        return 2;
    }

    private String activityStatusText(int status) {
        return switch (status) {
            case 1 -> "未开始";
            case 2 -> "进行中";
            default -> "已结束";
        };
    }

    /**
     * 倒计时秒数：未开始=距开始，进行中=距结束，已结束=0
     */
    private long calcCountdown(SeckillActivity activity, int status, LocalDateTime now) {
        return switch (status) {
            case 1 -> Math.max(0, Duration.between(now, activity.getStartTime()).getSeconds());
            case 2 -> Math.max(0, Duration.between(now, activity.getEndTime()).getSeconds());
            default -> 0L;
        };
    }

    /**
     * 库存售出进度 0~100
     */
    private int calcProgress(Integer total, Integer remaining) {
        if (total == null || total <= 0) {
            return 100;
        }
        int safeRemaining = remaining == null ? total : Math.min(remaining, total);
        return Math.round((total - safeRemaining) * 100f / total);
    }

    private String resolveSpecName(Long specId) {
        if (specId == null) {
            return null;
        }
        ProductSpec spec = productSpecMapper.selectById(specId);
        return spec != null ? spec.getSpecName() : null;
    }

    /**
     * 待付款订单剩余支付秒数（与普通订单口径一致）
     */
    private long computeExpireSeconds(LocalDateTime createTime) {
        if (createTime == null) {
            return 0L;
        }
        return Math.max(0L, Duration.between(LocalDateTime.now(),
                createTime.plusMinutes(timeoutMinutes)).getSeconds());
    }
}
