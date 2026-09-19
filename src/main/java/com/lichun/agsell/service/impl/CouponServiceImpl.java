package com.lichun.agsell.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lichun.agsell.common.BaseContext;
import com.lichun.agsell.exception.BusinessException;
import com.lichun.agsell.exception.ErrorCode;
import com.lichun.agsell.mapper.CouponMapper;
import com.lichun.agsell.mapper.CouponProductMapper;
import com.lichun.agsell.mapper.UserCouponMapper;
import com.lichun.agsell.model.entity.Coupon;
import com.lichun.agsell.model.entity.CouponProduct;
import com.lichun.agsell.model.entity.UserCoupon;
import com.lichun.agsell.model.dto.CouponCreateRequest;
import com.lichun.agsell.model.vo.CouponAvailableVO;
import com.lichun.agsell.model.vo.CouponListItemVO;
import com.lichun.agsell.model.vo.CouponVerifyVO;
import com.lichun.agsell.model.vo.CouponVO;
import com.lichun.agsell.model.vo.UserCouponVO;
import com.lichun.agsell.service.CouponRedisService;
import com.lichun.agsell.service.CouponService;
import com.lichun.agsell.utils.ThrowUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 优惠券服务实现
 * 核心原则：Redis 管「领取资格」（Lua 原子预扣），DB 管「核销与最终状态」（条件 UPDATE + 唯一索引），两端最终一致。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CouponServiceImpl implements CouponService {

    private final CouponMapper couponMapper;
    private final UserCouponMapper userCouponMapper;
    private final CouponProductMapper couponProductMapper;
    private final CouponRedisService couponRedisService;

    /** 券类型：满减券 */
    private static final int TYPE_FULL_REDUCTION = 1;
    /** 券类型：折扣券 */
    private static final int TYPE_DISCOUNT = 2;
    /** 券类型：品类满减券 */
    private static final int TYPE_CATEGORY = 3;

    // ==================== 用户端 ====================

    @Override
    public List<CouponListItemVO> listCoupons() {
        LocalDateTime now = LocalDateTime.now();
        List<Coupon> coupons = couponMapper.selectList(new LambdaQueryWrapper<Coupon>()
                .eq(Coupon::getStatus, 1) // 仅上架券
                .and(w -> w.isNull(Coupon::getStartTime).or().le(Coupon::getStartTime, now))
                .and(w -> w.isNull(Coupon::getEndTime).or().ge(Coupon::getEndTime, now))
                .orderByDesc(Coupon::getSort));
        // 限量券已领完的不再展示
        List<Coupon> available = coupons.stream()
                .filter(c -> c.getTotalCount() == null || c.getTotalCount() <= 0
                        || c.getReceivedCount() == null || c.getReceivedCount() < c.getTotalCount())
                .toList();

        // 当前用户已领标记（DB 为准，Redis 仅作并发预检）
        Long userId = BaseContext.getCurrentId();
        Set<Long> receivedCouponIds = new HashSet<>();
        if (userId != null && !available.isEmpty()) {
            List<Long> couponIds = available.stream().map(Coupon::getId).toList();
            receivedCouponIds = userCouponMapper.selectList(new LambdaQueryWrapper<UserCoupon>()
                            .eq(UserCoupon::getUserId, userId)
                            .in(UserCoupon::getCouponId, couponIds))
                    .stream().map(UserCoupon::getCouponId).collect(Collectors.toSet());
        }

        Set<Long> finalReceived = receivedCouponIds;
        return available.stream().map(c -> {
            CouponListItemVO vo = new CouponListItemVO();
            vo.setId(c.getId());
            vo.setCouponName(c.getCouponName());
            vo.setCouponType(c.getCouponType());
            vo.setThreshold(c.getThreshold());
            vo.setAmount(c.getAmount());
            vo.setDiscount(c.getDiscount());
            vo.setTotalCount(c.getTotalCount());
            vo.setReceivedCount(c.getReceivedCount());
            vo.setValidDays(c.getValidDays());
            vo.setStartTime(c.getStartTime());
            vo.setEndTime(c.getEndTime());
            vo.setReceived(finalReceived.contains(c.getId()));
            return vo;
        }).toList();
    }

    @Override
    @Transactional
    public UserCouponVO receiveCoupon(Long couponId) {
        Long userId = BaseContext.getCurrentId();
        ThrowUtils.throwIf(userId == null, ErrorCode.NOT_LOGIN_ERROR);
        ThrowUtils.throwIf(couponId == null, ErrorCode.PARAMS_ERROR, "优惠券ID不能为空");

        // 1. 查券模板并校验领取时间/上架状态
        Coupon coupon = couponMapper.selectById(couponId);
        ThrowUtils.throwIf(coupon == null, ErrorCode.COUPON_NOT_FOUND);
        ThrowUtils.throwIf(coupon.getStatus() == null || coupon.getStatus() != 1,
                ErrorCode.COUPON_NOT_FOUND);
        LocalDateTime now = LocalDateTime.now();
        if (coupon.getStartTime() != null && now.isBefore(coupon.getStartTime())) {
            throw new BusinessException(ErrorCode.COUPON_RECEIVE_END);
        }
        if (coupon.getEndTime() != null && now.isAfter(coupon.getEndTime())) {
            throw new BusinessException(ErrorCode.COUPON_RECEIVE_END);
        }

        boolean limited = coupon.getTotalCount() != null && coupon.getTotalCount() > 0;
        // 2. Lua 原子领取（防超发 + 防重复）
        int result = couponRedisService.tryReceive(couponId, userId, limited);
        if (result == 0) {
            throw new BusinessException(ErrorCode.COUPON_SOLD_OUT);
        }
        if (result == -2) {
            throw new BusinessException(ErrorCode.COUPON_RECEIVE_REPEAT);
        }
        if (result == -1) {
            // 缓存异常补偿：以 DB 为准重建剩余量缓存后重试一次
            coupon = couponMapper.selectById(couponId);
            if (coupon == null) {
                throw new BusinessException(ErrorCode.COUPON_NOT_FOUND);
            }
            couponRedisService.syncStock(coupon);
            result = couponRedisService.tryReceive(couponId, userId, limited);
            if (result == 0) {
                throw new BusinessException(ErrorCode.COUPON_SOLD_OUT);
            }
            if (result == -2) {
                throw new BusinessException(ErrorCode.COUPON_RECEIVE_REPEAT);
            }
            if (result != 1) {
                throw new BusinessException(ErrorCode.SYSTEM_ERROR, "领取服务异常，请稍后再试");
            }
        }

        // 3. 生成用户券（唯一索引 uk_user_coupon 兜底防重）
        try {
            int validDays = coupon.getValidDays() == null ? 7 : coupon.getValidDays();
            UserCoupon uc = new UserCoupon();
            uc.setUserId(userId);
            uc.setCouponId(couponId);
            uc.setStatus(0);
            uc.setExpireTime(now.plusDays(validDays));
            uc.setReceiveTime(now);
            userCouponMapper.insert(uc);

            // 4. 冗余计数（与 Redis 剩余量口径一致）
            couponMapper.update(null, new LambdaUpdateWrapper<Coupon>()
                    .setSql("received_count = received_count + 1")
                    .eq(Coupon::getId, couponId));
            return toUserCouponVO(uc, coupon);
        } catch (DuplicateKeyException e) {
            // 并发/历史数据兜底：补偿 Redis（回补剩余量 + 清除标记）
            couponRedisService.releaseReceive(couponId, userId, limited);
            throw new BusinessException(ErrorCode.COUPON_RECEIVE_REPEAT);
        } catch (Exception e) {
            log.error("[Coupon] 领取优惠券失败, couponId={}, userId={}", couponId, userId, e);
            couponRedisService.releaseReceive(couponId, userId, limited);
            throw e;
        }
    }

    @Override
    public Page<UserCouponVO> myCoupons(int pageNum, int pageSize, Integer status) {
        Long userId = BaseContext.getCurrentId();
        ThrowUtils.throwIf(userId == null, ErrorCode.NOT_LOGIN_ERROR);

        LambdaQueryWrapper<UserCoupon> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserCoupon::getUserId, userId);
        if (status != null) {
            wrapper.eq(UserCoupon::getStatus, status);
        }
        wrapper.orderByDesc(UserCoupon::getReceiveTime);

        Page<UserCoupon> page = userCouponMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);

        // 批量查询券模板（软删模板的券不展示；deleted 由逻辑删除自动过滤）
        List<Long> couponIds = page.getRecords().stream()
                .map(UserCoupon::getCouponId).distinct().toList();
        Map<Long, Coupon> couponMap = couponIds.isEmpty() ? Map.of()
                : couponMapper.selectList(new LambdaQueryWrapper<Coupon>()
                        .in(Coupon::getId, couponIds))
                .stream().collect(Collectors.toMap(Coupon::getId, Function.identity()));

        // 实时过期修正：未使用且已过期 → 按已过期展示（懒收敛，正确性不依赖状态字段）
        LocalDateTime now = LocalDateTime.now();
        Page<UserCouponVO> result = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        List<UserCouponVO> records = new ArrayList<>();
        for (UserCoupon uc : page.getRecords()) {
            Coupon coupon = couponMap.get(uc.getCouponId());
            if (coupon == null) {
                continue; // 模板已删除，券不再展示
            }
            int st = uc.getStatus();
            boolean expired = st == 0 && uc.getExpireTime() != null && uc.getExpireTime().isBefore(now);
            UserCouponVO vo = toUserCouponVO(uc, coupon);
            if (expired) {
                vo.setStatus(2);
                // 顺手收敛状态字段（幂等，下一批定时任务也会处理）
                userCouponMapper.update(null, new LambdaUpdateWrapper<UserCoupon>()
                        .set(UserCoupon::getStatus, 2)
                        .eq(UserCoupon::getId, uc.getId())
                        .eq(UserCoupon::getStatus, 0));
            }
            records.add(vo);
        }
        result.setRecords(records);
        return result;
    }

    @Override
    public CouponAvailableVO listAvailable(BigDecimal totalAmount, List<Long> productIds) {
        Long userId = BaseContext.getCurrentId();
        ThrowUtils.throwIf(userId == null, ErrorCode.NOT_LOGIN_ERROR);
        BigDecimal amount = totalAmount == null ? BigDecimal.ZERO : totalAmount;
        Set<Long> orderProductIds = productIds == null ? Set.of() : new HashSet<>(productIds);

        List<UserCoupon> userCoupons = userCouponMapper.selectList(new LambdaQueryWrapper<UserCoupon>()
                .eq(UserCoupon::getUserId, userId)
                .eq(UserCoupon::getStatus, 0) // 仅未使用
                .orderByDesc(UserCoupon::getReceiveTime));

        List<Long> couponIds = userCoupons.stream()
                .map(UserCoupon::getCouponId).distinct().toList();
        Map<Long, Coupon> couponMap = couponIds.isEmpty() ? Map.of()
                : couponMapper.selectList(new LambdaQueryWrapper<Coupon>()
                        .in(Coupon::getId, couponIds))
                .stream().collect(Collectors.toMap(Coupon::getId, Function.identity()));

        // 预取所有品类券的适用商品集合（批量，避免 N+1）
        Set<Long> categoryCouponIds = couponMap.values().stream()
                .filter(c -> c.getCouponType() != null && c.getCouponType() == TYPE_CATEGORY)
                .map(Coupon::getId).collect(Collectors.toSet());
        Map<Long, Set<Long>> categoryScopeMap = new HashMap<>();
        if (!categoryCouponIds.isEmpty()) {
            List<CouponProduct> scopeList = couponProductMapper.selectList(
                    new LambdaQueryWrapper<CouponProduct>()
                            .in(CouponProduct::getCouponId, categoryCouponIds));
            for (CouponProduct cp : scopeList) {
                categoryScopeMap.computeIfAbsent(cp.getCouponId(), k -> new HashSet<>())
                        .add(cp.getProductId());
            }
        }

        LocalDateTime now = LocalDateTime.now();
        List<CouponAvailableVO.Item> usable = new ArrayList<>();
        List<CouponAvailableVO.Item> unusable = new ArrayList<>();
        for (UserCoupon uc : userCoupons) {
            Coupon coupon = couponMap.get(uc.getCouponId());
            String reason = null;
            if (coupon == null || coupon.getStatus() == null || coupon.getStatus() != 1) {
                reason = "优惠券已失效";
            } else if (uc.getExpireTime() != null && uc.getExpireTime().isBefore(now)) {
                reason = "优惠券已过期";
            } else if (coupon.getThreshold() != null && coupon.getThreshold().compareTo(BigDecimal.ZERO) > 0
                    && amount.compareTo(coupon.getThreshold()) < 0) {
                reason = String.format("满 %s 元可用", coupon.getThreshold().stripTrailingZeros().toPlainString());
            } else if (coupon.getCouponType() != null && coupon.getCouponType() == TYPE_CATEGORY
                    && !scopeMatch(categoryScopeMap.get(coupon.getId()), orderProductIds)) {
                reason = "该优惠券仅限指定商品使用";
            }
            CouponAvailableVO.Item item = new CouponAvailableVO.Item();
            item.setId(uc.getId());
            item.setCouponId(coupon != null ? coupon.getId() : uc.getCouponId());
            item.setCouponName(coupon != null ? coupon.getCouponName() : null);
            item.setCouponType(coupon != null ? coupon.getCouponType() : null);
            item.setThreshold(coupon != null ? coupon.getThreshold() : null);
            item.setAmount(coupon != null ? coupon.getAmount() : null);
            item.setDiscount(coupon != null ? coupon.getDiscount() : null);
            item.setMaxDiscount(coupon != null ? coupon.getMaxDiscount() : null);
            item.setExpireTime(uc.getExpireTime());
            item.setStatus(uc.getStatus());
            item.setReason(reason);
            if (reason == null) {
                usable.add(item);
            } else {
                unusable.add(item);
            }
        }
        CouponAvailableVO vo = new CouponAvailableVO();
        vo.setUsable(usable);
        vo.setUnusable(unusable);
        return vo;
    }

    /**
     * 品类券商品范围校验：订单全部商品都在券适用范围内才可用。
     * 券无关联商品（scope 为空/null）按全店通用处理，不拦截。
     */
    private boolean scopeMatch(Set<Long> scope, Set<Long> orderProductIds) {
        if (scope == null || scope.isEmpty()) {
            return true; // 未配置适用商品，视为全店通用（兜底，不应发生，管理端会强校验非空）
        }
        if (orderProductIds.isEmpty()) {
            return false;
        }
        return scope.containsAll(orderProductIds);
    }

    @Override
    public CouponVerifyVO verifyCoupon(Long userCouponId, Long userId, BigDecimal totalAmount,
                                       List<Long> productIds, boolean seckill, String orderNo) {
        CouponVerifyVO result = new CouponVerifyVO();
        result.setUserCouponId(userCouponId);
        if (userCouponId == null) {
            result.setDiscount(BigDecimal.ZERO); // 未选券
            return result;
        }
        ThrowUtils.throwIf(userId == null, ErrorCode.NOT_LOGIN_ERROR);
        if (seckill) {
            throw new BusinessException(ErrorCode.COUPON_SECKILL_FORBIDDEN);
        }

        // 1. 查用户券并校验归属/状态/有效期
        UserCoupon uc = userCouponMapper.selectById(userCouponId);
        ThrowUtils.throwIf(uc == null || !Objects.equals(uc.getUserId(), userId),
                ErrorCode.COUPON_INVALID);
        ThrowUtils.throwIf(uc.getStatus() == null || uc.getStatus() != 0,
                ErrorCode.COUPON_INVALID, "优惠券不可用或已过期");
        LocalDateTime now = LocalDateTime.now();
        ThrowUtils.throwIf(uc.getExpireTime() != null && uc.getExpireTime().isBefore(now),
                ErrorCode.COUPON_INVALID);

        // 2. 查券模板（逻辑删除自动过滤，软删模板的券不可再核销）
        Coupon coupon = couponMapper.selectById(uc.getCouponId());
        ThrowUtils.throwIf(coupon == null, ErrorCode.COUPON_NOT_FOUND);
        ThrowUtils.throwIf(coupon.getStatus() == null || coupon.getStatus() != 1,
                ErrorCode.COUPON_INVALID, "优惠券已失效");

        // 3. 门槛校验
        BigDecimal total = totalAmount == null ? BigDecimal.ZERO : totalAmount;
        ThrowUtils.throwIf(coupon.getThreshold() != null && coupon.getThreshold().compareTo(BigDecimal.ZERO) > 0
                        && total.compareTo(coupon.getThreshold()) < 0,
                ErrorCode.COUPON_THRESHOLD_NOT_MET);

        // 3.1 品类券商品范围校验：订单全部商品须在券适用范围内
        if (coupon.getCouponType() != null && coupon.getCouponType() == TYPE_CATEGORY) {
            List<CouponProduct> scopeList = couponProductMapper.selectList(
                    new LambdaQueryWrapper<CouponProduct>()
                            .eq(CouponProduct::getCouponId, coupon.getId()));
            Set<Long> scope = scopeList.stream().map(CouponProduct::getProductId).collect(Collectors.toSet());
            Set<Long> orderPids = productIds == null ? Set.of() : new HashSet<>(productIds);
            ThrowUtils.throwIf(!scopeMatch(scope, orderPids),
                    ErrorCode.COUPON_SCOPE_NOT_MET);
        }

        // 4. 原子核销（status=0 条件守卫防并发双花；一并回填订单号与核销时间，对账闭环）
        int rows = userCouponMapper.update(null, new LambdaUpdateWrapper<UserCoupon>()
                .set(UserCoupon::getStatus, 1)
                .set(UserCoupon::getOrderNo, orderNo)
                .set(UserCoupon::getUseTime, now)
                .eq(UserCoupon::getId, uc.getId())
                .eq(UserCoupon::getUserId, userId)
                .eq(UserCoupon::getStatus, 0));
        ThrowUtils.throwIf(rows == 0, ErrorCode.COUPON_INVALID, "优惠券不可用或已过期");

        // 5. 按类型计算优惠额（永远 >=0 且 <= 订单金额）
        result.setDiscount(calcDiscount(coupon, total));
        result.setCouponName(coupon.getCouponName());
        return result;
    }

    /**
     * 按券类型计算优惠额：
     * 满减/品类 = min(面额, 订单金额)；折扣 = min(订单金额×(1-折扣率), 封顶额)。
     */
    private BigDecimal calcDiscount(Coupon coupon, BigDecimal total) {
        int type = coupon.getCouponType() == null ? TYPE_FULL_REDUCTION : coupon.getCouponType();
        if (type == TYPE_DISCOUNT) {
            BigDecimal discount = coupon.getDiscount() == null ? BigDecimal.ZERO : coupon.getDiscount();
            // 优惠额 = total * (1 - discount)
            BigDecimal raw = total.multiply(BigDecimal.ONE.subtract(discount))
                    .setScale(2, java.math.RoundingMode.HALF_UP);
            BigDecimal max = coupon.getMaxDiscount();
            BigDecimal capped = (max != null && max.compareTo(BigDecimal.ZERO) > 0) ? raw.min(max) : raw;
            // 兜底不超过订单金额
            return capped.min(total).max(BigDecimal.ZERO);
        }
        // 满减 / 品类券：面额兜底，永不超订单金额
        BigDecimal amount = coupon.getAmount() == null ? BigDecimal.ZERO : coupon.getAmount();
        return amount.min(total).max(BigDecimal.ZERO);
    }

    @Override
    public void refundCoupon(Long userCouponId, String orderNo) {
        if (userCouponId == null) {
            return;
        }
        // 幂等：仅匹配「已使用且绑定该订单号」的券回退，重复取消不重复处理
        userCouponMapper.update(null, new LambdaUpdateWrapper<UserCoupon>()
                .set(UserCoupon::getStatus, 0)
                .set(UserCoupon::getOrderNo, null)
                .set(UserCoupon::getUseTime, null)
                .eq(UserCoupon::getId, userCouponId)
                .eq(UserCoupon::getStatus, 1)
                .eq(orderNo != null, UserCoupon::getOrderNo, orderNo));
    }

    // ==================== 管理端 ====================

    @Override
    public Page<CouponVO> pageCoupons(int pageNum, int pageSize, String keyword, Integer status) {
        LambdaQueryWrapper<Coupon> wrapper = new LambdaQueryWrapper<>();
        if (status != null) {
            wrapper.eq(Coupon::getStatus, status);
        }
        if (keyword != null && !keyword.isBlank()) {
            wrapper.like(Coupon::getCouponName, keyword.trim());
        }
        wrapper.orderByDesc(Coupon::getCreateTime);
        Page<Coupon> page = couponMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);

        Page<CouponVO> result = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        result.setRecords(page.getRecords().stream().map(this::toCouponVO).toList());
        return result;
    }

    @Override
    public Long createCoupon(CouponCreateRequest request) {
        Coupon coupon = new Coupon();
        applyAndValidate(request, coupon);
        coupon.setStatus(request.getStatus() != null ? request.getStatus() : 1);
        coupon.setSort(request.getSort() != null ? request.getSort() : 0);
        couponMapper.insert(coupon);
        // 品类券：保存适用商品关联
        if (request.getCouponType() != null && request.getCouponType() == TYPE_CATEGORY
                && request.getProductIds() != null) {
            for (Long pid : request.getProductIds().stream().filter(Objects::nonNull).distinct().toList()) {
                CouponProduct cp = new CouponProduct();
                cp.setCouponId(coupon.getId());
                cp.setProductId(pid);
                couponProductMapper.insert(cp);
            }
        }
        couponRedisService.syncStock(coupon);
        log.info("[Coupon] 创建券模板, id={}, name={}, type={}", coupon.getId(), coupon.getCouponName(), coupon.getCouponType());
        return coupon.getId();
    }

    @Override
    public void updateCoupon(Long id, CouponCreateRequest request) {
        Coupon coupon = couponMapper.selectById(id);
        ThrowUtils.throwIf(coupon == null, ErrorCode.NOT_FOUND_ERROR, "优惠券不存在");

        // 上架中的券禁止修改营销参数（面额/门槛/总量/有效期），防止与已领用户产生歧义
        boolean onShelf = coupon.getStatus() != null && coupon.getStatus() == 1;
        if (onShelf && request != null) {
            boolean changed = !Objects.equals(coupon.getAmount(), request.getAmount())
                    || !Objects.equals(coupon.getThreshold(), request.getThreshold())
                    || !Objects.equals(coupon.getTotalCount(), request.getTotalCount())
                    || !Objects.equals(coupon.getValidDays(), request.getValidDays());
            ThrowUtils.throwIf(changed, ErrorCode.OPERATION_ERROR, "上架中的券不可修改面额/门槛/总量/有效期");
        }

        if (request != null) {
            if (request.getCouponName() != null && !request.getCouponName().isBlank()) {
                coupon.setCouponName(request.getCouponName().trim());
            }
            if (request.getStartTime() != null) {
                coupon.setStartTime(request.getStartTime());
            }
            if (request.getEndTime() != null) {
                coupon.setEndTime(request.getEndTime());
            }
            if (request.getSort() != null) {
                coupon.setSort(request.getSort());
            }
            if (request.getStatus() != null) {
                coupon.setStatus(request.getStatus());
            }
        }
        couponMapper.updateById(coupon);
        couponRedisService.syncStock(coupon);
        log.info("[Coupon] 编辑券模板, id={}", id);
    }

    @Override
    public void deleteCoupon(Long id) {
        Coupon coupon = couponMapper.selectById(id);
        ThrowUtils.throwIf(coupon == null, ErrorCode.NOT_FOUND_ERROR, "优惠券不存在");
        couponMapper.deleteById(id);
        couponRedisService.deleteCache(id);
        log.info("[Coupon] 删除券模板, id={}", id);
    }

    @Override
    public void updateCouponStatus(Long id, Integer status) {
        ThrowUtils.throwIf(status == null || (status != 0 && status != 1),
                ErrorCode.PARAMS_ERROR, "状态值无效");
        Coupon coupon = couponMapper.selectById(id);
        ThrowUtils.throwIf(coupon == null, ErrorCode.NOT_FOUND_ERROR, "优惠券不存在");
        coupon.setStatus(status);
        couponMapper.updateById(coupon);
        couponRedisService.syncStock(coupon);
        log.info("[Coupon] 券模板状态变更, id={}, status={}", id, status);
    }

    @Override
    public int expireCoupons() {
        int rows = userCouponMapper.update(null, new LambdaUpdateWrapper<UserCoupon>()
                .set(UserCoupon::getStatus, 2)
                .eq(UserCoupon::getStatus, 0)
                .lt(UserCoupon::getExpireTime, LocalDateTime.now())
                .last("LIMIT 1000"));
        if (rows > 0) {
            log.info("[Coupon] 过期批处理完成，置为过期 {} 张", rows);
        }
        return rows;
    }

    // ==================== 私有方法 ====================

    /**
     * 创建券模板表单校验并回填实体
     */
    private void applyAndValidate(CouponCreateRequest request, Coupon coupon) {
        ThrowUtils.throwIf(request == null, ErrorCode.PARAMS_ERROR, "请求参数不能为空");
        ThrowUtils.throwIf(request.getCouponName() == null || request.getCouponName().isBlank(),
                ErrorCode.PARAMS_ERROR, "券名称不能为空");
        int type = request.getCouponType() == null ? TYPE_FULL_REDUCTION : request.getCouponType();
        ThrowUtils.throwIf(type != TYPE_FULL_REDUCTION && type != TYPE_DISCOUNT && type != TYPE_CATEGORY,
                ErrorCode.PARAMS_ERROR, "券类型非法（1=满减 2=折扣 3=品类）");
        BigDecimal threshold = request.getThreshold() == null ? BigDecimal.ZERO : request.getThreshold();
        ThrowUtils.throwIf(threshold.compareTo(BigDecimal.ZERO) < 0,
                ErrorCode.PARAMS_ERROR, "使用门槛不能为负数");

        // 按类型校验金额字段
        if (type == TYPE_DISCOUNT) {
            // 折扣券：discount 必须在 (0,1)，不用 amount
            BigDecimal discount = request.getDiscount();
            ThrowUtils.throwIf(discount == null
                            || discount.compareTo(BigDecimal.ZERO) <= 0
                            || discount.compareTo(BigDecimal.ONE) >= 0,
                    ErrorCode.COUPON_DISCOUNT_INVALID);
            coupon.setDiscount(discount);
            coupon.setAmount(null);
            BigDecimal max = request.getMaxDiscount();
            coupon.setMaxDiscount(max != null && max.compareTo(BigDecimal.ZERO) > 0 ? max : null);
        } else {
            // 满减 / 品类券：amount > 0
            ThrowUtils.throwIf(request.getAmount() == null || request.getAmount().compareTo(BigDecimal.ZERO) <= 0,
                    ErrorCode.PARAMS_ERROR, "优惠金额必须大于0");
            coupon.setAmount(request.getAmount());
            coupon.setDiscount(null);
            coupon.setMaxDiscount(null);
        }
        // 品类券：必须挂载至少一个商品
        if (type == TYPE_CATEGORY) {
            ThrowUtils.throwIf(request.getProductIds() == null || request.getProductIds().isEmpty(),
                    ErrorCode.COUPON_PRODUCT_EMPTY);
        }

        int totalCount = request.getTotalCount() == null ? 0 : request.getTotalCount();
        ThrowUtils.throwIf(totalCount < 0, ErrorCode.PARAMS_ERROR, "发放总量不能为负数");
        int perUserLimit = request.getPerUserLimit() == null ? 1 : request.getPerUserLimit();
        ThrowUtils.throwIf(perUserLimit != 1,
                ErrorCode.PARAMS_ERROR, "每人限领数量固定为1（扩展多张需调整唯一索引）");
        int validDays = request.getValidDays() == null ? 7 : request.getValidDays();
        ThrowUtils.throwIf(validDays < 1, ErrorCode.PARAMS_ERROR, "有效天数必须大于0");
        if (request.getStartTime() != null && request.getEndTime() != null) {
            ThrowUtils.throwIf(!request.getEndTime().isAfter(request.getStartTime()),
                    ErrorCode.PARAMS_ERROR, "领取结束时间必须晚于开始时间");
        }

        coupon.setCouponName(request.getCouponName().trim());
        coupon.setCouponType(type);
        coupon.setThreshold(threshold);
        coupon.setTotalCount(totalCount);
        coupon.setReceivedCount(0);
        coupon.setPerUserLimit(perUserLimit);
        coupon.setValidDays(validDays);
        coupon.setStartTime(request.getStartTime());
        coupon.setEndTime(request.getEndTime());
    }

    private CouponVO toCouponVO(Coupon coupon) {
        CouponVO vo = new CouponVO();
        vo.setId(coupon.getId());
        vo.setCouponName(coupon.getCouponName());
        vo.setCouponType(coupon.getCouponType());
        vo.setThreshold(coupon.getThreshold());
        vo.setAmount(coupon.getAmount());
        vo.setDiscount(coupon.getDiscount());
        vo.setMaxDiscount(coupon.getMaxDiscount());
        vo.setTotalCount(coupon.getTotalCount());
        vo.setReceivedCount(coupon.getReceivedCount());
        vo.setPerUserLimit(coupon.getPerUserLimit());
        vo.setValidDays(coupon.getValidDays());
        vo.setStartTime(coupon.getStartTime());
        vo.setEndTime(coupon.getEndTime());
        vo.setStatus(coupon.getStatus());
        vo.setSort(coupon.getSort());
        vo.setCreateTime(coupon.getCreateTime());
        // 品类券回显适用商品
        if (coupon.getCouponType() != null && coupon.getCouponType() == TYPE_CATEGORY) {
            List<CouponProduct> scopeList = couponProductMapper.selectList(
                    new LambdaQueryWrapper<CouponProduct>()
                            .eq(CouponProduct::getCouponId, coupon.getId()));
            vo.setProductIds(scopeList.stream().map(CouponProduct::getProductId).toList());
        }
        return vo;
    }

    private UserCouponVO toUserCouponVO(UserCoupon uc, Coupon coupon) {
        UserCouponVO vo = new UserCouponVO();
        vo.setId(uc.getId());
        vo.setCouponId(uc.getCouponId());
        vo.setCouponName(coupon != null ? coupon.getCouponName() : null);
        vo.setCouponType(coupon != null ? coupon.getCouponType() : null);
        vo.setThreshold(coupon != null ? coupon.getThreshold() : null);
        vo.setAmount(coupon != null ? coupon.getAmount() : null);
        vo.setDiscount(coupon != null ? coupon.getDiscount() : null);
        vo.setMaxDiscount(coupon != null ? coupon.getMaxDiscount() : null);
        vo.setStatus(uc.getStatus());
        vo.setOrderNo(uc.getOrderNo());
        vo.setUseTime(uc.getUseTime());
        vo.setExpireTime(uc.getExpireTime());
        vo.setReceiveTime(uc.getReceiveTime());
        return vo;
    }
}
