import request from "@/utils/request";
import sm from "sm-crypto";

const USER_BASE_URL = "/sym/user";

const UserAPI = {
  /**
   * 获取当前登录用户信息
   *
   * @returns 登录用户昵称、头像信息，包括角色和权限
   */
  getInfo() {
    return request<any, UserInfo>({
      url: `${USER_BASE_URL}/me`,
      method: "post",
    });
  },

  /** 修改当前登录账号的密码；服务端会核验原密码并注销旧会话。 */
  changeOwnPassword(oldPassword: string, newPassword: string) {
    return request<any, { relogin: boolean }>({
      url: "/portal/account/password",
      method: "post",
      data: {
        oldPassword: sm.sm3(oldPassword),
        newPassword: sm.sm3(newPassword),
        newPlainPassword: newPassword,
      },
    });
  },
};

export default UserAPI;

/** 登录用户信息 */
export interface UserInfo {
  /** 用户ID */
  userId?: string;

  /** 用户名 */
  userName?: string;

  /** 真实姓名 */
  realName?: string;

  /** 手机号 */
  phone?: string;

  /** 用户部门 */
  orgId?: string;

  /** 用户部门 */
  orgName?: string;

  /** 所属机构路径 */
  orgPath?: string;

  /** 用户部门序列号  */
  serialNumber?: string;

  /** 用户根级部门 */
  orgRootId?: string;

  /** 用户根级部门序列号 */
  orgRootSerialNumber?: string;

  /** 头像URL */
  avatar?: string;

  /** 角色 */
  roles: string[];

  /** 权限 */
  perms: string[];
}
