package com.lichun.agsell.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lichun.agsell.model.dto.AfterSalesCreateRequest;
import com.lichun.agsell.model.vo.AfterSalesDetailVO;
import com.lichun.agsell.model.vo.AfterSalesListItemVO;

/**
 * 用户端售后 Service
 */
public interface AfterSalesService {

    /**
     * 申请售后（仅 待收货/已完成 订单，申请后订单进入售后中）
     */
    AfterSalesDetailVO apply(AfterSalesCreateRequest request);

    /**
     * 我的售后列表
     */
    Page<AfterSalesListItemVO> listMine(int pageNum, int pageSize, Integer status);

    /**
     * 售后详情
     */
    AfterSalesDetailVO getDetail(String afterSalesNo);

    /**
     * 撤销售后申请（仅待处理可撤销，撤销后订单恢复原状态）
     */
    void cancelApply(String afterSalesNo);
}
