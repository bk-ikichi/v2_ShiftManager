package jp.bk.shiftmanager.dto;

import lombok.Data;

/** スタッフ一覧の1行 */
@Data
public class StaffRow {
    private Long id;
    private String loginId;
    private String name;
    private String positionName;
    private boolean admin;
    private boolean enabled;
}
