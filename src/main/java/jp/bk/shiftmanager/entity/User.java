package jp.bk.shiftmanager.entity;

import lombok.Data;

/** スタッフ（管理者を含む） */
@Data
public class User {
    private Long id;
    private String loginId;
    private String name;
    private String passwordHash;
    /** 初期ポジション（未設定ならnull） */
    private Long positionId;
    private boolean admin;
    private boolean enabled;
    private boolean mustChangePassword;
}
