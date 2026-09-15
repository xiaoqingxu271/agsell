package com.lichun.agsell.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lichun.agsell.model.dto.AdminCreateRequest;
import com.lichun.agsell.model.dto.AdminPasswordResetRequest;
import com.lichun.agsell.model.dto.AdminUpdateRequest;
import com.lichun.agsell.model.vo.AdminListItemVO;

/**
 * 管理员管理服务
 */
public interface AdminSystemService {

    /** 管理员分页列表（不含密码） */
    Page<AdminListItemVO> listAdmins(int pageNum, int pageSize, String keyword);

    /** 新增管理员（仅 SUPER_ADMIN；role 仅 ADMIN/OPERATOR） */
    void createAdmin(AdminCreateRequest request);

    /** 编辑管理员（姓名/角色） */
    void updateAdmin(Long id, AdminUpdateRequest request);

    /** 重置密码 */
    void resetPassword(Long id, AdminPasswordResetRequest request);

    /** 启停 */
    void updateStatus(Long id, Integer status);

    /** 删除（软删除；含 R1/R2/R3 保护规则） */
    void deleteAdmin(Long id);
}
