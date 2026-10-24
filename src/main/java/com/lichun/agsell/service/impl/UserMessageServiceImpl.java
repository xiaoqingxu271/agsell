package com.lichun.agsell.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lichun.agsell.common.BaseContext;
import com.lichun.agsell.exception.ErrorCode;
import com.lichun.agsell.mapper.UserMessageMapper;
import com.lichun.agsell.model.entity.UserMessage;
import com.lichun.agsell.model.vo.UserMessageVO;
import com.lichun.agsell.service.UserMessageService;
import com.lichun.agsell.utils.ThrowUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;



/**
 * 用户站内消息服务实现
 * <p>
 * notify 与发货/售后处理同事务写入：消息落库失败则整个业务一起回滚，
 * 避免「已发货却没有消息」的不一致（同库写入不值得为它引入异步补偿）。
 */
@Service
@RequiredArgsConstructor
public class UserMessageServiceImpl implements UserMessageService {

    private final UserMessageMapper userMessageMapper;

    @Override
    public Page<UserMessageVO> pageMine(int pageNum, int pageSize) {
        Long userId = BaseContext.getCurrentId();
        ThrowUtils.throwIf(userId == null, ErrorCode.NOT_LOGIN_ERROR);

        Page<UserMessage> page = userMessageMapper.selectPage(new Page<>(pageNum, pageSize),
                new LambdaQueryWrapper<UserMessage>()
                        .eq(UserMessage::getUserId, userId)
                        .orderByDesc(UserMessage::getCreateTime));

        Page<UserMessageVO> result = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        result.setRecords(page.getRecords().stream().map(this::toVO).toList());
        return result;
    }

    @Override
    public long unreadCount() {
        Long userId = BaseContext.getCurrentId();
        ThrowUtils.throwIf(userId == null, ErrorCode.NOT_LOGIN_ERROR);
        return userMessageMapper.selectCount(new LambdaQueryWrapper<UserMessage>()
                .eq(UserMessage::getUserId, userId)
                .eq(UserMessage::getIsRead, 0));
    }

    @Override
    public int markAllRead() {
        Long userId = BaseContext.getCurrentId();
        ThrowUtils.throwIf(userId == null, ErrorCode.NOT_LOGIN_ERROR);
        return userMessageMapper.update(null, new LambdaUpdateWrapper<UserMessage>()
                .eq(UserMessage::getUserId, userId)
                .eq(UserMessage::getIsRead, 0)
                .set(UserMessage::getIsRead, 1));
    }

    @Override
    public void notify(Long userId, String type, String title, String content, String relatedNo) {
        ThrowUtils.throwIf(userId == null, ErrorCode.PARAMS_ERROR, "接收用户不能为空");
        UserMessage message = new UserMessage();
        message.setUserId(userId);
        message.setType(type);
        message.setTitle(title);
        message.setContent(content);
        message.setRelatedNo(relatedNo);
        message.setIsRead(0);
        userMessageMapper.insert(message);
    }

    private UserMessageVO toVO(UserMessage message) {
        UserMessageVO vo = new UserMessageVO();
        vo.setId(message.getId());
        vo.setType(message.getType());
        vo.setTitle(message.getTitle());
        vo.setContent(message.getContent());
        vo.setRelatedNo(message.getRelatedNo());
        vo.setIsRead(message.getIsRead());
        vo.setCreateTime(message.getCreateTime());
        return vo;
    }
}
